package com.example.scheduled.alert.trigger.strategy;

import com.example.scheduled.alert.entity.ExceptionEvent;
import com.example.scheduled.alert.entity.TriggerCondition;
import com.example.scheduled.alert.trigger.TriggerStrategy;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * 绝对时间触发策略 - 在固定的时刻触发
 * 示例：每天 16:00 触发报警
 */
@Slf4j
public class AbsoluteTimeTrigger implements TriggerStrategy {

    @Override
    public boolean shouldTrigger(TriggerCondition condition, ExceptionEvent event, LocalDateTime now) {
        if (condition.getAbsoluteTime() == null) {
            return false;
        }

        LocalTime triggerTime = condition.getAbsoluteTime();
        LocalTime currentTime = now.toLocalTime();

        // 简单比较：已经过了设定时刻
        boolean triggered = currentTime.isAfter(triggerTime) || currentTime.equals(triggerTime);

        // 检查是否在时间窗口内（如果配置了）
        if (condition.getTimeWindowStart() != null && condition.getTimeWindowEnd() != null) {
            LocalTime windowStart = condition.getTimeWindowStart();
            LocalTime windowEnd = condition.getTimeWindowEnd();
            boolean inWindow = currentTime.isAfter(windowStart) && currentTime.isBefore(windowEnd);
            triggered = triggered && inWindow;
        }

        return triggered;
    }

    @Override
    public LocalDateTime calculateNextEvaluationTime(TriggerCondition condition, ExceptionEvent event, LocalDateTime now) {
        if (condition.getAbsoluteTime() == null) {
            return null;
        }

        LocalTime triggerTime = condition.getAbsoluteTime();
        LocalDateTime todayTriggerTime = now.toLocalDate().atTime(triggerTime);

        // 如果还没到今天的触发时间，返回今天的触发时间
        if (now.isBefore(todayTriggerTime)) {
            return todayTriggerTime;
        }

        // 如果已经过了，返回明天的触发时间
        return todayTriggerTime.plusDays(1);
    }
}
