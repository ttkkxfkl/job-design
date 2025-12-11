package com.example.scheduled.alert.service;

import com.example.scheduled.alert.entity.*;
import com.example.scheduled.alert.repository.AlertEventLogRepository;
import com.example.scheduled.alert.repository.AlertRuleRepository;
import com.example.scheduled.alert.repository.ExceptionEventRepository;
import com.example.scheduled.alert.trigger.TriggerStrategy;
import com.example.scheduled.alert.trigger.TriggerStrategyFactory;
import com.example.scheduled.entity.ScheduledTask;
import com.example.scheduled.service.TaskManagementService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 报警升级服务 - 负责报警的升级流程和评估任务的创建
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AlertEscalationService {

    private final AlertRuleRepository alertRuleRepository;
    private final ExceptionEventRepository exceptionEventRepository;
    private final AlertEventLogRepository alertEventLogRepository;
    private final TaskManagementService taskManagementService;
    private final TriggerStrategyFactory triggerStrategyFactory;

    /**
     * 当异常事件创建时调用 - 为最低等级创建初始评估任务
     */
    @Transactional
    public void scheduleInitialEvaluation(ExceptionEvent event) {
        log.info("为异常事件 [{}] 创建初始评估任务", event.getId());

        // 1. 获取该异常类型的所有启用的报警规则（按等级从低到高排序）
        List<AlertRule> allRules = alertRuleRepository
                .findEnabledRulesByExceptionType(event.getExceptionTypeId());

        if (allRules.isEmpty()) {
            log.warn("异常类型 [{}] 没有配置任何报警规则", event.getExceptionTypeId());
            return;
        }

        // 2. 只为最低等级规则创建评估任务
        AlertRule lowestRule = allRules.get(0);
        createEvaluationTask(event, lowestRule);
    }

    /**
     * 创建单个等级的评估任务
     */
    @Transactional
    public void createEvaluationTask(ExceptionEvent event, AlertRule rule) {
        try {
            // 获取触发条件并创建策略
            TriggerCondition condition = new TriggerCondition();
            condition.setId(rule.getTriggerConditionId());
            // 从数据库加载完整的条件信息
            // TODO: 需要注入 TriggerConditionRepository 来加载完整信息

            TriggerStrategy strategy = triggerStrategyFactory.createStrategy(condition);

            // 计算下次评估时间
            LocalDateTime nextEvaluationTime = strategy
                    .calculateNextEvaluationTime(condition, event, LocalDateTime.now());

            if (nextEvaluationTime != null) {
                // 构造任务数据
                Map<String, Object> taskData = new HashMap<>();
                taskData.put("exceptionEventId", event.getId());
                taskData.put("alertRuleId", rule.getId());
                taskData.put("evaluationType", "ALERT_EVALUATION");

                // 创建一个 ONCE 模式的定时任务提交给调度系统
                taskManagementService.createOnceTask(
                        "报警评估-异常[" + event.getId() + "]-规则[" + rule.getId() + "]",
                        ScheduledTask.TaskType.ALERT,
                        nextEvaluationTime,
                        taskData,
                        1,
                        rule.getPriority(),
                        30L
                );

                log.info("已创建评估任务: 异常[{}] 规则[{}] 等级[{}] 评估时间[{}]",
                        event.getId(), rule.getId(), rule.getLevel(), nextEvaluationTime);
            }
        } catch (Exception e) {
            log.error("创建评估任务失败: 异常[{}] 规则[{}]", event.getId(), rule.getId(), e);
        }
    }

    /**
     * 当当前等级触发报警后，为下一个更高等级创建评估任务
     */
    @Transactional
    public void scheduleNextLevelEvaluation(ExceptionEvent event, AlertRule triggeredRule) {
        log.info("为异常事件 [{}] 的下一等级创建评估任务，当前等级: {}", event.getId(), triggeredRule.getLevel());

        // 1. 获取所有规则，找到下一个更高等级的规则
        List<AlertRule> allRules = alertRuleRepository
                .findEnabledRulesByExceptionType(event.getExceptionTypeId());

        // 2. 过滤出更高等级的规则
        List<AlertRule> higherRules = allRules.stream()
                .filter(rule -> isHigherLevel(rule.getLevel(), triggeredRule.getLevel()))
                .toList();

        if (higherRules.isEmpty()) {
            log.info("异常事件 [{}] 已是最高等级 [{}]，无需继续升级", event.getId(), triggeredRule.getLevel());
            return;
        }

        // 3. 为下一个等级创建评估任务
        AlertRule nextRule = higherRules.get(0);
        createEvaluationTask(event, nextRule);
    }

    /**
     * 判断 level1 是否高于 level2
     */
    private boolean isHigherLevel(String level1, String level2) {
        int priority1 = getAlertLevelPriority(level1);
        int priority2 = getAlertLevelPriority(level2);
        return priority1 > priority2;
    }

    /**
     * 获取报警等级的数字优先级（BLUE=1, YELLOW=2, RED=3）
     */
    private int getAlertLevelPriority(String level) {
        return switch(level) {
            case "BLUE" -> 1;
            case "YELLOW" -> 2;
            case "RED" -> 3;
            default -> 0;
        };
    }

    /**
     * 记录报警事件日志
     */
    @Transactional
    public void logAlertEvent(ExceptionEvent event, AlertRule rule, String triggerReason) {
            AlertEventLog alertLog = AlertEventLog.builder()
                .exceptionEventId(event.getId())
                .alertRuleId(rule.getId())
                .triggeredAt(LocalDateTime.now())
                .alertLevel(rule.getLevel())
                .triggerReason(triggerReason)
                .actionStatus("SENT")
                .build();

            alertEventLogRepository.insert(alertLog);
            log.info("已记录报警事件日志: 异常[{}] 规则[{}] 等级[{}]",
                event.getId(), rule.getId(), rule.getLevel());
    }

    /**
     * 解决异常事件
     */
    @Transactional
    public void resolveEvent(Long exceptionEventId) {
        ExceptionEvent event = exceptionEventRepository.selectById(exceptionEventId);
        if (event != null) {
            event.setStatus("RESOLVED");
            event.setResolvedAt(LocalDateTime.now());
            exceptionEventRepository.updateById(event);
            log.info("异常事件 [{}] 已解决", exceptionEventId);
        }
    }
}
