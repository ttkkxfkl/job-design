package com.example.scheduled.alert.event;

import lombok.Data;
import lombok.EqualsAndHashCode;
import org.springframework.context.ApplicationEvent;

/**
 * 告警系统事件基类
 * 所有告警系统内的事件都应该继承自这个类
 */
@EqualsAndHashCode(callSuper = true)
@Data
public abstract class AlertSystemEvent extends ApplicationEvent {

    private final Long exceptionEventId;
    private final String eventType;

    public AlertSystemEvent(Object source, Long exceptionEventId, String eventType) {
        super(source);
        this.exceptionEventId = exceptionEventId;
        this.eventType = eventType;
    }
}
