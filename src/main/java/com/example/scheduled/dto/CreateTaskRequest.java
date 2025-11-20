package com.example.scheduled.dto;

import com.example.scheduled.entity.ScheduledTask;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 创建任务请求DTO
 */
@Data
public class CreateTaskRequest {

    @NotBlank(message = "任务名称不能为空")
    private String taskName;

    @NotNull(message = "任务类型不能为空")
    private ScheduledTask.TaskType taskType;

    /**
     * 执行时间（ONCE 模式使用）
     */
    @Future(message = "执行时间必须是将来的时间")
    private LocalDateTime executeTime;

    /**
     * Cron 表达式（CRON 模式使用）
     */
    private String cronExpression;

    /**
     * 任务优先级：0-10，数值越大优先级越高，默认5
     */
    private Integer priority;

    /**
     * 任务执行超时时间（秒），默认300秒（5分钟）
     */
    private Long executionTimeout;

    private Map<String, Object> taskData;

    /**
     * 最大重试次数（仅 ONCE 模式有效）
     */
    private Integer maxRetryCount;
}
