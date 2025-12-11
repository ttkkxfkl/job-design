package com.example.scheduled.alert.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 报警事件日志表 - 记录每次报警的触发情况（审计日志）
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
     * 触发原因（用于审计）
     */
    private String triggerReason;

    /**
     * 动作执行状态：PENDING、SENT、FAILED
     */
    private String actionStatus;

    /**
     * 动作执行失败的错误信息
     */
    private String actionErrorMessage;

    private LocalDateTime createdAt;

    /**
     * 动作执行状态枚举
     */
    public enum ActionStatus {
        PENDING,  // 待执行
        SENT,     // 已发送
        FAILED    // 失败
    }
}
