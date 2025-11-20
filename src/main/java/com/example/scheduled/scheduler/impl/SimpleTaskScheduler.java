package com.example.scheduled.scheduler.impl;

import com.example.scheduled.config.ScheduledTaskProperties;
import com.example.scheduled.entity.ScheduledTask;
import com.example.scheduled.entity.TaskExecutionLog;
import com.example.scheduled.executor.TaskExecutor;
import com.example.scheduled.lock.DistributedLock;
import com.example.scheduled.repository.ScheduledTaskRepository;
import com.example.scheduled.repository.TaskExecutionLogRepository;
import com.example.scheduled.scheduler.TaskScheduler;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 内置线程池任务调度器实现
 * 基于 ScheduledThreadPoolExecutor 实现秒级精度调度
 */
@Slf4j
@Service
@ConditionalOnProperty(name = "scheduled.task.scheduler-type", havingValue = "simple", matchIfMissing = true)
public class SimpleTaskScheduler implements TaskScheduler {

    private final ScheduledTaskRepository taskRepository;
    private final TaskExecutionLogRepository logRepository;
    private final ScheduledTaskProperties properties;
    private final DistributedLock distributedLock;
    private final Map<ScheduledTask.TaskType, TaskExecutor> executorMap;

    private ScheduledThreadPoolExecutor scheduler;
    private final ConcurrentHashMap<Long, ScheduledFuture<?>> scheduledTasks = new ConcurrentHashMap<>();

    public SimpleTaskScheduler(ScheduledTaskRepository taskRepository,
                               TaskExecutionLogRepository logRepository,
                               ScheduledTaskProperties properties,
                               DistributedLock distributedLock,
                               List<TaskExecutor> executors) {
        this.taskRepository = taskRepository;
        this.logRepository = logRepository;
        this.properties = properties;
        this.distributedLock = distributedLock;
        // 构建执行器映射表
        this.executorMap = executors.stream()
                .collect(Collectors.toMap(
                        executor -> {
                            for (ScheduledTask.TaskType type : ScheduledTask.TaskType.values()) {
                                if (executor.support(type)) {
                                    return type;
                                }
                            }
                            throw new IllegalStateException("执行器未声明支持的任务类型：" + executor.getClass().getName());
                        },
                        Function.identity()
                ));
    }

    @PostConstruct
    @Override
    public void init() {
        log.info("初始化 Simple 任务调度器，核心线程数：{}", properties.getCorePoolSize());

        scheduler = new ScheduledThreadPoolExecutor(
                properties.getCorePoolSize(),
                new ThreadPoolExecutor.CallerRunsPolicy()
        );
        scheduler.setRemoveOnCancelPolicy(true);

        // 加载所有待执行任务（仅限 ONCE 模式）
        loadAllPendingOnceTasks();

        log.info("Simple 任务调度器初始化完成，已注册执行器：{}",
                executorMap.values().stream()
                        .map(TaskExecutor::getName)
                        .collect(Collectors.joining(", ")));
    }

    /**
     * 加载所有待执行的一次性任务（ONCE 模式）
     */
    @Transactional(readOnly = true)
    public void loadAllPendingOnceTasks() {
        List<ScheduledTask> tasks = taskRepository.selectList(
            new LambdaQueryWrapper<ScheduledTask>()
                .eq(ScheduledTask::getStatus, ScheduledTask.TaskStatus.PENDING)
                .orderByAsc(ScheduledTask::getExecuteTime)
        );
        
        // 过滤出 ONCE 模式的任务
        List<ScheduledTask> onceTasks = tasks.stream()
                .filter(task -> task.getScheduleMode() == ScheduledTask.ScheduleMode.ONCE)
                .toList();
        
        log.info("加载所有待执行的一次性任务，数量：{}", onceTasks.size());
        
        for (ScheduledTask task : onceTasks) {
            scheduleTask(task);
        }
    }

