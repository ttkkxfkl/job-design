package com.example.scheduled.alert.event;

import com.example.scheduled.alert.constant.AlertConstants;
import lombok.Getter;

/**
 * 系统恢复事件
 * 当系统启动时，对所有未恢复的活跃异常进行恢复处理时发布此事件
 */
@Getter
public class AlertRecoveredEvent extends AlertSystemEvent {

    private final int recoveredTaskCount;
    private final String recoveryMessage;

    /**
     * 新构造函数（推荐） - 包含 businessId
     */
    public AlertRecoveredEvent(
            Object source,
            Long exceptionEventId,
            String businessId,
            String businessType,
            int recoveredTaskCount,
            String recoveryMessage) {
        super(source, exceptionEventId, AlertConstants.AlertEventType.ALERT_RECOVERED, businessId, businessType);
        this.recoveredTaskCount = recoveredTaskCount;
        this.recoveryMessage = recoveryMessage;
    }
    
    /**
     * 兼容构造函数（不推荐）
     */
    @Deprecated
    public AlertRecoveredEvent(
            Object source,
            Long exceptionEventId,
            int recoveredTaskCount,
            String recoveryMessage) {
        super(source, exceptionEventId, AlertConstants.AlertEventType.ALERT_RECOVERED);
        this.recoveredTaskCount = recoveredTaskCount;
        this.recoveryMessage = recoveryMessage;
    }
}
