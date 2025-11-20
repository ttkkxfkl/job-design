package com.example.scheduled.scheduler.impl;

import com.example.scheduled.config.ScheduledTaskProperties;
import com.example.scheduled.entity.ScheduledTask;
import com.example.scheduled.entity.TaskExecutionLog;
import com.example.scheduled.executor.TaskExecutor;
import com.example.scheduled.job.ScheduledTaskJob;
import com.example.scheduled.lock.DistributedLock;
import com.example.scheduled.repository.ScheduledTaskRepository;
import com.example.scheduled.repository.TaskExecutionLogRepository;
import com.example.scheduled.scheduler.TaskScheduler;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.quartz.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Quartz 任务调度器实现
 * 支持 ONCE（一次性定时）和 CRON（周期性调度）两种模式
 */
@Slf4j
@Service
@ConditionalOnProperty(name = "scheduled.task.scheduler-type", havingValue = "quartz")
public class QuartzTaskScheduler implements TaskScheduler {

    private final Scheduler quartzScheduler;
    private final ScheduledTaskRepository taskRepository;
    private final TaskExecutionLogRepository logRepository;
    private final ScheduledTaskProperties properties;
    private final DistributedLock distributedLock;
    private final Map<ScheduledTask.TaskType, TaskExecutor> executorMap;

    public QuartzTaskScheduler(Scheduler quartzScheduler,
                               ScheduledTaskRepository taskRepository,
                               TaskExecutionLogRepository logRepository,
                               ScheduledTaskProperties properties,
                               DistributedLock distributedLock,
                               List<TaskExecutor> executors) {
        this.quartzScheduler = quartzScheduler;
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
        log.info("初始化 Quartz 任务调度器");

        try {
            // 加载所有待执行任务（支持 ONCE 和 CRON 模式）
            loadAllPendingTasks();

            log.info("Quartz 任务调度器初始化完成，已注册执行器：{}",
                    executorMap.values().stream()
                            .map(TaskExecutor::getName)
                            .collect(Collectors.joining(", ")));
        } catch (Exception e) {
            log.error("Quartz 调度器初始化失败", e);
            throw new RuntimeException("Quartz 调度器初始化失败", e);
        }
    }

    /**
     * 加载所有待执行任务（支持 ONCE 和 CRON）
     */
    @Transactional(readOnly = true)
    public void loadAllPendingTasks() throws SchedulerException {
        List<ScheduledTask> tasks = taskRepository.selectList(
            new LambdaQueryWrapper<ScheduledTask>()
                .eq(ScheduledTask::getStatus, ScheduledTask.TaskStatus.PENDING)
                .orderByAsc(ScheduledTask::getExecuteTime)
        );
        log.info("加载所有待执行任务，数量：{}", tasks.size());

        for (ScheduledTask task : tasks) {
            scheduleTask(task);
        }
    }

