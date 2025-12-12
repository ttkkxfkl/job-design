package com.example.scheduled.alert.trigger;

/**
 * 相对触发事件类型枚举
 * 定义所有支持的相对时间触发事件类型及其对应的上下文字段名
 * 
 * 扩展方式：
 * 1. 添加新的枚举常量，指定对应的上下文字段名
 * 2. 新增类型无需修改 RelativeEventTrigger 代码，只需在此添加枚举值
 */
public enum RelativeEventType {
    /**
     * 班次开始事件
     * 上下文字段：shift_start_time
     */
    SHIFT_START("shift_start_time"),

    /**
     * 上一次操作事件
     * 上下文字段：last_operation_time
     */
    LAST_OPERATION("last_operation_time"),

    /**
     * 异常检测事件
     * 上下文字段：exception_detected_time（从 ExceptionEvent.detectedAt 获取）
     */
    EXCEPTION_DETECTED("exception_detected_time"),

    // 后续可继续扩展...
    // 例如：
    // TASK_START("task_start_time"),
    // TASK_END("task_end_time"),
    // WORKER_ARRIVAL("worker_arrival_time"),
    ;

    /**
     * 该事件类型对应的检测上下文中的字段名
     * 
     * 对于 EXCEPTION_DETECTED，此字段不使用，
     * 而是直接从 ExceptionEvent.detectedAt 获取
     */
    private final String contextKey;

    RelativeEventType(String contextKey) {
        this.contextKey = contextKey;
    }

    public String getContextKey() {
        return contextKey;
    }

    /**
     * 根据字符串值获取对应的枚举常量
     * 
     * @param value 事件类型字符串（如 "SHIFT_START"）
     * @return 对应的枚举常量，如果不存在返回 null
     */
    public static RelativeEventType fromString(String value) {
        if (value == null) {
            return null;
        }
        try {
            return RelativeEventType.valueOf(value);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