    @Override
    public void scheduleTask(ScheduledTask task) {
        // Simple 调度器仅支持 ONCE 模式
        if (task.getScheduleMode() != ScheduledTask.ScheduleMode.ONCE) {
            log.warn("Simple 调度器不支持 CRON 模式任务：{}", task.getTaskName());
            return;
        }

        // 如果已经调度过，跳过
        if (scheduledTasks.containsKey(task.getId())) {
            log.debug("任务 [{}] 已在调度队列中，跳过", task.getTaskName());
            return;
        }

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime executeTime = task.getExecuteTime();

        // 计算延迟时间（秒）
        long delay = java.time.Duration.between(now, executeTime).getSeconds();

        if (delay < 0) {
            log.warn("任务 [{}] 的执行时间已过期，立即执行", task.getTaskName());
            delay = 0;
        }

        // 提交到调度器
        ScheduledFuture<?> future = scheduler.schedule(
                () -> executeTask(task.getId()),
                delay,
                TimeUnit.SECONDS
        );

        scheduledTasks.put(task.getId(), future);
        log.info("任务 [{}] 已加入 Simple 调度队列，执行时间：{}，延迟：{}秒",
                task.getTaskName(), executeTime, delay);
    }

    @Override
    @Transactional
    public void executeTask(Long taskId) {
        String lockKey = "task:" + taskId;

        // 尝试获取分布式锁（防止集群重复执行）
        if (!distributedLock.tryLock(lockKey, 300)) {
            log.warn("任务 [{}] 获取锁失败，可能正在被其他节点执行", taskId);
            return;
        }

        try {
            ScheduledTask task = taskRepository.selectById(taskId);
            if (task == null) {
                log.error("任务不存在，ID：{}", taskId);
                return;
            }

            // 检查任务状态
            if (task.getStatus() != ScheduledTask.TaskStatus.PENDING) {
                log.warn("任务 [{}] 状态不是PENDING，当前状态：{}，跳过执行",
                        task.getTaskName(), task.getStatus());
                return;
            }

            log.info("开始执行任务 [{}]，任务ID：{}", task.getTaskName(), taskId);

            // 更新任务状态为执行中
            task.setStatus(ScheduledTask.TaskStatus.EXECUTING);
            task.setLastExecuteTime(LocalDateTime.now());
            if (task.getId() == null) {
                taskRepository.insert(task);
            } else {
                taskRepository.updateById(task);
            }

            long startTime = System.currentTimeMillis();
            TaskExecutionLog executionLog = TaskExecutionLog.builder()
                    .taskId(taskId)
                    .executeTime(LocalDateTime.now())
                    .build();

            boolean needReschedule = false;
            try {
                // 获取对应的执行器
                TaskExecutor executor = executorMap.get(task.getTaskType());
                if (executor == null) {
                    throw new IllegalStateException("未找到任务类型 [" + task.getTaskType() + "] 的执行器");
                }

                // 获取任务超时时间（秒），默认300秒（5分钟）
                Long timeoutSeconds = task.getExecutionTimeout();
                if (timeoutSeconds == null || timeoutSeconds <= 0) {
                    timeoutSeconds = 300L;
                }

                // 使用 ExecutorService 提交任务并设置超时
                ExecutorService executorService = Executors.newSingleThreadExecutor();
                Future<?> executionFuture = executorService.submit(() -> {
                    try {
                        executor.execute(task);
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                });

                try {
                    // 等待任务完成，带超时控制
                    executionFuture.get(timeoutSeconds, TimeUnit.SECONDS);

                    // 更新任务状态为成功
                    task.setStatus(ScheduledTask.TaskStatus.SUCCESS);
                    task.setErrorMessage(null);
                    executionLog.setStatus(ScheduledTask.TaskStatus.SUCCESS);

                    log.info("任务 [{}] 执行成功", task.getTaskName());

                } catch (TimeoutException e) {
                    log.error("任务 [{}] 执行超时，超时时间：{}秒", task.getTaskName(), timeoutSeconds);
                    
                    // 尝试取消任务
                    executionFuture.cancel(true);
                    
                    // 增加重试次数
                    task.setRetryCount(task.getRetryCount() + 1);
                    task.setErrorMessage("任务执行超时：" + timeoutSeconds + "秒");

                    // 判断是否还能重试
                    if (task.getRetryCount() >= task.getMaxRetryCount()) {
                        task.setStatus(ScheduledTask.TaskStatus.TIMEOUT);
                        log.error("任务 [{}] 已达到最大重试次数，标记为超时失败", task.getTaskName());
                    } else {
                        // 重新调度：计算下次执行时间并回到 PENDING
                        task.setExecuteTime(LocalDateTime.now().plusSeconds(properties.getRetryIntervalSeconds()));
                        task.setStatus(ScheduledTask.TaskStatus.PENDING);
                        needReschedule = true;
                        log.warn("任务 [{}] 将在 {} 秒后重试，当前重试次数：{}/{}",
                                task.getTaskName(), properties.getRetryIntervalSeconds(),
                                task.getRetryCount(), task.getMaxRetryCount());
                    }

                    executionLog.setStatus(ScheduledTask.TaskStatus.TIMEOUT);
                    executionLog.setErrorMessage("执行超时：" + timeoutSeconds + "秒");
                    
                } catch (ExecutionException e) {
                    throw e.getCause() != null ? (Exception) e.getCause() : e;
                } finally {
                    executorService.shutdownNow();
                }

            } catch (Exception e) {
                log.error("任务 [{}] 执行失败", task.getTaskName(), e);

                // 增加重试次数
                task.setRetryCount(task.getRetryCount() + 1);
                task.setErrorMessage(e.getMessage());

                // 判断是否还能重试
                if (task.getRetryCount() >= task.getMaxRetryCount()) {
                    task.setStatus(ScheduledTask.TaskStatus.FAILED);
                    log.error("任务 [{}] 已达到最大重试次数，标记为失败", task.getTaskName());
                } else {
                    // 即时重新调度：计算下次执行时间并回到 PENDING
                    task.setExecuteTime(LocalDateTime.now().plusSeconds(properties.getRetryIntervalSeconds()));
                    task.setStatus(ScheduledTask.TaskStatus.PENDING);
                    needReschedule = true;
                    log.warn("任务 [{}] 将在 {} 秒后重试，当前重试次数：{}/{}",
                            task.getTaskName(), properties.getRetryIntervalSeconds(),
                            task.getRetryCount(), task.getMaxRetryCount());
                }

                executionLog.setStatus(ScheduledTask.TaskStatus.FAILED);
                executionLog.setErrorMessage(e.getMessage());
            }

            long duration = System.currentTimeMillis() - startTime;
            executionLog.setExecutionDurationMs(duration);

            // 保存执行结果
            if (task.getId() == null) {
                taskRepository.insert(task);
            } else {
                taskRepository.updateById(task);
            }
            if (executionLog.getId() == null) {
                logRepository.insert(executionLog);
            } else {
                logRepository.updateById(executionLog);
            }

            // 从调度队列中移除
            scheduledTasks.remove(taskId);

            // 如需重试，重新加入调度队列
            if (needReschedule) {
                scheduleTask(task);
            }

        } finally {
            distributedLock.unlock(lockKey);
        }
    }

    @Override
    @Transactional
    public boolean cancelTask(Long taskId) {
        ScheduledTask task = taskRepository.selectById(taskId);
        if (task == null) {
            return false;
        }

        // 取消调度
        ScheduledFuture<?> future = scheduledTasks.remove(taskId);
        if (future != null) {
            future.cancel(false);
        }

        // 更新状态
        task.setStatus(ScheduledTask.TaskStatus.CANCELLED);
        if (task.getId() == null) {
            taskRepository.insert(task);
        } else {
            taskRepository.updateById(task);
        }

        log.info("任务 [{}] 已取消", task.getTaskName());
        return true;
    }

    @PreDestroy
    @Override
    public void destroy() {
        log.info("正在关闭 Simple 任务调度器...");
        if (scheduler != null) {
            scheduler.shutdown();
            try {
                if (!scheduler.awaitTermination(60, TimeUnit.SECONDS)) {
                    scheduler.shutdownNow();
                }
            } catch (InterruptedException e) {
                scheduler.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
        log.info("Simple 任务调度器已关闭");
    }

    @Override
    public Map<String, Object> getSchedulerStatus() {
        return Map.of(
                "schedulerType", "Simple",
                "activeCount", scheduler.getActiveCount(),
                "poolSize", scheduler.getPoolSize(),
                "queueSize", scheduler.getQueue().size(),
                "scheduledTaskCount", scheduledTasks.size(),
                "completedTaskCount", scheduler.getCompletedTaskCount()
        );
    }

    @Override
    public String getSchedulerType() {
        return "Simple";
    }
}
