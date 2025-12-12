package com.example.scheduled.alert.event;

import lombok.Getter;

/**
 * 系统恢复事件
 * 当系统启动时，对所有未恢复的活跃异常进行恢复处理时发布此事件
 */
@Getter
public class AlertRecoveredEvent extends AlertSystemEvent {

    private final int recoveredTaskCount;
    private final String recoveryMessage;

    public AlertRecoveredEvent(
            Object source,
            Long exceptionEventId,
            int recoveredTaskCount,
            String recoveryMessage) {
        super(source, exceptionEventId, "ALERT_RECOVERED");
        this.recoveredTaskCount = recoveredTaskCount;
        this.recoveryMessage = recoveryMessage;
    }
}
