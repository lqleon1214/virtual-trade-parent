package com.itheima.delaytask.inf;

import com.itheima.delaytask.dto.Task;
import com.itheima.delaytask.exception.ScheduleSystemException;
import com.itheima.delaytask.exception.TaskNotExistException;

public interface TaskService {

    /**
     * 添加子任务
     * @param task
     * @return
     * @throws ScheduleSystemException
     */
    public long addTask(Task task) throws ScheduleSystemException;

    /**
     * 取消任务
     * @param taskId
     * @return
     */
    public boolean cancelTask(long taskId) throws TaskNotExistException, ScheduleSystemException;

    /**
     * 拉取消费任务，消费的是当前需要执行的任务
     * 按照类型和优先级来拉取任务
     * @return
     * @throws ScheduleSystemException
     */
    public Task poll(int type, int priority) throws ScheduleSystemException;
}
