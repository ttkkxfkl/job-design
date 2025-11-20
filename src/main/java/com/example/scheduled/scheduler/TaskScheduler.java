package com.example.scheduled.scheduler;

import com.example.scheduled.entity.ScheduledTask;

import java.util.Map;

/**
 * 任务调度器接口
 * 定义统一的调度行为，支持多种实现（内置线程池、Quartz 等）
 */
public interface TaskScheduler {

    /**
     * 初始化调度器
     */
    void init();

    /**
     * 调度单个任务
     * @param task 待调度的任务
     */
    void scheduleTask(ScheduledTask task);

    /**
     * 执行任务
     * @param taskId 任务ID
     */
    void executeTask(Long taskId);

    /**
     * 取消任务
     * @param taskId 任务ID
     * @return 是否取消成功
     */
    boolean cancelTask(Long taskId);

    /**
     * 销毁调度器
     */
    void destroy();

    /**
     * 获取调度器状态
     * @return 状态信息
     */
    Map<String, Object> getSchedulerStatus();

    /**
     * 获取调度器类型名称
     * @return 调度器类型
     */
    String getSchedulerType();
}
