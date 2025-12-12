package com.example.scheduled.alert.trigger;

import com.example.scheduled.alert.entity.TriggerCondition;
import com.example.scheduled.alert.repository.TriggerConditionRepository;
import com.example.scheduled.alert.trigger.strategy.AbsoluteTimeTrigger;
import com.example.scheduled.alert.trigger.strategy.HybridTrigger;
import com.example.scheduled.alert.trigger.strategy.RelativeEventTrigger;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import static com.example.scheduled.alert.constant.AlertConstants.TriggerType.*;



/**
 * 触发策略工厂 - 根据条件类型创建相应的策略实现
 */
@Slf4j
@Data
@Component
@RequiredArgsConstructor
public class TriggerStrategyFactory {

    private final TriggerConditionRepository triggerConditionRepository;

    /**
     * 根据触发条件创建对应的策略实例
     */
    public TriggerStrategy createStrategy(TriggerCondition condition) {
        if (condition == null) {
            throw new IllegalArgumentException("触发条件不能为空");
        }

        String conditionType = condition.getConditionType();

        return switch(conditionType) {
            case ABSOLUTE -> new AbsoluteTimeTrigger();
            case RELATIVE -> new RelativeEventTrigger();
            case HYBRID -> new HybridTrigger(this);
            default -> throw new IllegalArgumentException("未知的触发条件类型: " + conditionType);
        };
    }

}
