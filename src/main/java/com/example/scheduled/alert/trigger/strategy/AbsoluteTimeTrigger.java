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

//    /**
//     * ============================================================================
//     * 以下为三种扩展方案的参考实现，目前未启用，后期可根据需求选择使用
//     * ============================================================================
//     */
//
//    /**
//     * 方案1：使用 Cron 表达式（最灵活）
//     * 支持复杂的时间模式，如：
//     * - "0 16 * * *" 每天 16:00
//     * - "0 16 * * 3" 每周三 16:00
//     * - "0 16 15 * *" 每月15号 16:00
//     * - "0 */2 * * *" 每2小时
//     *
//     * 需求：
//     * - TriggerCondition 添加 cronExpression 字段
//     * - 项目已引入 Quartz（已有）
//     **/
//    /*
//    public boolean shouldTrigger_CronBased(TriggerCondition condition, ExceptionEvent event, LocalDateTime now) {
//        if (condition.getCronExpression() == null) {
//            return false;
//        }
//
//        try {
//            org.quartz.CronExpression cronExpr = new org.quartz.CronExpression(condition.getCronExpression());
//
//            // Cron表达式通常精确到分钟，所以检查当前分钟是否匹配
//            java.util.Date nextFireTime = cronExpr.getNextValidTimeAfter(new java.util.Date());
//            if (nextFireTime == null) {
//                return false;
//            }
//
//            // 判断下一个触发时间是否在当前分钟内
//            LocalDateTime nextTrigger = LocalDateTime.ofInstant(
//                nextFireTime.toInstant(),
//                java.time.ZoneId.systemDefault()
//            );
//
//            // 如果下一个触发时间的小时和分钟与当前时间相同，则应该触发
//            return now.getHour() == nextTrigger.getHour() &&
//                   now.getMinute() == nextTrigger.getMinute();
//
//        } catch (org.quartz.ParseException e) {
//            log.error("Cron表达式解析失败: {}", condition.getCronExpression(), e);
//            return false;
//        }
//    }
//
//    public LocalDateTime calculateNextEvaluationTime_CronBased(TriggerCondition condition, ExceptionEvent event, LocalDateTime now) {
//        if (condition.getCronExpression() == null) {
//            return null;
//        }
//
//        try {
//            org.quartz.CronExpression cronExpr = new org.quartz.CronExpression(condition.getCronExpression());
//
//            // 从当前时间的下一秒开始计算
//            java.util.Date currentDate = java.util.Date.from(
//                now.atZone(java.time.ZoneId.systemDefault()).toInstant()
//            );
//            java.util.Date nextFireTime = cronExpr.getNextValidTimeAfter(currentDate);
//
//            if (nextFireTime == null) {
//                // 如果没有下一个触发时间，返回null表示无需再评估
//                return null;
//            }
//
//            return LocalDateTime.ofInstant(
//                nextFireTime.toInstant(),
//                java.time.ZoneId.systemDefault()
//            );
//
//        } catch (org.quartz.ParseException e) {
//            log.error("Cron表达式解析失败: {}", condition.getCronExpression(), e);
//            return null;
//        }
//    }
//    */
//
//    /**
//     * 方案2：扩展设计（支持周/月级别）
//     * 支持模式：
//     * - frequency = "DAILY", time = "16:00" 每天 16:00
//     * - frequency = "WEEKLY", dayOfWeek = "WEDNESDAY", time = "16:00" 每周三 16:00
//     * - frequency = "MONTHLY", dayOfMonth = 15, time = "16:00" 每月15号 16:00
//     *
//     * 需求：
//     * - TriggerCondition 添加 frequency、dayOfWeek、dayOfMonth 字段
//     * - 不依赖 Cron 表达式
//     */
//    /*
//    public boolean shouldTrigger_FrequencyBased(TriggerCondition condition, ExceptionEvent event, LocalDateTime now) {
//        if (condition.getAbsoluteTime() == null || condition.getFrequency() == null) {
//            return false;
//        }
//
//        LocalTime triggerTime = condition.getAbsoluteTime();
//        LocalTime currentTime = now.toLocalTime();
//
//        // 首先检查时间是否已到
//        boolean timeMatches = currentTime.isAfter(triggerTime) || currentTime.equals(triggerTime);
//        if (!timeMatches) {
//            return false;
//        }
//
//        // 检查频率是否匹配
//        String frequency = condition.getFrequency();
//        boolean frequencyMatches = false;
//
//        switch (frequency) {
//            case "DAILY" -> frequencyMatches = true;  // 每天都符合
//
//            case "WEEKLY" -> {
//                java.time.DayOfWeek targetDay = condition.getDayOfWeek();  // 需要添加这个字段
//                frequencyMatches = now.getDayOfWeek() == targetDay;
//            }
//
//            case "MONTHLY" -> {
//                int targetDayOfMonth = condition.getDayOfMonth();  // 需要添加这个字段
//                frequencyMatches = now.getDayOfMonth() == targetDayOfMonth;
//            }
//        }
//
//        if (!frequencyMatches) {
//            return false;
//        }
//
//        // 检查是否在时间窗口内（如果配置了）
//        if (condition.getTimeWindowStart() != null && condition.getTimeWindowEnd() != null) {
//            LocalTime windowStart = condition.getTimeWindowStart();
//            LocalTime windowEnd = condition.getTimeWindowEnd();
//            boolean inWindow = currentTime.isAfter(windowStart) && currentTime.isBefore(windowEnd);
//            return inWindow;
//        }
//
//        return true;
//    }
//
//    public LocalDateTime calculateNextEvaluationTime_FrequencyBased(TriggerCondition condition, ExceptionEvent event, LocalDateTime now) {
//        if (condition.getAbsoluteTime() == null || condition.getFrequency() == null) {
//            return null;
//        }
//
//        LocalTime triggerTime = condition.getAbsoluteTime();
//        String frequency = condition.getFrequency();
//
//        LocalDateTime nextTrigger = null;
//
//        switch (frequency) {
//            case "DAILY" -> {
//                // 计算明天的触发时间
//                LocalDateTime tomorrowTrigger = now.toLocalDate().plusDays(1).atTime(triggerTime);
//                nextTrigger = now.isBefore(tomorrowTrigger) ? tomorrowTrigger : tomorrowTrigger.plusDays(1);
//            }
//
//            case "WEEKLY" -> {
//                java.time.DayOfWeek targetDay = condition.getDayOfWeek();
//                // 计算下一个目标星期几的触发时间
//                int daysUntilTarget = (targetDay.getValue() - now.getDayOfWeek().getValue() + 7) % 7;
//                if (daysUntilTarget == 0 && now.toLocalTime().isAfter(triggerTime)) {
//                    daysUntilTarget = 7;  // 如果今天已经过了触发时间，则下周
//                }
//                nextTrigger = now.toLocalDate().plusDays(daysUntilTarget).atTime(triggerTime);
//            }
//
//            case "MONTHLY" -> {
//                int targetDayOfMonth = condition.getDayOfMonth();
//                LocalDateTime nextMonthTrigger = now.withDayOfMonth(targetDayOfMonth).withHour(triggerTime.getHour())
//                    .withMinute(triggerTime.getMinute()).withSecond(triggerTime.getSecond());
//
//                if (now.isAfter(nextMonthTrigger)) {
//                    // 如果当月已经过了，计算下个月
//                    nextMonthTrigger = nextMonthTrigger.plusMonths(1);
//                }
//                nextTrigger = nextMonthTrigger;
//            }
//        }
//
//        return nextTrigger;
//    }
//    */
//
//    /**
//     * 方案3：保持简单（仅支持日级重复，文档化限制）
//     * 这是当前的实现方案，优点是简单易维护
//     *
//     * 限制说明：
//     * - 只支持日级重复（每天同一时刻）
//     * - 不支持周/月级别的时间模式
//     * - 不直接处理时区问题（依赖系统时区）
//     * - 适用于大多数简单的报警场景
//     *
//     * 如果需要更复杂的时间规则，请切换到方案1（Cron）或方案2（频率）
//     *
//     * 方案3 的实现就是当前的 shouldTrigger 和 calculateNextEvaluationTime
//     * 无需额外代码，上面的实现已经是方案3
//     */
}
