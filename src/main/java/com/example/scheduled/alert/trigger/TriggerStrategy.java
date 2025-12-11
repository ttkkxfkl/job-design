package com.example.scheduled.alert.trigger;

import com.example.scheduled.alert.entity.ExceptionEvent;
import com.example.scheduled.alert.entity.TriggerCondition;

import java.time.LocalDateTime;

/**
 * 触发策略接口 - 用于计算下次触发时间和判断是否应该触发
 */
public interface TriggerStrategy {

    /**
     * 判断当前是否应该触发报警
     * @param condition 触发条件配置
     * @param event 异常事件
     * @param now 当前时间
     * @return true 表示应该触发
     */
    boolean shouldTrigger(TriggerCondition condition, ExceptionEvent event, LocalDateTime now);

    /**
     * 计算下次应该评估的时间
     * @param condition 触发条件配置
     * @param event 异常事件
     * @param now 当前时间
     * @return 下次评估时间，如果无需再评估返回 null
     */
    LocalDateTime calculateNextEvaluationTime(TriggerCondition condition, ExceptionEvent event, LocalDateTime now);
}
