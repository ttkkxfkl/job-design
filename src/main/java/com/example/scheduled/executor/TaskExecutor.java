package com.example.scheduled.executor;

import com.example.scheduled.entity.ScheduledTask;

/**
 * 任务执行器接口 - 策略模式
 * 不同的任务类型实现不同的执行器
 */
public interface TaskExecutor {

    /**
     * 判断是否支持该任务类型
     */
    boolean support(ScheduledTask.TaskType taskType);

    /**
     * 执行任务
     * @param task 任务信息
     * @throws Exception 执行异常
     */
    void execute(ScheduledTask task) throws Exception;

    /**
     * 获取执行器名称
     */
    String getName();
}
