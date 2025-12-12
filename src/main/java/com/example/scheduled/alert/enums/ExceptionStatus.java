package com.example.scheduled.alert.enums;

/**
 * 异常事件状态枚举
 */
public enum ExceptionStatus {
    ACTIVE("活跃", "异常正在进行中，可能正在升级"),
    RESOLVING("解除中", "收到解除信号，但还有任务在执行中，防止中途崩溃"),
    RESOLVED("已解除", "异常完全解除，所有相关任务已取消或完成");

    private final String displayName;
    private final String description;

    ExceptionStatus(String displayName, String description) {
        this.displayName = displayName;
        this.description = description;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getDescription() {
        return description;
    }
}
