package com.example.scheduled.controller;

import com.example.scheduled.dto.ApiResponse;
import com.example.scheduled.dto.CreateTaskRequest;
import com.example.scheduled.dto.TaskTypeInfo;
import com.example.scheduled.entity.*;
import com.example.scheduled.service.TaskManagementService;
import com.example.scheduled.service.TaskStatisticsService;
import com.example.scheduled.service.TaskTypeInfoService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 定时任务管理接口
 */
@Slf4j
@RestController
@RequestMapping("/api/tasks")
@RequiredArgsConstructor
public class TaskController {

    private final TaskManagementService taskManagementService;
    private final TaskStatisticsService taskStatisticsService;
    private final TaskTypeInfoService taskTypeInfoService;

    /**
     * 创建一次性定时任务（ONCE 模式）
     */
    @PostMapping("/once")
    public ApiResponse<ScheduledTask> createOnceTask(@Valid @RequestBody CreateTaskRequest request) {
        try {
            ScheduledTask task = taskManagementService.createOnceTask(
                    request.getTaskName(),
                    request.getTaskType(),
                    request.getExecuteTime(),
                    request.getTaskData(),
                    request.getMaxRetryCount(),
                    request.getPriority(),
                    request.getExecutionTimeout()
            );
            return ApiResponse.success("一次性任务创建成功", task);
        } catch (Exception e) {
            log.error("创建一次性任务失败", e);
            return ApiResponse.error(e.getMessage());
        }
    }

    /**
     * 创建周期性 Cron 任务（CRON 模式）
     */
    @PostMapping("/cron")
    public ApiResponse<ScheduledTask> createCronTask(@Valid @RequestBody CreateTaskRequest request) {
        try {
            if (request.getCronExpression() == null || request.getCronExpression().trim().isEmpty()) {
                return ApiResponse.error("Cron 表达式不能为空");
            }
            
            ScheduledTask task = taskManagementService.createCronTask(
                    request.getTaskName(),
                    request.getTaskType(),
                    request.getCronExpression(),
                    request.getTaskData(),
                    request.getPriority(),
                    request.getExecutionTimeout()
            );
            return ApiResponse.success("Cron 任务创建成功", task);
        } catch (Exception e) {
            log.error("创建 Cron 任务失败", e);
            return ApiResponse.error(e.getMessage());
        }
    }

    /**
     * 创建定时任务（兼容旧接口，默认为 ONCE 模式）
     */
    @PostMapping
    public ApiResponse<ScheduledTask> createTask(@Valid @RequestBody CreateTaskRequest request) {
        // 根据是否有 cronExpression 判断模式
        if (request.getCronExpression() != null && !request.getCronExpression().trim().isEmpty()) {
            return createCronTask(request);
        } else {
            return createOnceTask(request);
        }
    }

    /**
     * 查询任务详情
     */
    @GetMapping("/{id}")
    public ApiResponse<ScheduledTask> getTask(@PathVariable Long id) {
        ScheduledTask task = taskManagementService.getTaskById(id);
        if (task == null) {
            return ApiResponse.error(404, "任务不存在");
        }
        return ApiResponse.success(task);
    }

    /**
     * 查询所有任务
     */
    @GetMapping
    public ApiResponse<List<ScheduledTask>> getAllTasks(
            @RequestParam(required = false) ScheduledTask.TaskStatus status) {
        List<ScheduledTask> tasks;
        if (status != null) {
            tasks = taskManagementService.getTasksByStatus(status);
        } else {
            tasks = taskManagementService.getAllTasks();
        }
        return ApiResponse.success(tasks);
    }

    /**
     * 取消任务
     */
    @DeleteMapping("/{id}")
    public ApiResponse<Boolean> cancelTask(@PathVariable Long id) {
        boolean success = taskManagementService.cancelTask(id);
        if (success) {
            return ApiResponse.success("任务已取消", true);
        } else {
            return ApiResponse.error("任务取消失败或任务不存在");
        }
    }

