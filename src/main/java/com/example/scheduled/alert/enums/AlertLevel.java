package com.example.scheduled.alert.enums;

/**
 * 全局告警等级枚举
 * 支持多级告警，前端在添加告警规则时选择对应等级
 * 可扩展为 LEVEL_1 到 LEVEL_N
 */
public enum AlertLevel {
    LEVEL_1("等级1", 1),
    LEVEL_2("等级2", 2),
    LEVEL_3("等级3", 3),
    LEVEL_4("等级4", 4),
    LEVEL_5("等级5", 5),
    LEVEL_6("等级6", 6),
    LEVEL_7("等级7", 7),
    LEVEL_8("等级8", 8),
    LEVEL_9("等级9", 9),
    LEVEL_10("等级10", 10);

    private final String description;
    private final int priority;

    AlertLevel(String description, int priority) {
        this.description = description;
        this.priority = priority;
    }

    public String getDescription() {
        return description;
    }

    public int getPriority() {
        return priority;
    }

    /**
     * 根据优先级比较两个等级
     * @param other 另一个等级
     * @return 如果当前等级 >= other，返回true
     */
    public boolean isHigherOrEqual(AlertLevel other) {
        return this.priority >= other.priority;
    }

    /**
     * 根据优先级比较两个等级
     * @param other 另一个等级
     * @return 如果当前等级 > other，返回true
     */
    public boolean isHigherThan(AlertLevel other) {
        return this.priority > other.priority;
    }
}
