package com.example.scheduled.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 报警规则配置 DTO
 * 将 AlertRule 和 TriggerCondition 统一为一个配置对象，供前端使用
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AlertRuleConfigDTO {

    // ==================== 规则基础信息 ====================
    private Long id;                          // 规则ID
    private Long exceptionTypeId;             // 异常类型ID
    private String level;                     // 报警级别 (LEVEL_1/LEVEL_2/LEVEL_3)
    private String orgScope;                  // 适用机构 (如: 山西省)
    private Boolean enabled;                  // 是否启用
    private Integer priority;                 // 优先级

    // ==================== 触发条件 ====================
    @JsonProperty("triggerCondition")
    private TriggerConditionInfo triggerCondition;

    // ==================== 阈值配置 ====================
    private String operator;                  // 比较符 (>  >=  <  <=  =)
    private Long thresholdValue;              // 阈值数值
    private String timeUnit;                  // 时间单位 (MINUTE/HOUR/DAY)

    // ==================== 动作配置 ====================
    private String actionType;                // 动作类型 (LOG/EMAIL/SMS/WEBHOOK)
    private Map<String, Object> actionConfig; // 动作配置详情 (JSON)

    // ==================== 元数据 ====================
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    /**
     * 触发条件信息
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class TriggerConditionInfo {
        private Long id;                      // 触发条件ID
        private String conditionType;         // 条件类型 (ABSOLUTE/RELATIVE/HYBRID)
        
        // ABSOLUTE 类型字段
        private String absoluteTime;          // 固定时刻 (如: 16:00:00)
        
        // RELATIVE 类型字段
        private String relativeEventType;     // 相对事件类型 (如: FIRST_BOREHOLE_START)
        private Integer relativeDurationMinutes; // 相对延迟时长(分钟)
        
        // 时间窗口
        private String timeWindowStart;       // 时间窗口开始 (如: 08:00:00)
        private String timeWindowEnd;         // 时间窗口结束 (如: 22:00:00)
        
        // HYBRID 类型字段
        private String logicalOperator;       // 逻辑运算符 (AND/OR)
        private String combinedConditionIds;  // 组合条件IDs (如: 10,11)
        
        // 显示名称
        private String displayName;           // 用于前端展示的易读名称
    }
}
