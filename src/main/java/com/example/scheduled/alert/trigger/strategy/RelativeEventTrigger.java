package com.example.scheduled.alert.trigger.strategy;

import com.example.scheduled.alert.entity.ExceptionEvent;
import com.example.scheduled.alert.entity.TriggerCondition;
import com.example.scheduled.alert.trigger.TriggerStrategy;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Map;

/**
 * 相对时间触发策略 - 从某个事件开始计时，经过指定时间后触发
 * 示例：班次开始后 8 小时触发报警
 */
@Slf4j
public class RelativeEventTrigger implements TriggerStrategy {

    @Override
    public boolean shouldTrigger(TriggerCondition condition, ExceptionEvent event, LocalDateTime now) {
        if (condition.getRelativeEventType() == null || condition.getRelativeDurationMinutes() == null) {
            return false;
        }

        LocalDateTime eventTime = getEventTime(event, condition.getRelativeEventType());
        if (eventTime == null) {
            log.warn("无法获取事件时间: {}", condition.getRelativeEventType());
            return false;
        }

        LocalDateTime triggerTime = eventTime.plusMinutes(condition.getRelativeDurationMinutes());

        // 简单比较：已经过了触发时间
        boolean triggered = now.isAfter(triggerTime) || now.equals(triggerTime);

        // 检查是否在时间窗口内（如果配置了）
        if (condition.getTimeWindowStart() != null && condition.getTimeWindowEnd() != null) {
            LocalTime windowStart = condition.getTimeWindowStart();
            LocalTime windowEnd = condition.getTimeWindowEnd();
            LocalTime currentTime = now.toLocalTime();
            boolean inWindow = currentTime.isAfter(windowStart) && currentTime.isBefore(windowEnd);
            triggered = triggered && inWindow;
        }

        return triggered;
    }

    @Override
    public LocalDateTime calculateNextEvaluationTime(TriggerCondition condition, ExceptionEvent event, LocalDateTime now) {
        if (condition.getRelativeEventType() == null || condition.getRelativeDurationMinutes() == null) {
            return null;
        }

        LocalDateTime eventTime = getEventTime(event, condition.getRelativeEventType());
        if (eventTime == null) {
            return null;
        }

        LocalDateTime triggerTime = eventTime.plusMinutes(condition.getRelativeDurationMinutes());

        // 如果触发时间还未到达，返回触发时间
        if (now.isBefore(triggerTime)) {
            return triggerTime;
        }

        // 如果已经触发过了，相对事件触发通常只触发一次，不需要再评估
        return null;
    }

    /**
     * 从异常事件的上下文中获取相对事件的时间
     */
    private LocalDateTime getEventTime(ExceptionEvent event, String eventType) {
        if (event.getDetectionContext() == null) {
            return null;
        }

        Map<String, Object> context = event.getDetectionContext();

        return switch(eventType) {
            case "SHIFT_START" -> (LocalDateTime) context.get("shift_start_time");
            case "LAST_OPERATION" -> (LocalDateTime) context.get("last_operation_time");
            case "EXCEPTION_DETECTED" -> event.getDetectedAt();
            default -> null;
        };
    }
}