    /**
     * 暂停任务
     */
    @PutMapping("/{id}/pause")
    public ApiResponse<Boolean> pauseTask(@PathVariable Long id) {
        boolean success = taskManagementService.pauseTask(id);
        if (success) {
            return ApiResponse.success("任务已暂停", true);
        } else {
            return ApiResponse.error("任务暂停失败，请检查任务状态");
        }
    }

    /**
     * 恢复已暂停的任务
     */
    @PutMapping("/{id}/resume")
    public ApiResponse<Boolean> resumeTask(@PathVariable Long id) {
        boolean success = taskManagementService.resumeTask(id);
        if (success) {
            return ApiResponse.success("任务已恢复", true);
        } else {
            return ApiResponse.error("任务恢复失败，请检查任务状态");
        }
    }

    /**
     * 手动立即重试任务
     */
    @PostMapping("/{id}/retry")
    public ApiResponse<Boolean> retryTask(@PathVariable Long id) {
        boolean success = taskManagementService.retryTaskNow(id);
        if (success) {
            return ApiResponse.success("任务已设置为立即重试", true);
        } else {
            return ApiResponse.error("任务重试失败或任务不存在");
        }
    }

    /**
     * 查询任务执行历史
     */
    @GetMapping("/{id}/logs")
    public ApiResponse<List<TaskExecutionLog>> getTaskLogs(@PathVariable Long id) {
        List<TaskExecutionLog> logs = taskManagementService.getTaskExecutionLogs(id);
        return ApiResponse.success(logs);
    }

    /**
     * 获取调度器状态
     */
    @GetMapping("/scheduler/status")
    public ApiResponse<Map<String, Object>> getSchedulerStatus() {
        Map<String, Object> status = taskManagementService.getSchedulerStatus();
        return ApiResponse.success(status);
    }

    /**
     * 统计待执行任务数量
     */
    @GetMapping("/count/pending")
    public ApiResponse<Long> countPendingTasks() {
        long count = taskManagementService.countPendingTasks();
        return ApiResponse.success(count);
    }

    /**
     * 获取任务总体统计信息
     */
    @GetMapping("/statistics/summary")
    public ApiResponse<TaskStatistics> getStatisticsSummary() {
        TaskStatistics statistics = taskStatisticsService.getOverallStatistics();
        return ApiResponse.success(statistics);
    }

    /**
     * 获取每日任务统计（最近N天）
     */
    @GetMapping("/statistics/daily")
    public ApiResponse<List<DailyTaskStatistics>> getDailyStatistics(
            @RequestParam(defaultValue = "7") int days) {
        if (days <= 0 || days > 90) {
            return ApiResponse.error("天数范围应在 1-90 之间");
        }
        List<DailyTaskStatistics> statistics = taskStatisticsService.getDailyStatistics(days);
        return ApiResponse.success(statistics);
    }

    /**
     * 获取任务类型分布统计
     */
    @GetMapping("/statistics/type-distribution")
    public ApiResponse<List<TaskTypeStatistics>> getTypeDistribution() {
        List<TaskTypeStatistics> distribution = taskStatisticsService.getTaskTypeDistribution();
        return ApiResponse.success(distribution);
    }

    /**
     * 获取任务模式分布统计
     */
    @GetMapping("/statistics/mode-distribution")
    public ApiResponse<Map<String, Long>> getModeDistribution() {
        Map<String, Long> distribution = taskStatisticsService.getScheduleModeDistribution();
        return ApiResponse.success(distribution);
    }

    /**
     * 获取任务状态分布统计
     */
    @GetMapping("/statistics/status-distribution")
    public ApiResponse<Map<String, Long>> getStatusDistribution() {
        Map<String, Long> distribution = taskStatisticsService.getStatusDistribution();
        return ApiResponse.success(distribution);
    }

    /**
     * 获取任务类型枚举的展示列表（基于执行器注解收集）
     */
    @GetMapping("/types")
    public ApiResponse<List<TaskTypeInfo>> getTaskTypes() {
        return ApiResponse.success(taskTypeInfoService.getAllTaskTypes());
    }
}
