package com.itheima.delaytask.service;

import com.alibaba.fastjson.JSON;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.itheima.cache.CacheService;
import com.itheima.delaytask.constants.DelayTaskRedisKeyConstants;
import com.itheima.delaytask.constants.TaskStatusConstants;
import com.itheima.delaytask.dto.Task;
import com.itheima.delaytask.exception.ScheduleSystemException;
import com.itheima.delaytask.exception.TaskNotExistException;
import com.itheima.delaytask.inf.TaskService;
import com.itheima.delaytask.mapper.TaskInfoLogsMapper;
import com.itheima.delaytask.mapper.TaskInfoMapper;
import com.itheima.delaytask.po.TaskInfo;
import com.itheima.delaytask.po.TaskInfoLogs;
import com.itheima.delaytask.properties.SystemParamProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import javax.annotation.PostConstruct;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
public class TaskServiceImpl implements TaskService {

    @Autowired
    private TaskInfoMapper taskInfoMapper;
    @Autowired
    private TaskInfoLogsMapper taskInfoLogsMapper;
    @Autowired
    private CacheService cacheService;
    @Autowired
    private ThreadPoolTaskExecutor taskExecutor;
    @Autowired
    private SystemParamProperties paramProperties;
    private long nextScheduleTime;                  // 下一次未来2分钟的时间节点
    @Autowired
    private ThreadPoolTaskScheduler threadPoolTaskScheduler;

    @PostConstruct
    public void syncData() {
        // 首次运行即执行一次，然后按照设定的时间周期按照固定频率执行
        threadPoolTaskScheduler.scheduleAtFixedRate(new Runnable() {
            @Override
            public void run() {
                reloadData();
            }
        }, TimeUnit.MINUTES.toMillis(paramProperties.getPreLoad()));        // 需要传递一个毫秒值
    }