    @Override
    public void scheduleTask(ScheduledTask task) {
        try {
            String jobKey = "task-" + task.getId();
            String groupKey = "scheduled-tasks";

            // 检查任务是否已存在
            JobKey jKey = JobKey.jobKey(jobKey, groupKey);
            if (quartzScheduler.checkExists(jKey)) {
                log.debug("任务 [{}] 已在 Quartz 调度队列中，跳过", task.getTaskName());
                return;
            }

            // 创建 JobDetail
            JobDetail jobDetail = JobBuilder.newJob(ScheduledTaskJob.class)
                    .withIdentity(jKey)
                    .usingJobData("taskId", task.getId())
                    .storeDurably(false)
                    .build();

            // 获取任务优先级，默认为5
            Integer priority = task.getPriority();
            if (priority == null || priority < 0 || priority > 10) {
                priority = 5;
            }

            Trigger trigger;

            if (task.getScheduleMode() == ScheduledTask.ScheduleMode.CRON) {
                // CRON 模式：使用 Cron 表达式
                trigger = TriggerBuilder.newTrigger()
                        .withIdentity("trigger-" + task.getId(), groupKey)
                        .withPriority(priority)  // 设置优先级
                        .withSchedule(CronScheduleBuilder.cronSchedule(task.getCronExpression())
                                .withMisfireHandlingInstructionDoNothing())
                        .build();

                log.info("任务 [{}] 已加入 Quartz CRON 调度，表达式：{}，优先级：{}",
                        task.getTaskName(), task.getCronExpression(), priority);

            } else {
                // ONCE 模式：一次性定时执行
                LocalDateTime executeTime = task.getExecuteTime();
                Date startTime = Date.from(executeTime.atZone(ZoneId.systemDefault()).toInstant());

                // 如果时间已过期，立即执行
                if (startTime.before(new Date())) {
                    log.warn("任务 [{}] 的执行时间已过期，立即执行", task.getTaskName());
                    startTime = new Date();
                }

                trigger = TriggerBuilder.newTrigger()
                        .withIdentity("trigger-" + task.getId(), groupKey)
                        .withPriority(priority)  // 设置优先级
                        .startAt(startTime)
                        .withSchedule(SimpleScheduleBuilder.simpleSchedule()
                                .withMisfireHandlingInstructionFireNow())
                        .build();

                log.info("任务 [{}] 已加入 Quartz ONCE 调度，执行时间：{}，优先级：{}",
                        task.getTaskName(), executeTime, priority);
            }

            // 调度任务
            quartzScheduler.scheduleJob(jobDetail, trigger);

        } catch (Exception e) {
            log.error("调度任务失败：{}", task.getTaskName(), e);
            throw new RuntimeException("调度任务失败", e);
        }
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

            // 对于 CRON 任务，不检查 PENDING 状态（允许周期执行）
            if (task.getScheduleMode() == ScheduledTask.ScheduleMode.ONCE) {
                if (task.getStatus() != ScheduledTask.TaskStatus.PENDING) {
                    log.warn("任务 [{}] 状态不是PENDING，当前状态：{}，跳过执行",
                            task.getTaskName(), task.getStatus());
                    return;
                }
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

                    // 更新任务状态
                    if (task.getScheduleMode() == ScheduledTask.ScheduleMode.CRON) {
                        // CRON 任务执行成功后恢复 PENDING 状态，等待下次触发
                        task.setStatus(ScheduledTask.TaskStatus.PENDING);
                    } else {
                        // ONCE 任务执行成功后标记为 SUCCESS
                        task.setStatus(ScheduledTask.TaskStatus.SUCCESS);
                    }
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

                    // 判断是否还能重试（仅限 ONCE 模式）
                    if (task.getScheduleMode() == ScheduledTask.ScheduleMode.ONCE) {
                        if (task.getRetryCount() >= task.getMaxRetryCount()) {
                            task.setStatus(ScheduledTask.TaskStatus.TIMEOUT);
                            log.error("任务 [{}] 已达到最大重试次数，标记为超时失败", task.getTaskName());
                            // 从 Quartz 移除任务
                            cancelTask(taskId);
                        } else {
                            // 即时重新调度
                            task.setExecuteTime(LocalDateTime.now().plusSeconds(properties.getRetryIntervalSeconds()));
                            task.setStatus(ScheduledTask.TaskStatus.PENDING);
                            needReschedule = true;
                            log.warn("任务 [{}] 将在 {} 秒后重试，当前重试次数：{}/{}",
                                    task.getTaskName(), properties.getRetryIntervalSeconds(),
                                    task.getRetryCount(), task.getMaxRetryCount());
                        }
                    } else {
                        // CRON 任务超时后保持 PENDING，等待下次调度
                        task.setStatus(ScheduledTask.TaskStatus.PENDING);
                        log.warn("CRON 任务 [{}] 执行超时，将等待下次触发", task.getTaskName());
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

                // 判断是否还能重试（仅限 ONCE 模式）
                if (task.getScheduleMode() == ScheduledTask.ScheduleMode.ONCE) {
                    if (task.getRetryCount() >= task.getMaxRetryCount()) {
                        task.setStatus(ScheduledTask.TaskStatus.FAILED);
                        log.error("任务 [{}] 已达到最大重试次数，标记为失败", task.getTaskName());
                        
                        // 从 Quartz 移除任务
                        cancelTask(taskId);
                    } else {
                        // 即时重新调度
                        task.setExecuteTime(LocalDateTime.now().plusSeconds(properties.getRetryIntervalSeconds()));
                        task.setStatus(ScheduledTask.TaskStatus.PENDING);
                        needReschedule = true;
                        log.warn("任务 [{}] 将在 {} 秒后重试，当前重试次数：{}/{}",
                                task.getTaskName(), properties.getRetryIntervalSeconds(),
                                task.getRetryCount(), task.getMaxRetryCount());
                    }
                } else {
                    // CRON 任务失败后保持 PENDING，等待下次调度
                    task.setStatus(ScheduledTask.TaskStatus.PENDING);
                    log.warn("CRON 任务 [{}] 执行失败，将等待下次触发", task.getTaskName());
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

            // 如需重试（ONCE 模式），先取消再重新调度
            if (needReschedule) {
                cancelTask(taskId);
                scheduleTask(task);
            }

        } finally {
            distributedLock.unlock(lockKey);
        }
    }

    @Override
    @Transactional
    public boolean cancelTask(Long taskId) {
        try {
            ScheduledTask task = taskRepository.selectById(taskId);
            if (task == null) {
                return false;
            }

            String jobKey = "task-" + taskId;
            String groupKey = "scheduled-tasks";
            JobKey jKey = JobKey.jobKey(jobKey, groupKey);

            // 从 Quartz 删除任务
            boolean deleted = quartzScheduler.deleteJob(jKey);

            if (deleted) {
                // 更新状态
                task.setStatus(ScheduledTask.TaskStatus.CANCELLED);
                if (task.getId() == null) {
                    taskRepository.insert(task);
                } else {
                    taskRepository.updateById(task);
                }

                log.info("任务 [{}] 已从 Quartz 取消", task.getTaskName());
                return true;
            }

            return false;
        } catch (SchedulerException e) {
            log.error("取消任务失败，任务ID：{}", taskId, e);
            return false;
        }
    }

    @PreDestroy
    @Override
    public void destroy() {
        log.info("正在关闭 Quartz 任务调度器...");
        try {
            if (quartzScheduler != null && !quartzScheduler.isShutdown()) {
                quartzScheduler.shutdown(true);
            }
            log.info("Quartz 任务调度器已关闭");
        } catch (SchedulerException e) {
            log.error("关闭 Quartz 调度器失败", e);
        }
    }

    @Override
    public Map<String, Object> getSchedulerStatus() {
        try {
            SchedulerMetaData metaData = quartzScheduler.getMetaData();
            return Map.of(
                    "schedulerType", "Quartz",
                    "schedulerName", quartzScheduler.getSchedulerName(),
                    "schedulerInstanceId", quartzScheduler.getSchedulerInstanceId(),
                    "numberOfJobsExecuted", metaData.getNumberOfJobsExecuted(),
                    "isStarted", quartzScheduler.isStarted(),
                    "isInStandbyMode", quartzScheduler.isInStandbyMode(),
                    "runningSince", metaData.getRunningSince()
            );
        } catch (SchedulerException e) {
            log.error("获取 Quartz 调度器状态失败", e);
            return Map.of("schedulerType", "Quartz", "error", e.getMessage());
        }
    }

    @Override
    public String getSchedulerType() {
        return "Quartz";
    }
}
