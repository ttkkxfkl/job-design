package com.example.scheduled.alert.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.example.scheduled.alert.enums.ExceptionStatus;
import com.example.scheduled.alert.enums.ResolutionSource;
import com.example.scheduled.config.JsonTypeHandler;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 异常事件表 - 记录检测到的异常及其当前状态
 * 支持异常的完整生命周期：检测 -> 升级 -> 解除 -> 恢复
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
     * 该上下文会被 AlertDependencyManager 逐步更新
     */
    @TableField(value = "detection_context", typeHandler = JsonTypeHandler.class)
    private Map<String, Object> detectionContext;

    /**
     * 待机升级状态（JSON格式）
     * 示例：{
     *   "LEVEL_2": {
     *     "status": "WAITING",
     *     "dependencies": [...],
     *     "createdAt": "2025-12-12T10:02:00"
     *   }
     * }
     */
    @TableField(value = "pending_escalations", typeHandler = JsonTypeHandler.class)
    private Map<String, Object> pendingEscalations;

    /**
     * 当前报警等级：NONE、LEVEL_1、LEVEL_2 等
     */
    private String currentAlertLevel;

    /**
     * 最后一次升级的时刻
     */
    private LocalDateTime lastEscalatedAt;

    /**
     * 异常解除的时刻
     */
    private LocalDateTime resolvedAt;

    /**
     * 异常状态：ACTIVE(活跃中)、RESOLVING(解除中)、RESOLVED(已解除)
     * ACTIVE: 正常运行，可能正在升级
     * RESOLVING: 收到解除信号，但还有任务在执行中（防止中途系统崩溃）
     * RESOLVED: 完全解除，所有相关任务已取消或完成
     */
    private String status;

    /**
     * 解除原因描述
     */
    private String resolutionReason;

    /**
     * 解除来源：MANUAL_RESOLUTION(手动解除)、AUTO_RECOVERY(自动恢复)、SYSTEM_CANCEL(系统取消)
     */
    private String resolutionSource;

    /**
     * 系统启动恢复标志
     * true: 已在系统启动时恢复过，无需再次处理
     * false: 未恢复，启动时需要处理待机任务
     */
    private Boolean recoveryFlag;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
