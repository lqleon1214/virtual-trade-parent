package com.itheima.delaytask.service;

import com.alibaba.fastjson.JSON;
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
import jdk.internal.org.jline.terminal.spi.JansiSupport;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.Date;
import java.util.Set;

@Slf4j
public class TaskServiceImpl implements TaskService {

    @Autowired
    private TaskInfoMapper taskInfoMapper;
    @Autowired
    private TaskInfoLogsMapper taskInfoLogsMapper;
    @Autowired
    private CacheService cacheService;

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
        } else {
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
}
