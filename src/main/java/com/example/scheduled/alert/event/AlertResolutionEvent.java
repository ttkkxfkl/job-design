package com.example.scheduled.alert.event;

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

    public AlertResolutionEvent(
            Object source,
            Long exceptionEventId,
            ResolutionSource resolutionSource,
            String resolutionReason) {
        super(source, exceptionEventId, "ALERT_RESOLVED");
        this.resolutionSource = resolutionSource;
        this.resolutionReason = resolutionReason;
    }
}
