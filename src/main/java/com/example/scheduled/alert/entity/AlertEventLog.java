package com.example.scheduled.alert.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 报警事件日志表 - 记录每次报警的触发情况及状态变更（审计日志）
 * 支持多种事件类型：报警触发、升级、解除、任务取消、系统恢复等
 */
@TableName("alert_event_log")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AlertEventLog {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long exceptionEventId;

    private Long alertRuleId;

    /**
     * 报警触发时刻
     */
    private LocalDateTime triggeredAt;

    /**
     * 报警等级
     */
    private String alertLevel;

    /**
     * 事件类型：ALERT_TRIGGERED(报警触发)、ALERT_ESCALATED(报警升级)、ALERT_RESOLVED(报警解除)、
     *          TASK_CANCELLED(任务取消)、SYSTEM_RECOVERY(系统恢复)
     */
    private String eventType;

    /**
     * 触发原因（用于审计）
     */
    private String triggerReason;

    /**
     * 动作执行状态：PENDING、SENT、FAILED、COMPLETED
     */
    private String actionStatus;

    /**
     * 动作执行失败的错误信息
     */
    private String actionErrorMessage;

    private LocalDateTime createdAt;
}
