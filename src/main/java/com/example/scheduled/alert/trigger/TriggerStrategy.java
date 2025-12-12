package com.example.scheduled.alert.trigger;

import com.example.scheduled.alert.entity.ExceptionEvent;
import com.example.scheduled.alert.entity.TriggerCondition;

import java.time.LocalDateTime;

/**
 * 触发策略接口 - 定义报警触发的评估策略
 * 
 * 该接口定义两个核心职责：
 * 1. shouldTrigger - 在评估时刻判断报警是否应该立即触发
 * 2. calculateNextEvaluationTime - 为调度系统安排下一次评估时间，实现精准调度
 * 
 * 支持多种触发策略：绝对时间、相对时间、混合条件等
 */
public interface TriggerStrategy {

    /**
     * 在指定时刻判断是否满足触发条件
     * 
     * 该方法用于在调度时刻评估报警是否应该立即触发。
     * 例如：判断当前时间是否到达了预定的触发时刻（绝对时间策略），
     * 或从某个事件开始是否已经经过了指定的时长（相对时间策略）
     * 
     * @param condition 触发条件配置（包含触发时间、时间窗口等）
     * @param event 异常事件（包含事件时间、检测上下文等）
     * @param now 当前评估时刻
     * @return true 表示应该立即触发报警，false 表示还不应该触发
     */
    boolean shouldTrigger(TriggerCondition condition, ExceptionEvent event, LocalDateTime now);

    /**
     * 计算下次应该评估触发条件的时间
     * 
     * 该方法为调度系统提供下一次任务执行的推荐时间，避免频繁无意义的检查。
     * 例如：如果现在是 14:00，绝对时间触发为 16:00，则返回 16:00 作为下次评估时间。
     * 这样调度系统可以精准安排任务，而不是每分钟都检查一次。
     * 
     * @param condition 触发条件配置
     * @param event 异常事件
     * @param now 当前时间
     * @return 下次应该评估的时间；如果无需再评估（如条件已永久不满足）返回 null
     */
    LocalDateTime calculateNextEvaluationTime(TriggerCondition condition, ExceptionEvent event, LocalDateTime now);
}
