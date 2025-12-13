package com.example.scheduled.alert.executor;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.scheduled.alert.action.AlertActionExecutor;
import com.example.scheduled.alert.entity.AlertEventLog;
import com.example.scheduled.alert.entity.AlertRule;
import com.example.scheduled.alert.entity.ExceptionEvent;
import com.example.scheduled.alert.entity.ExceptionType;
import com.example.scheduled.alert.entity.TriggerCondition;
import com.example.scheduled.alert.detection.ExceptionDetectionStrategy;
import com.example.scheduled.alert.repository.AlertEventLogRepository;
import com.example.scheduled.alert.repository.AlertRuleRepository;
import com.example.scheduled.alert.repository.ExceptionEventRepository;
import com.example.scheduled.alert.repository.ExceptionTypeRepository;
import com.example.scheduled.alert.repository.TriggerConditionRepository;
import com.example.scheduled.alert.service.AlertEscalationService;
import com.example.scheduled.alert.trigger.TriggerStrategy;
import com.example.scheduled.alert.trigger.TriggerStrategyFactory;
import com.example.scheduled.entity.ScheduledTask;
import com.example.scheduled.executor.TaskExecutor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import static com.example.scheduled.alert.constant.AlertConstants.AlertEventType.ALERT_TRIGGERED;
import static com.example.scheduled.alert.constant.AlertConstants.ExceptionEventStatus.ACTIVE;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 报警评估执行器 - 实现 TaskExecutor 接口
 * 由任务调度系统在指定时间调用，评估报警条件是否满足
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AlertExecutor implements TaskExecutor {

    private final AlertRuleRepository alertRuleRepository;
    private final ExceptionEventRepository exceptionEventRepository;
    private final TriggerConditionRepository triggerConditionRepository;
    private final ExceptionTypeRepository exceptionTypeRepository;
    private final AlertEventLogRepository alertEventLogRepository;
    private final TriggerStrategyFactory triggerStrategyFactory;
    private final AlertEscalationService alertEscalationService;
    private final List<AlertActionExecutor> actionExecutors;
    private final List<ExceptionDetectionStrategy> detectionStrategies;

    @Override
    public boolean support(ScheduledTask.TaskType taskType) {
        return taskType == ScheduledTask.TaskType.ALERT;
    }

    @Override
    public void execute(ScheduledTask task) throws Exception {
        Map<String, Object> taskData = task.getTaskData();
        Long exceptionEventId = ((Number) taskData.get("exceptionEventId")).longValue();
        
        // 支持两种模式：
        // 模式1：从 alertRuleId 直接查询规则（初始调度）
        // 模式2：从 levelName 查询规则（依赖管理器调度或恢复调度）
        Long alertRuleId = null;
        final String[] levelNameHolder = {null};
        
        if (taskData.containsKey("alertRuleId")) {
            alertRuleId = ((Number) taskData.get("alertRuleId")).longValue();
        } else if (taskData.containsKey("levelName")) {
            levelNameHolder[0] = (String) taskData.get("levelName");
        } else {
            log.error("任务数据缺少alertRuleId或levelName: exceptionEventId={}", exceptionEventId);
            return;
        }

        log.info("开始执行报警评估任务: 异常[{}] ruleId={} levelName={}", exceptionEventId, alertRuleId, levelNameHolder[0]);

        try {
            // 1. 获取异常事件和报警规则
            ExceptionEvent event = exceptionEventRepository.selectById(exceptionEventId);
            if (event == null) {
                log.warn("异常事件 [{}] 不存在", exceptionEventId);
                return;
            }

            AlertRule rule;
            if (alertRuleId != null) {
                rule = alertRuleRepository.selectById(alertRuleId);
                if (rule == null) {
                    log.warn("报警规则 [{}] 不存在", alertRuleId);
                    return;
                }
            } else {
                // 从 levelName 查询规则
                final String finalLevelName = levelNameHolder[0];
                List<AlertRule> rules = alertRuleRepository.findEnabledRulesByExceptionType(event.getExceptionTypeId());
                rule = rules.stream()
                        .filter(r -> r.getLevel().equals(finalLevelName))
                        .findFirst()
                        .orElse(null);
                if (rule == null) {
                    log.warn("异常类型 [{}] 的等级 [{}] 规则不存在", event.getExceptionTypeId(), finalLevelName);
                    return;
                }
            }

            // 【关键】幂等性检查：如果事件已解除，跳过执行
            if (!ACTIVE.equals(event.getStatus())) {
                log.info("异常事件已解除（status={}），跳过评估: exceptionEventId={}",
                        event.getStatus(), exceptionEventId);
                return;
            }

            // 【关键】幂等性检查：检查是否已执行过此等级（防止重复执行）
            if (isLevelAlreadyTriggered(event, rule.getLevel())) {
                log.info("等级 [{}] 已触发过，跳过重复执行: exceptionEventId={}",
                        rule.getLevel(), exceptionEventId);
                return;
            }

            // 2. 校验异常当前是否仍满足检测逻辑（不同异常类型有不同的业务判断）
            ExceptionType exceptionType = exceptionTypeRepository.selectById(event.getExceptionTypeId());
            if (exceptionType == null) {
                log.warn("异常类型 [{}] 不存在", event.getExceptionTypeId());
                return;
            }

            if (!isExceptionStillActive(exceptionType, event)) {
                log.info("异常事件 [{}] 当前未满足业务检测逻辑，跳过本次告警评估", exceptionEventId);
                return;
            }

            // 3. 获取触发条件并评估
            TriggerCondition condition = triggerConditionRepository.selectById(rule.getTriggerConditionId());
            if (condition == null) {
                log.warn("触发条件 [{}] 不存在", rule.getTriggerConditionId());
                return;
            }

            TriggerStrategy strategy = triggerStrategyFactory.createStrategy(condition);
            boolean shouldTrigger = strategy.shouldTrigger(condition, event, LocalDateTime.now());

            if (shouldTrigger) {
                // ✅ 当前等级应该触发报警了！
                handleAlertTriggered(event, rule, condition, strategy);
            } else {
                // ❌ 条件还不满足，继续等待
                handleAlertNotTriggered(event, rule, condition, strategy);
            }

        } catch (Exception e) {
            log.error("报警评估任务执行失败: 异常[{}] 规则[{}]", exceptionEventId, alertRuleId, e);
            throw e;
        }
    }

    /**
     * 处理报警被触发的情况
     */
    private void handleAlertTriggered(
            ExceptionEvent event,
            AlertRule rule,
            TriggerCondition condition,
            TriggerStrategy strategy) {

        log.info("报警条件满足: 异常[{}] 规则[{}] 等级[{}]", event.getId(), rule.getId(), rule.getLevel());

        // 1. 记录报警事件日志
        alertEscalationService.logAlertEvent(event, rule, "触发条件已满足");

        // 2. 执行报警动作（发邮件、短信等）
        try {
            executeAlertAction(event, rule);
        } catch (Exception e) {
            log.error("报警动作执行失败", e);
        }

        // 3. 更新异常事件的当前等级
        event.setCurrentAlertLevel(rule.getLevel());
        event.setLastEscalatedAt(LocalDateTime.now());
        exceptionEventRepository.updateById(event);

        // 4. 检查是否有更高等级的规则，如果有则为下一等级创建评估任务
        alertEscalationService.scheduleNextLevelEvaluation(event, rule);
    }

    /**
     * 处理报警条件不满足的情况（异常场景）
     * 
     * 正常情况下不应该走到这里，因为调度系统已按计算的时间执行。
     * 可能的异常原因：
     * 1. 调度偏差（提前执行） → 重新调度到正确时间
     * 2. 时间窗口限制 → 调度到窗口内的下一个时间点
     * 3. 数据被篡改/策略异常 → 记录错误
     */
    private void handleAlertNotTriggered(
            ExceptionEvent event,
            AlertRule rule,
            TriggerCondition condition,
            TriggerStrategy strategy) {

        log.warn("报警条件未满足（异常情况）: 异常[{}] 规则[{}] 等级[{}]", 
                event.getId(), rule.getId(), rule.getLevel());

        // 计算下次评估时间
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime nextEvaluationTime = strategy
                .calculateNextEvaluationTime(condition, event, now);

        if (nextEvaluationTime != null && nextEvaluationTime.isAfter(now)) {
            // 情况1: 时间未到（调度偏差或时间窗口限制），创建延迟任务
            log.warn("触发时间未到，创建延迟评估任务: 异常[{}] 规则[{}] 下次时间[{}]",
                    event.getId(), rule.getId(), nextEvaluationTime);
            
            // 使用三参数版本直接指定时间，避免触发策略重新计算
            alertEscalationService.scheduleEscalationEvaluation(event.getId(), rule.getLevel(), nextEvaluationTime);
            
        } else {
            // 情况2: 其他异常（数据问题/策略bug）
            log.error("报警评估异常：无法计算下次时间或策略判断失败: exceptionEventId={}, ruleId={}, level={}, " +
                    "condition={}, detectionContext={}", 
                    event.getId(), rule.getId(), rule.getLevel(), 
                    condition, event.getDetectionContext());
            
            // 记录异常日志到数据库，便于排查
            alertEscalationService.logAlertEvent(event, rule, 
                    "报警评估异常：条件判断失败，nextEvaluationTime=" + nextEvaluationTime);
        }
    }

    /**
     * 执行报警动作
     */
    private void executeAlertAction(ExceptionEvent event, AlertRule rule) throws Exception {
        String actionType = rule.getActionType();
        Map<String, Object> actionConfig = rule.getActionConfig();

        // 查找对应的动作执行器
        for (AlertActionExecutor executor : actionExecutors) {
            if (executor.supports(actionType)) {
                executor.execute(actionConfig, event, rule);
                log.info("已执行报警动作: 类型[{}]", actionType);
                return;
            }
        }

        log.warn("未找到对应的报警动作执行器: {}", actionType);
    }

    @Override
    public String getName() {
        return "AlertExecutor";
    }

    /**
     * 针对不同异常类型的业务检测逻辑，判断异常是否仍然成立
     */
    private boolean isExceptionStillActive(ExceptionType exceptionType, ExceptionEvent event) {
        String logicType = exceptionType.getDetectionLogicType();
        Map<String, Object> config = exceptionType.getDetectionConfig();
        Map<String, Object> context = event.getDetectionContext();

        if (logicType == null || logicType.isBlank()) {
            // 未配置检测逻辑，默认认为异常仍然成立
            return true;
        }

        ExceptionDetectionStrategy strategy = detectionStrategies.stream()
                .filter(s -> logicType.equalsIgnoreCase(s.getStrategyName()))
                .findFirst()
                .orElse(null);

        if (strategy == null) {
            log.warn("未找到检测策略 [{}]，默认跳过本次评估", logicType);
            return false;
        }

        boolean detected = strategy.detect(config, context);
        if (!detected) {
            log.info("检测策略 [{}] 判定异常未成立，config={} context={}", logicType, config, context);
        }
        return detected;
    }

    /**
     * 检查指定等级是否已触发过（幂等性保护）
     * 防止Quartz持久化冲突导致重复执行
     */
    private boolean isLevelAlreadyTriggered(ExceptionEvent event, String level) {
        // 检查 alert_event_log 中是否有该等级的 ALERT_TRIGGERED 记录
        LambdaQueryWrapper<AlertEventLog> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(AlertEventLog::getExceptionEventId, event.getId())
                .eq(AlertEventLog::getAlertLevel, level)
                .eq(AlertEventLog::getEventType, ALERT_TRIGGERED);

        long count = alertEventLogRepository.selectCount(wrapper);

        if (count > 0) {
            log.debug("等级 [{}] 已有 {} 条触发记录", level, count);
        }

        return count > 0;
    }
}
