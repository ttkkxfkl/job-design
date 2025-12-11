package com.example.scheduled.alert.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.example.scheduled.config.JsonTypeHandler;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 异常事件表 - 记录检测到的异常及其当前状态
 */
@TableName(value = "exception_event", autoResultMap = true)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExceptionEvent {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long exceptionTypeId;

    /**
     * 异常发现的时刻
     */
    private LocalDateTime detectedAt;

    /**
     * 检测上下文信息（JSON格式：班次、操作人、班组等）
     */
    @TableField(value = "detection_context", typeHandler = JsonTypeHandler.class)
    private Map<String, Object> detectionContext;

    /**
     * 当前报警等级：NONE、BLUE、YELLOW、RED
     */
    private String currentAlertLevel;

    /**
     * 最后一次升级的时刻
     */
    private LocalDateTime lastEscalatedAt;

    /**
     * 异常解决的时刻
     */
    private LocalDateTime resolvedAt;

    /**
     * 异常状态：ACTIVE、RESOLVED、SUPPRESSED
     */
    private String status;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    /**
     * 异常事件状态枚举
     */
    public enum ExceptionEventStatus {
        ACTIVE,      // 活跃中
        RESOLVED,    // 已解决
        SUPPRESSED   // 已抑制
    }

    /**
     * 报警等级枚举
     */
    public enum AlertLevel {
        NONE,    // 无报警
        BLUE,    // 蓝色预警
        YELLOW,  // 黄色预警
        RED      // 红色预警
    }
}
