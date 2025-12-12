package com.example.scheduled.alert.event;

import com.example.scheduled.alert.constant.AlertConstants;
import com.example.scheduled.alert.enums.ResolutionSource;
import lombok.Getter;

/**
 * 报警解除事件
 * 当报警被手动解除或自动恢复时发布此事件
 */
@Getter
public class AlertResolutionEvent extends AlertSystemEvent {

    private final ResolutionSource resolutionSource;
    private final String resolutionReason;

    /**
     * 新构造函数（推荐） - 包含 businessId
     */
    public AlertResolutionEvent(
            Object source,
            Long exceptionEventId,
            String businessId,
            String businessType,
            ResolutionSource resolutionSource,
            String resolutionReason) {
        super(source, exceptionEventId, AlertConstants.AlertEventType.ALERT_RESOLVED, businessId, businessType);
        this.resolutionSource = resolutionSource;
        this.resolutionReason = resolutionReason;
    }
    
    /**
     * 兼容构造函数（不推荐）
     */
    @Deprecated
    public AlertResolutionEvent(
            Object source,
            Long exceptionEventId,
            ResolutionSource resolutionSource,
            String resolutionReason) {
        super(source, exceptionEventId, AlertConstants.AlertEventType.ALERT_RESOLVED);
        this.resolutionSource = resolutionSource;
        this.resolutionReason = resolutionReason;
    }
}
