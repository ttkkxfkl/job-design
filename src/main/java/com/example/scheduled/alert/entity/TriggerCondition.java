package com.example.scheduled.alert.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalTime;
import java.time.LocalDateTime;

/**
 * 触发条件表 - 定义报警的触发时机
 */
@TableName("trigger_condition")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TriggerCondition {

    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 条件类型：ABSOLUTE(固定时刻)、RELATIVE(相对事件)、HYBRID(混合)
     */
    private String conditionType;

    /**
     * 绝对时间触发：固定时刻（如 16:00）
     */
    private LocalTime absoluteTime;

    /**
     * 相对时间触发：事件类型（SHIFT_START、LAST_OPERATION、EXCEPTION_DETECTED）
     */
    private String relativeEventType;

    /**
     * 相对时间：从事件开始后的分钟数
     */
    private Integer relativeDurationMinutes;

    /**
     * 时间窗口开始
     */
    private LocalTime timeWindowStart;

    /**
     * 时间窗口结束
     */
    private LocalTime timeWindowEnd;

    /**
     * 混合条件时的逻辑操作符：AND、OR
     */
    private String logicalOperator;

    /**
     * 混合条件时关联的条件IDs（逗号分隔）
     */
    private String combinedConditionIds;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    /**
     * 触发条件类型枚举
     */
    public enum ConditionType {
        ABSOLUTE,   // 固定时刻
        RELATIVE,   // 相对事件时间
        HYBRID      // 混合条件
    }

    /**
     * 相对事件类型枚举
     */
    public enum RelativeEventType {
        SHIFT_START,           // 班次开始
        LAST_OPERATION,        // 上一次操作
        EXCEPTION_DETECTED     // 异常发现时刻
    }
}