    private void reloadData() {
        long start = System.currentTimeMillis();            // 计时代码
        // 先清除缓存中的原有数据
        clearCache();
        // 查询任务表中的所有分组
        QueryWrapper<TaskInfo> queryWrapper = new QueryWrapper<>();
        queryWrapper.lambda()
                .select(TaskInfo::getTaskType, TaskInfo::getPriority)
                .groupBy(TaskInfo::getTaskType, TaskInfo::getPriority);
        List<TaskInfo> group_tasks = taskInfoMapper.selectList(queryWrapper);
        // 获取每个分组下的任务数据将其添加到缓存
        if (Objects.isNull(group_tasks)) return;
        CountDownLatch countDownLatch = new CountDownLatch(group_tasks.size());//计时代码
        // 定义未来2分钟的时间节点变量
        Calendar calendar = Calendar.getInstance();
        calendar.add(Calendar.MINUTE, paramProperties.getPreLoad());
        nextScheduleTime = calendar.getTimeInMillis();
        for (TaskInfo group_task : group_tasks) {
            // 查询该分组下的任务
//            queryWrapper.lambda()
//                    .eq(TaskInfo::getTaskType, group_task.getTaskType())
//                    .eq(TaskInfo::getPriority, group_task.getPriority());
//            List<TaskInfo> taskInfos = taskInfoMapper.selectList(queryWrapper);
//            // 调用addTaskToCache(Task)方法添加到缓存
//            for (TaskInfo taskInfo : taskInfos) {
//                Task task = new Task();
//                BeanUtils.copyProperties(taskInfo, task);
//                task.setExecuteTime(taskInfo.getExecuteTime().getTime());
//                addTaskToCache(task);
//            }
            // 将每个分组都提交到线程池中执行
            taskExecutor.execute(new Runnable() {
                @Override
                public void run() {
                    // 查询该分组下的任务
                    QueryWrapper<TaskInfo> queryWrapper = new QueryWrapper<>();
                    queryWrapper.lambda()
                            .eq(TaskInfo::getTaskType, group_task.getTaskType())
                            .eq(TaskInfo::getPriority, group_task.getPriority());
                    List<TaskInfo> taskInfos = taskInfoMapper.selectList(queryWrapper);
                    // 调用addTaskToCache(Task)方法添加到缓存
                    for (TaskInfo taskInfo : taskInfos) {
                        Task task = new Task();
                        BeanUtils.copyProperties(taskInfo, task);
                        task.setExecuteTime(taskInfo.getExecuteTime().getTime());
                        addTaskToCache(task);
                    }
                    countDownLatch.countDown();         // 计时代码
                }
            });
        }

        // 计时代码
        try {
            countDownLatch.await(5, TimeUnit.MINUTES);
            log.info("多线程分组数据恢复耗时:{}", System.currentTimeMillis() - start);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    private void clearCache() {
        // 缓存中要清楚的消费者队列和外来数据集合————我们只需找到所有跟延迟任务相关的key，然后删除这些key即可
        Set<String> futureKeys = cacheService.scan(DelayTaskRedisKeyConstants.FUTURE + "*");
        Set<String> topicKeys = cacheService.scan(DelayTaskRedisKeyConstants.TOPIC + "*");
        // 删除这些key即可
        cacheService.delete(futureKeys);
        cacheService.delete(topicKeys);
    }

    @Override
    @Transactional
    public long addTask(Task task) throws ScheduleSystemException {
        /**
         * 1. 先将任务添加导数据库
         * 2. 添加成功后添加到缓存
         */
        boolean success = addTaskToDb(task);
        if (success) {
            addTaskToCache(task);
        }
        return task.getTaskId();
    }

    private void addTaskToCache(Task task) {
        // 要根据任务类型和优先级进行分组，每组的key不一样
        String key = task.getTaskType() + "_" + task.getPriority();
        // 根据时间判断
        if (task.getExecuteTime() <= System.currentTimeMillis()) {
            // 任务进入消费者队列List——数据从左侧入队列，消费时从右侧出队列——保证任务消费的顺序性
            // 同时我们让所有分组的key都有一个相同的前缀，方便进行管理
            cacheService.lLeftPush(DelayTaskRedisKeyConstants.TOPIC + key, JSON.toJSONString(task));
        } else if (task.getExecuteTime() <= nextScheduleTime) {
            // 任务进行zset排序等待
            cacheService.zAdd(DelayTaskRedisKeyConstants.FUTURE + key, JSON.toJSONString(task), task.getExecuteTime());
        }

        // 先暂时将数据存储到zset，后续会对此优化
        // cacheService.zAdd(DelayTaskRedisKeyConstants.DBCACHE, JSON.toJSONString(task), task.getExecuteTime());
    }

    /**
     * 将数据
     * @param task
     * @return
     */
    private boolean addTaskToDb(Task task) {
        boolean success = false;
        try {
            // 未执行任务入库
            TaskInfo taskInfo = new TaskInfo();
            taskInfo.setTaskType(task.getTaskType());
            taskInfo.setPriority(task.getPriority());
            taskInfo.setParameters(task.getParameters());
            taskInfo.setExecuteTime(new Date(task.getExecuteTime()));

            taskInfoMapper.insert(taskInfo);

            // 填充返回的主键值——任务的id
            task.setTaskId(taskInfo.getTaskId());

            // 保存任务日志记录——任务日志和任务字段差不多，多了一个版本号和任务状态
            TaskInfoLogs taskInfoLogs = new TaskInfoLogs();
            BeanUtils.copyProperties(taskInfo, taskInfoLogs);
            taskInfoLogs.setVersion(1);
            taskInfoLogs.setStatus(TaskStatusConstants.SCHEDULED);          // 状态是刚提交的状态
            taskInfoLogsMapper.insert(taskInfoLogs);
            success = true;
        } catch (Exception e) {
            log.error("add task exception, task={}", task);
            throw new ScheduleSystemException(e);
        }

        return success;
    }

    @Override
    @Transactional
    public boolean cancelTask(long taskId) throws TaskNotExistException, ScheduleSystemException {
        /**
         * 1. 删除任务表数据
         * 2. 更新记录表的状态
         * 3. 删除redis中的数据
         */
        boolean success = false;
        Task task = updateDb(taskId, TaskStatusConstants.CANCELLED);
        if (task != null) {
            removeTaskFromCache(task);
            success = true;
        }
        return false;
    }

    private void removeTaskFromCache(Task task) {
        String key = task.getTaskType() + "_" + task.getPriority();
        // 判断
        if (task.getExecuteTime() <= System.currentTimeMillis()) {
            // 从消费者队列中移除
            cacheService.lRemove(DelayTaskRedisKeyConstants.TOPIC + key, 0, JSON.toJSONString(task));
        } else {
            cacheService.zRemove(DelayTaskRedisKeyConstants.FUTURE + key, JSON.toJSONString(task));
        }
        // 暂时这样写，后续优化
        // cacheService.zRemove(DelayTaskRedisKeyConstants.DBCACHE, JSON.toJSON(task));
    }

    private Task updateDb(long taskId, int status) {
        Task task = null;

        // 修改任务日志表中的状态
        TaskInfoLogs taskInfoLogs = taskInfoLogsMapper.selectById(taskId);
        if (taskInfoLogs == null) {
            throw new TaskNotExistException("task not exist,taskid={}", taskId);
        }
        try {
            // 更改状态
            UpdateWrapper<TaskInfoLogs> updateWrapper = new UpdateWrapper<>();
            updateWrapper.lambda()
                    .set(TaskInfoLogs::getStatus, status)
                    .eq(TaskInfoLogs::getTaskId, taskId);
            taskInfoLogsMapper.update(null, updateWrapper);

            // 删除任务表数据，按照主键删除
            taskInfoLogsMapper.deleteById(taskId);
            // 构造返回
            task = new Task();
            // 封装数据
            BeanUtils.copyProperties(taskInfoLogs, task);
            task.setExecuteTime(taskInfoLogs.getExecuteTime().getTime());
        } catch (Exception e) {
            log.error("cancel task exception,taskid={}", taskId);
            throw new ScheduleSystemException(e);
        }
        return task;
    }

    @Override
    @Transactional
    public Task poll(int type, int priority) throws ScheduleSystemException {
        Task task = null;

        // 从zset集合中去拉取当前需要可执行的任务，按照分数获取即可，分数就是任务的执行时间
//        Set<String> byScore = cacheService.zRangeByScore(DelayTaskRedisKeyConstants.DBCACHE, 0, System.currentTimeMillis());
//        if (byScore == null || byScore.size() == 0) {
//            return null;
//        }

        // 消费时只需从消费者队列List中右侧最外层元素即可
        String key = type + "_" + priority;
        String task_json = cacheService.lRightPop(DelayTaskRedisKeyConstants.TOPIC + key);
        if (StringUtils.isEmpty(task_json)) return null;

        try {
            // 获取当前第一个任务
//            String task_json = byScore.iterator().next();
            task = JSON.parseObject(task_json, Task.class);
            // 更新数据库中任务信息——删除任务表数据，日志状态改为已执行——复用之前的方法
            updateDb(task.getTaskId(), TaskStatusConstants.EXECUTED);
            // 删除缓存中的该任务
            cacheService.zRemove(DelayTaskRedisKeyConstants.DBCACHE, task_json);
        } catch (Exception e) {
            log.error("poll task exception,msg={}", e.getMessage());
            throw new ScheduleSystemException(e);
        }
        return null;
    }

    @Scheduled(cron = "*/1 * * * * ?")
    public void refresh() {
        // 如果这个地方不放入线程池，如果这个地方的执行超过1s，就会造成下一个任务延迟执行
        taskExecutor.execute(new Runnable() {
            @Override
            public void run() {
                log.info("{}进行了定时判断", LocalDateTime.now().format(DateTimeFormatter.ISO_DATE_TIME));
                try {
                    TimeUnit.SECONDS.sleep(2);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                // 找到未来数据集合的所有key
                Set<String> future_keys = cacheService.scan(DelayTaskRedisKeyConstants.FUTURE + "*");
                if (future_keys == null || future_keys.size() == 0) return;
                for (String future_key : future_keys) {
                    // 根据每一个key从该分组下 获取当前需要执行的任务数据Set集合
                    Set<String> values = cacheService.zRangeByScore(future_key, 0, System.currentTimeMillis());
                    if (values == null || values.size() == 0) continue;
                    // 将这组数据添加到消费者队列的对应分组上，并从zset中移除
                    String topicKey = DelayTaskRedisKeyConstants.TOPIC + future_key.split(DelayTaskRedisKeyConstants.FUTURE)[1];
//            for (String value : values) {
//                cacheService.lLeftPush(topicKey, value);
//                cacheService.zRemove(future_key, value);
//            }
                    cacheService.refreshWithPipeline(future_key, topicKey, values);         // 只需此依据，改造完成
                }
            }
        });
    }
}
