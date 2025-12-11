package com.example.scheduled.alert.trigger.strategy;

import com.example.scheduled.alert.entity.ExceptionEvent;
import com.example.scheduled.alert.entity.TriggerCondition;
import com.example.scheduled.alert.trigger.TriggerStrategy;
import com.example.scheduled.alert.trigger.TriggerStrategyFactory;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 混合触发策略 - 多个条件组合（AND/OR）
 * 示例：(条件1 AND 条件2) 或 (条件3 OR 条件4)
 */
@Slf4j
public class HybridTrigger implements TriggerStrategy {

    private final TriggerStrategyFactory strategyFactory;

    public HybridTrigger(TriggerStrategyFactory strategyFactory) {
        this.strategyFactory = strategyFactory;
    }

    @Override
    public boolean shouldTrigger(TriggerCondition condition, ExceptionEvent event, LocalDateTime now) {
        if (condition.getCombinedConditionIds() == null || condition.getLogicalOperator() == null) {
            return false;
        }

        List<Long> conditionIds = parseConditionIds(condition.getCombinedConditionIds());
        if (conditionIds.isEmpty()) {
            return false;
        }

        // 评估所有子条件
        List<Boolean> results = conditionIds.stream()
                .map(id -> {
                    TriggerCondition subCondition = strategyFactory.getTriggerConditionRepository().selectById(id);
                    if (subCondition == null) {
                        return false;
                    }
                    TriggerStrategy subStrategy = strategyFactory.createStrategy(subCondition);
                    return subStrategy.shouldTrigger(subCondition, event, now);
                })
                .collect(Collectors.toList());

        // 根据逻辑操作符进行判断
        if ("AND".equalsIgnoreCase(condition.getLogicalOperator())) {
            return results.stream().allMatch(b -> b);
        } else if ("OR".equalsIgnoreCase(condition.getLogicalOperator())) {
            return results.stream().anyMatch(b -> b);
        }

        return false;
    }

    @Override
    public LocalDateTime calculateNextEvaluationTime(TriggerCondition condition, ExceptionEvent event, LocalDateTime now) {
        if (condition.getCombinedConditionIds() == null) {
            return null;
        }

        List<Long> conditionIds = parseConditionIds(condition.getCombinedConditionIds());
        if (conditionIds.isEmpty()) {
            return null;
        }

        // 计算所有子条件的下次评估时间
        List<LocalDateTime> nextTimes = conditionIds.stream()
                .map(id -> {
                    TriggerCondition subCondition = strategyFactory.getTriggerConditionRepository().selectById(id);
                    if (subCondition == null) {
                        return null;
                    }
                    TriggerStrategy subStrategy = strategyFactory.createStrategy(subCondition);
                    return subStrategy.calculateNextEvaluationTime(subCondition, event, now);
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        if (nextTimes.isEmpty()) {
            return null;
        }

        // 混合条件中，取最早的下次评估时间
        // 这样可以尽快重新评估整个混合条件
        return nextTimes.stream()
                .min(LocalDateTime::compareTo)
                .orElse(null);
    }

    /**
     * 解析条件IDs字符串（逗号分隔）为Long列表
     */
    private List<Long> parseConditionIds(String idString) {
        if (idString == null || idString.trim().isEmpty()) {
            return List.of();
        }
        return Arrays.stream(idString.split(","))
                .map(String::trim)
                .map(id -> {
                    try {
                        return Long.parseLong(id);
                    } catch (NumberFormatException e) {
                        log.warn("无效的条件ID: {}", id);
                        return null;
                    }
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }
}
