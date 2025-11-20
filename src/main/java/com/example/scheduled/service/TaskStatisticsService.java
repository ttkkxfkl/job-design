package com.example.scheduled.service;

import com.example.scheduled.entity.*;
import com.example.scheduled.repository.ScheduledTaskRepository;
import com.example.scheduled.repository.TaskExecutionLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 任务统计服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TaskStatisticsService {

    private final ScheduledTaskRepository taskRepository;
    private final TaskExecutionLogRepository logRepository;

    /**
     * 获取任务总体统计信息
     */
    @Transactional(readOnly = true)
    public TaskStatistics getOverallStatistics() {
        // 统计各状态任务数
        Long pendingCount = taskRepository.selectCount(new LambdaQueryWrapper<ScheduledTask>().eq(ScheduledTask::getStatus, ScheduledTask.TaskStatus.PENDING));
        Long executingCount = taskRepository.selectCount(new LambdaQueryWrapper<ScheduledTask>().eq(ScheduledTask::getStatus, ScheduledTask.TaskStatus.EXECUTING));
        Long successCount = taskRepository.selectCount(new LambdaQueryWrapper<ScheduledTask>().eq(ScheduledTask::getStatus, ScheduledTask.TaskStatus.SUCCESS));
        Long failedCount = taskRepository.selectCount(new LambdaQueryWrapper<ScheduledTask>().eq(ScheduledTask::getStatus, ScheduledTask.TaskStatus.FAILED));
        Long cancelledCount = taskRepository.selectCount(new LambdaQueryWrapper<ScheduledTask>().eq(ScheduledTask::getStatus, ScheduledTask.TaskStatus.CANCELLED));
        Long pausedCount = taskRepository.selectCount(new LambdaQueryWrapper<ScheduledTask>().eq(ScheduledTask::getStatus, ScheduledTask.TaskStatus.PAUSED));
        Long timeoutCount = taskRepository.selectCount(new LambdaQueryWrapper<ScheduledTask>().eq(ScheduledTask::getStatus, ScheduledTask.TaskStatus.TIMEOUT));

        Long totalCount = pendingCount + executingCount + successCount + failedCount + 
                          cancelledCount + pausedCount + timeoutCount;

        // 计算成功率
        Long completedCount = successCount + failedCount + timeoutCount;
        Double successRate = completedCount > 0 ? 
                (successCount * 100.0 / completedCount) : 0.0;

        // 统计执行时长
                List<TaskExecutionLog> successLogs = logRepository.selectList(
                        new LambdaQueryWrapper<TaskExecutionLog>().eq(TaskExecutionLog::getStatus, ScheduledTask.TaskStatus.SUCCESS)
                );
        
        Double avgDuration = 0.0;
        Long maxDuration = 0L;
        Long minDuration = 0L;
        
        if (!successLogs.isEmpty()) {
            List<Long> durations = successLogs.stream()
                    .map(TaskExecutionLog::getExecutionDurationMs)
                    .filter(d -> d != null && d > 0)
                    .toList();
            
            if (!durations.isEmpty()) {
                avgDuration = durations.stream()
                        .mapToLong(Long::longValue)
                        .average()
                        .orElse(0.0);
                maxDuration = durations.stream()
                        .mapToLong(Long::longValue)
                        .max()
                        .orElse(0L);
                minDuration = durations.stream()
                        .mapToLong(Long::longValue)
                        .min()
                        .orElse(0L);
            }
        }

        return TaskStatistics.builder()
                .pendingCount(pendingCount)
                .executingCount(executingCount)
                .successCount(successCount)
                .failedCount(failedCount)
                .cancelledCount(cancelledCount)
                .pausedCount(pausedCount)
                .timeoutCount(timeoutCount)
                .totalCount(totalCount)
                .successRate(Math.round(successRate * 100.0) / 100.0)
                .avgExecutionDuration(Math.round(avgDuration * 100.0) / 100.0)
                .maxExecutionDuration(maxDuration)
                .minExecutionDuration(minDuration)
                .build();
    }

    /**
     * 获取每日任务统计（最近N天）
     */
    @Transactional(readOnly = true)
    public List<DailyTaskStatistics> getDailyStatistics(int days) {
        List<DailyTaskStatistics> result = new ArrayList<>();
        LocalDate today = LocalDate.now();

        for (int i = days - 1; i >= 0; i--) {
            LocalDate date = today.minusDays(i);
            LocalDateTime startOfDay = date.atStartOfDay();
            LocalDateTime endOfDay = date.atTime(LocalTime.MAX);

            // 查询当天的执行日志
                        List<TaskExecutionLog> logs = logRepository.selectList(
                                new LambdaQueryWrapper<TaskExecutionLog>()
                                        .between(TaskExecutionLog::getExecuteTime, startOfDay, endOfDay)
                        );

            long executedCount = logs.size();
            long successCount = logs.stream()
                    .filter(log -> log.getStatus() == ScheduledTask.TaskStatus.SUCCESS)
                    .count();
            long failedCount = logs.stream()
                    .filter(log -> log.getStatus() == ScheduledTask.TaskStatus.FAILED)
                    .count();
            long timeoutCount = logs.stream()
                    .filter(log -> log.getStatus() == ScheduledTask.TaskStatus.TIMEOUT)
                    .count();

            double successRate = executedCount > 0 ? 
                    (successCount * 100.0 / executedCount) : 0.0;

            result.add(DailyTaskStatistics.builder()
                    .date(date.toString())
                    .executedCount(executedCount)
                    .successCount(successCount)
                    .failedCount(failedCount)
                    .timeoutCount(timeoutCount)
                    .successRate(Math.round(successRate * 100.0) / 100.0)
                    .build());
        }

        return result;
    }

    /**
     * 获取任务类型分布统计
     */
    @Transactional(readOnly = true)
    public List<TaskTypeStatistics> getTaskTypeDistribution() {
        List<ScheduledTask> allTasks = taskRepository.selectList(null);
        long totalCount = allTasks.size();

        if (totalCount == 0) {
            return new ArrayList<>();
        }

        // 按类型分组统计
        Map<ScheduledTask.TaskType, Long> typeCountMap = allTasks.stream()
                .collect(Collectors.groupingBy(
                        ScheduledTask::getTaskType,
                        Collectors.counting()
                ));

        return typeCountMap.entrySet().stream()
                .map(entry -> TaskTypeStatistics.builder()
                        .taskType(entry.getKey())
                        .count(entry.getValue())
                        .percentage(Math.round(entry.getValue() * 10000.0 / totalCount) / 100.0)
                        .build())
                .sorted((a, b) -> Long.compare(b.getCount(), a.getCount()))
                .toList();
    }

    /**
     * 获取任务模式分布统计
     */
    @Transactional(readOnly = true)
    public Map<String, Long> getScheduleModeDistribution() {
        List<ScheduledTask> allTasks = taskRepository.selectList(null);
        
        return allTasks.stream()
                .collect(Collectors.groupingBy(
                        task -> task.getScheduleMode().name(),
                        Collectors.counting()
                ));
    }

    /**
     * 获取任务状态分布统计
     */
    @Transactional(readOnly = true)
    public Map<String, Long> getStatusDistribution() {
        List<ScheduledTask> allTasks = taskRepository.selectList(null);
        
        return allTasks.stream()
                .collect(Collectors.groupingBy(
                        task -> task.getStatus().name(),
                        Collectors.counting()
                ));
    }
}
