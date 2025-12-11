package com.example.scheduled.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.example.scheduled.config.JsonTypeHandler;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 定时任务实体类
 */
@TableName(value = "scheduled_task", autoResultMap = true)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ScheduledTask {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String taskName;

    private TaskType taskType;

    /**
     * 调度模式：ONCE（一次性定时）或 CRON（周期性调度）
     */
    private ScheduleMode scheduleMode;

    /**
     * 执行时间（用于 ONCE 模式）
     */
    private LocalDateTime executeTime;

    /**
     * Cron 表达式（用于 CRON 模式）
     */
    private String cronExpression;

    /**
     * 任务优先级（0-10，数字越大优先级越高，默认 5）
     */
    private Integer priority;

    /**
     * 执行超时时间（秒），超时会被强制终止
     */
    private Long executionTimeout;

    @TableField(value = "task_data", typeHandler = JsonTypeHandler.class)
    private Map<String, Object> taskData;

    private TaskStatus status;

    private Integer retryCount;

    private Integer maxRetryCount;

    private LocalDateTime lastExecuteTime;

    private String errorMessage;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    // MyBatis-Plus 可用 @TableField(fill = FieldFill.INSERT/UPDATE) 实现自动填充

    /**
     * 调度模式枚举
     */
    public enum ScheduleMode {
        ONCE,   // 一次性定时执行
        CRON    // 周期性 Cron 调度
    }

    /**
     * 任务类型枚举
     */
    public enum TaskType {
        LOG,        // 日志打印
        EMAIL,      // 邮件通知
        SMS,        // 短信通知
        WEBHOOK,    // HTTP回调
        MQ,         // 消息队列
        PLAN,       // 任务计划
        ALERT       // 报警升级评估
    }

    /**
     * 任务状态枚举
     */
    public enum TaskStatus {
        PENDING,    // 待执行
        EXECUTING,  // 执行中
        SUCCESS,    // 成功
        FAILED,     // 失败
        CANCELLED,  // 已取消
        PAUSED,     // 已暂停
        TIMEOUT     // 执行超时
    }
}
