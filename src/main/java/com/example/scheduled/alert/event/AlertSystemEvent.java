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

    /**
     * 异常事件ID（可选）
     * 如果事件是由某个已存在的异常事件触发的，则填充此字段
     * 大部分独立触发的事件此字段为 null
     */
    private final Long exceptionEventId;
    
    /**
     * 事件类型（如：FIRST_BOREHOLE_START、SHIFT_END 等）
     */
    private final String eventType;
    
    /**
     * 业务数据ID（必填）
     * 标识此事件归属于哪个业务数据，如班次ID、钻孔ID等
     */
    private final String businessId;
    
    /**
     * 业务类型（必填）
     * 标识业务数据的类型，如：SHIFT、BOREHOLE、OPERATION等
     */
    private final String businessType;

    public AlertSystemEvent(Object source, Long exceptionEventId, String eventType, 
                           String businessId, String businessType) {
        super(source);
        this.exceptionEventId = exceptionEventId;
        this.eventType = eventType;
        this.businessId = businessId;
        this.businessType = businessType;
    }
    
    /**
     * 兼容构造函数（不推荐使用，请使用包含 businessId 的构造函数）
     */
    @Deprecated
    public AlertSystemEvent(Object source, Long exceptionEventId, String eventType) {
        this(source, exceptionEventId, eventType, null, null);
    }
}
