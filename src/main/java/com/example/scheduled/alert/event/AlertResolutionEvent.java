package com.example.scheduled.alert.event;

import com.example.scheduled.alert.constant.AlertConstants;
import lombok.Getter;

/**
 * 报警解除事件
 * 当报警被手动解除或自动恢复时发布此事件
 */
@Getter
public class AlertResolutionEvent extends AlertSystemEvent {

    private final String resolutionReason;

    /**
     * 新构造函数（推荐） - 包含 businessId
     */
    public AlertResolutionEvent(
            Object source,
            Long exceptionEventId,
            String businessId,
            String businessType) {
        super(source, exceptionEventId, AlertConstants.AlertEventType.ALERT_RESOLVED, businessId, businessType);
        this.resolutionReason = null;
    }

    /**
     * 兼容构造函数 - 包含解除原因
     */
    public AlertResolutionEvent(
            Object source,
            Long exceptionEventId,
            String businessId,
            String businessType,
            String resolutionReason) {
        super(source, exceptionEventId, AlertConstants.AlertEventType.ALERT_RESOLVED, businessId, businessType);
        this.resolutionReason = resolutionReason;
    }
}
