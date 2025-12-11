package com.example.scheduled.alert.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.example.scheduled.config.JsonTypeHandler;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 报警规则表 - 定义异常的各等级报警规则
 */
@TableName(value = "alert_rule", autoResultMap = true)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AlertRule {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long exceptionTypeId;

    /**
     * 报警等级：BLUE、YELLOW、RED
     */
    private String level;

    private Long triggerConditionId;

    /**
     * 动作类型：LOG、EMAIL、SMS、WEBHOOK
     */
    private String actionType;

    /**
     * 动作配置（JSON格式）
     */
    @TableField(value = "action_config", typeHandler = JsonTypeHandler.class)
    private Map<String, Object> actionConfig;

    /**
     * 优先级（0-10，数字越大优先级越高）
     */
    private Integer priority;

    private Boolean enabled;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    /**
     * 报警等级枚举
     */
    public enum AlertLevel {
        BLUE,    // 蓝色：轻度预警
        YELLOW,  // 黄色：中度预警
        RED      // 红色：严重警告
    }

    /**
     * 动作类型枚举
     */
    public enum ActionType {
        LOG,      // 日志
        EMAIL,    // 邮件
        SMS,      // 短信
        WEBHOOK   // 网络钩子
    }

    /**
     * 获取等级优先级（用于排序）
     */
    public static int getLevelPriority(String level) {
        return switch(level) {
            case "BLUE" -> 1;
            case "YELLOW" -> 2;
            case "RED" -> 3;
            default -> 0;
        };
    }
}
