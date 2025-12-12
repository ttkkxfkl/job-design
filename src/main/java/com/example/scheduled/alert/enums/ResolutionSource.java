package com.example.scheduled.alert.enums;

/**
 * 报警解除来源枚举
 */
public enum ResolutionSource {
    MANUAL_RESOLUTION("手动解除", "用户手动点击解除按钮"),
    AUTO_RECOVERY("自动恢复", "业务系统检测异常恢复，自动调用解除"),
    SYSTEM_CANCEL("系统取消", "系统管理员通过管理界面取消");

    private final String displayName;
    private final String description;

    ResolutionSource(String displayName, String description) {
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
