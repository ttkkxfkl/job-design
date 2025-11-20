package com.example.scheduled.service;

import com.example.scheduled.entity.ScheduledTask;
import com.example.scheduled.entity.TaskExecutionLog;
import com.example.scheduled.repository.ScheduledTaskRepository;
import com.example.scheduled.repository.TaskExecutionLogRepository;
import com.example.scheduled.scheduler.TaskScheduler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 任务管理服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TaskManagementService {

    private final ScheduledTaskRepository taskRepository;
    private final TaskExecutionLogRepository logRepository;
    private final TaskScheduler taskScheduler;

    /**
     * 创建一次性定时任务（ONCE 模式）
     */
    @Transactional
    public ScheduledTask createOnceTask(String taskName,
                                        ScheduledTask.TaskType taskType,
                                        LocalDateTime executeTime,
                                        Map<String, Object> taskData,
                                        Integer maxRetryCount,
                                        Integer priority,
                                        Long executionTimeout) {

        // 验证执行时间
        if (executeTime.isBefore(LocalDateTime.now())) {
            throw new IllegalArgumentException("执行时间不能早于当前时间");
        }

        ScheduledTask task = ScheduledTask.builder()
                .taskName(taskName)
                .taskType(taskType)
                .scheduleMode(ScheduledTask.ScheduleMode.ONCE)
                .executeTime(executeTime)
                .priority(priority)
                .executionTimeout(executionTimeout)
                .taskData(taskData)
                .status(ScheduledTask.TaskStatus.PENDING)
                .retryCount(0)
                .maxRetryCount(maxRetryCount != null ? maxRetryCount : 3)
                .build();

        if (task.getId() == null) {
            taskRepository.insert(task);
        } else {
            taskRepository.updateById(task);
        }
        log.info("创建一次性任务成功：{}, ID: {}, 执行时间: {}, 优先级: {}, 超时: {}秒", 
                taskName, task.getId(), executeTime, task.getPriority(), task.getExecutionTimeout());

        // 立即调度任务
        taskScheduler.scheduleTask(task);

        return task;
    }

    /**
     * 创建周期性 Cron 任务（CRON 模式）
     */
    @Transactional
    public ScheduledTask createCronTask(String taskName,
                                        ScheduledTask.TaskType taskType,
                                        String cronExpression,
                                        Map<String, Object> taskData,
                                        Integer priority,
                                        Long executionTimeout) {

        // 验证 Cron 表达式（简单验证）
        if (cronExpression == null || cronExpression.trim().isEmpty()) {
            throw new IllegalArgumentException("Cron 表达式不能为空");
        }

        ScheduledTask task = ScheduledTask.builder()
                .taskName(taskName)
                .taskType(taskType)
                .scheduleMode(ScheduledTask.ScheduleMode.CRON)
                .cronExpression(cronExpression)
                .priority(priority)
                .executionTimeout(executionTimeout)
                .taskData(taskData)
                .status(ScheduledTask.TaskStatus.PENDING)
                .retryCount(0)
                .maxRetryCount(0)  // CRON 任务不需要重试
                .build();

        if (task.getId() == null) {
            taskRepository.insert(task);
        } else {
            taskRepository.updateById(task);
        }
        log.info("创建 Cron 任务成功：{}, ID: {}, Cron: {}, 优先级: {}, 超时: {}秒", 
                taskName, task.getId(), cronExpression, task.getPriority(), task.getExecutionTimeout());

        // 立即调度任务
        taskScheduler.scheduleTask(task);

        return task;
    }

    /**
     * 根据ID查询任务
     */
    public ScheduledTask getTaskById(Long id) {
        return taskRepository.selectById(id);
    }

    /**
     * 查询所有任务
     */
    public List<ScheduledTask> getAllTasks() {
        return taskRepository.selectList(null);
    }

    /**
     * 根据状态查询任务
     */
    public List<ScheduledTask> getTasksByStatus(ScheduledTask.TaskStatus status) {
        return taskRepository.selectList(
            new LambdaQueryWrapper<ScheduledTask>()
                .eq(ScheduledTask::getStatus, status)
                .orderByAsc(ScheduledTask::getExecuteTime)
        );
    }

    /**
     * 取消任务
     */
    @Transactional
    public boolean cancelTask(Long taskId) {
        return taskScheduler.cancelTask(taskId);
    }

    /**
     * 暂停任务
     */
    @Transactional
    public boolean pauseTask(Long taskId) {
        ScheduledTask task = taskRepository.selectById(taskId);
        if (task == null) {
            return false;
        }

        // 只有 PENDING 状态的任务可以暂停
        if (task.getStatus() != ScheduledTask.TaskStatus.PENDING) {
            log.warn("任务 [{}] 状态不是 PENDING，无法暂停，当前状态：{}", task.getTaskName(), task.getStatus());
            return false;
        }

        // 取消调度
        taskScheduler.cancelTask(taskId);

        // 更新状态为暂停
        task.setStatus(ScheduledTask.TaskStatus.PAUSED);
        taskRepository.updateById(task);

        log.info("任务 [{}] 已暂停", task.getTaskName());
        return true;
    }

    /**
     * 恢复已暂停的任务
     */
    @Transactional
    public boolean resumeTask(Long taskId) {
        ScheduledTask task = taskRepository.selectById(taskId);
        if (task == null) {
            return false;
        }

        // 只有 PAUSED 状态的任务可以恢复
        if (task.getStatus() != ScheduledTask.TaskStatus.PAUSED) {
            log.warn("任务 [{}] 状态不是 PAUSED，无法恢复，当前状态：{}", task.getTaskName(), task.getStatus());
            return false;
        }

        // 更新状态为待执行
        task.setStatus(ScheduledTask.TaskStatus.PENDING);
        taskRepository.updateById(task);

        // 重新调度
        taskScheduler.scheduleTask(task);

        log.info("任务 [{}] 已恢复", task.getTaskName());
        return true;
    }

    /**
     * 手动立即重试任务
     */
    @Transactional
    public boolean retryTaskNow(Long taskId) {
        ScheduledTask task = taskRepository.selectById(taskId);
        if (task == null) {
            return false;
        }

        // 取消当前调度
        taskScheduler.cancelTask(taskId);

        // 重置执行时间为现在
        task.setExecuteTime(LocalDateTime.now());
        task.setStatus(ScheduledTask.TaskStatus.PENDING);
        taskRepository.updateById(task);

        // 重新调度（立即执行）
        taskScheduler.scheduleTask(task);

        log.info("任务 [{}] 已设置为立即重试", task.getTaskName());
        return true;
    }

    /**
     * 查询任务执行历史
     */
    public List<TaskExecutionLog> getTaskExecutionLogs(Long taskId) {
        return logRepository.selectList(
            new LambdaQueryWrapper<TaskExecutionLog>()
                .eq(TaskExecutionLog::getTaskId, taskId)
                .orderByDesc(TaskExecutionLog::getExecuteTime)
        );
    }

    /**
     * 统计待执行任务数量
     */
    public long countPendingTasks() {
        // 统计待执行任务数量（PENDING 且 executeTime > now）
        return taskRepository.selectCount(
            new LambdaQueryWrapper<ScheduledTask>()
                .eq(ScheduledTask::getStatus, ScheduledTask.TaskStatus.PENDING)
                .gt(ScheduledTask::getExecuteTime, LocalDateTime.now())
        );
    }

    /**
     * 获取调度器状态
     */
    public Map<String, Object> getSchedulerStatus() {
        Map<String, Object> status = taskScheduler.getSchedulerStatus();
        status.put("pendingTasksInDb", countPendingTasks());
        return status;
    }
}
