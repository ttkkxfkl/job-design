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
 * 
 * 原理：
 * - 评估多个子条件的触发状态
 * - 根据逻辑操作符（AND/OR）进行聚合判断
 * 
 * 示例场景：
 * 1. (班次开始后8小时) AND (生产设备温度>80℃) - 两个条件都满足才触发
 * 2. (时间=16:00) OR (班次开始后12小时) - 满足其中任一条件就触发
 * 
 * ============================================================================
 * 数据库存储格式示例
 * ============================================================================
 * 
 * 场景：如果班次已开始8小时 OR 当前时刻已到16:00，则触发报警
 * 
 * 表：trigger_condition
 * 
 * 主条件（混合条件）记录：
 * ┌────────┬──────────────┬──────────────┬───────────────┬──────────────┐
 * │ id     │ conditionType│ logicalOp    │ combinedIds   │ 说明         │
 * ├────────┼──────────────┼──────────────┼───────────────┼──────────────┤
 * │ 100    │ HYBRID       │ OR           │ "10,20"       │ 混合条件：10或20 │
 * └────────┴──────────────┴──────────────┴───────────────┴──────────────┘
 * 
 * 子条件记录1（相对时间条件）：
 * ┌────────┬──────────────┬─────────────────┬────────────────┐
 * │ id     │ conditionType│ relativeEventTyp│ durationMinutes│
 * ├────────┼──────────────┼─────────────────┼────────────────┤
 * │ 10     │ RELATIVE     │ SHIFT_START     │ 480 (8小时)    │
 * └────────┴──────────────┴─────────────────┴────────────────┘
 * 
 * 子条件记录2（绝对时间条件）：
 * ┌────────┬──────────────┬──────────────┐
 * │ id     │ conditionType│ absoluteTime │
 * ├────────┼──────────────┼──────────────┤
 * │ 20     │ ABSOLUTE     │ 16:00:00     │
 * └────────┴──────────────┴──────────────┘
 * 
 * 执行流程：
 * 1. 读取 id=100 的混合条件：combinedConditionIds="10,20", logicalOperator="OR"
 * 2. 解析得到子条件IDs：[10, 20]
 * 3. 评估子条件10（RELATIVE）：班次开始+8小时 是否已到达？
 * 4. 评估子条件20（ABSOLUTE）：当前时刻 是否 >= 16:00？
 * 5. 执行 OR 逻辑：只要有一个满足，就返回 true
 * 
 * ============================================================================
 * 更复杂的示例（嵌套混合条件）
 * ============================================================================
 * 
 * 场景：(条件A AND 条件B) OR (条件C AND 条件D)
 * 
 * 主条件（三层嵌套）：
 * id=1000, conditionType=HYBRID, logicalOperator=OR, combinedConditionIds="1001,1002"
 *   ├─ 子混合条件1001 (AND): combinedConditionIds="10,20", logicalOperator=AND
 *   │   ├─ 子条件10 (RELATIVE)
 *   │   └─ 子条件20 (ABSOLUTE)
 *   └─ 子混合条件1002 (AND): combinedConditionIds="30,40", logicalOperator=AND
 *       ├─ 子条件30 (RELATIVE)
 *       └─ 子条件40 (ABSOLUTE)
 * 
 * 注意：当前实现只支持两层混合（子条件不能再是HYBRID），如需支持深度嵌套
 * 可在 TriggerStrategyFactory.createStrategy() 中递归处理
 */
@SuppressWarnings("all")
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

        // 示例：combinedConditionIds = "10,20" -> 解析为 [10L, 20L]
        List<Long> conditionIds = parseConditionIds(condition.getCombinedConditionIds());
        if (conditionIds.isEmpty()) {
            return false;
        }

        // 评估所有子条件
        // 示例：对条件10和20分别评估
        // - 条件10评估结果：true（班次开始已超过8小时）
        // - 条件20评估结果：false（当前时刻还未到16:00）
        // 结果 results = [true, false]
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
        // - AND: 所有子条件都为 true 才返回 true
        //   例如：results=[true, false] -> AND -> false（不是所有都满足）
        // - OR: 只要有一个子条件为 true 就返回 true
        //   例如：results=[true, false] -> OR -> true（至少有一个满足）
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
        // 示例：
        // - 子条件10（RELATIVE，班次开始+8小时）：下次评估时间 = 2025-12-13 10:00:00
        // - 子条件20（ABSOLUTE，每天16:00）：下次评估时间 = 2025-12-12 16:00:00
        // nextTimes = [2025-12-13 10:00:00, 2025-12-12 16:00:00]
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
        // 示例中：min(2025-12-13 10:00:00, 2025-12-12 16:00:00) = 2025-12-12 16:00:00
        // 这样下次在16:00时就会重新评估混合条件，及时发现满足条件的情况
        return nextTimes.stream()
                .min(LocalDateTime::compareTo)
                .orElse(null);
    }

    /**
     * 解析条件IDs字符串（逗号分隔）为Long列表
     * 
     * 示例：
     * - 输入：idString = "10, 20, 30"
     * - 输出：[10L, 20L, 30L]
     * 
     * @param idString 逗号分隔的条件ID字符串（如 "1,2,3" 或 "1, 2, 3"）
     * @return 解析后的条件ID列表，会自动过滤掉无效的ID并记录警告
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
