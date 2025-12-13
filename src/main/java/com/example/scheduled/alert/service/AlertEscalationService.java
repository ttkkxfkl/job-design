package com.example.scheduled.alert.service;

import com.example.scheduled.alert.constant.AlertConstants;
import com.example.scheduled.alert.entity.*;
import com.example.scheduled.alert.repository.AlertEventLogRepository;
import com.example.scheduled.alert.repository.AlertRuleRepository;
import com.example.scheduled.alert.repository.ExceptionEventRepository;
import com.example.scheduled.alert.repository.TriggerConditionRepository;
import com.example.scheduled.alert.trigger.TriggerStrategy;
import com.example.scheduled.alert.trigger.TriggerStrategyFactory;
import com.example.scheduled.entity.ScheduledTask;
import com.example.scheduled.service.TaskManagementService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.example.scheduled.alert.constant.AlertConstants.ActionStatus.SENT;
import static com.example.scheduled.alert.constant.AlertConstants.Defaults.*;
import static com.example.scheduled.alert.constant.AlertConstants.PendingEscalationStatus.WAITING;

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
    private final TriggerConditionRepository triggerConditionRepository;

    /**
     * 当异常事件创建时调用 - 为最低等级创建初始评估任务
     */
    @Transactional
    public void scheduleInitialEvaluation(ExceptionEvent event) {
        log.info("为异常事件 [{}] 创建初始评估任务", event.getId());

        // 1. 获取该异常类型的所有启用的报警规则
        List<AlertRule> allRules = alertRuleRepository
                .findEnabledRulesByExceptionType(event.getExceptionTypeId());
        
        // 按等级优先级排序（从低到高）
        // BLUE/LEVEL_1 (priority=1) < YELLOW/LEVEL_2 (priority=2) < RED/LEVEL_3 (priority=3)
        allRules.sort(Comparator.comparingInt((AlertRule rule) -> AlertConstants.AlertLevels.getPriority(rule.getLevel()))
                                .thenComparingLong(AlertRule::getId));

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
            // 从数据库加载完整的触发条件信息
            TriggerCondition condition = triggerConditionRepository.selectById(rule.getTriggerConditionId());
            if (condition == null) {
                log.warn("触发条件不存在: triggerConditionId={}", rule.getTriggerConditionId());
                return;
            }

            TriggerStrategy strategy = triggerStrategyFactory.createStrategy(condition);

            // 计算下次评估时间
            LocalDateTime nextEvaluationTime = strategy
                    .calculateNextEvaluationTime(condition, event, LocalDateTime.now());

            // 如果策略返回 null，尝试闭环处理：
            // 1) 对相对事件：根据 detection_context 推导触发时间；缺少事件时写入 WAITING
            // 2) 其他情况：记录警告并跳过
            if (nextEvaluationTime == null) {
                LocalDateTime recoveredTime = recoverRelativeTriggerTime(condition, event, rule);
                if (recoveredTime == null) {
                    return;
                }
                nextEvaluationTime = recoveredTime;
            }

            // 构造任务数据
            Map<String, Object> taskData = new HashMap<>();
            taskData.put("exceptionEventId", event.getId());
            taskData.put("alertRuleId", rule.getId());
            taskData.put("evaluationType", "ALERT_EVALUATION");

            // 创建一个 ONCE 模式的定时任务提交给调度系统
            ScheduledTask task = taskManagementService.createOnceTask(
                    "报警评估-异常[" + event.getId() + "]-规则[" + rule.getId() + "]",
                    ScheduledTask.TaskType.ALERT,
                    nextEvaluationTime,
                    taskData,
                    DEFAULT_MAX_RETRY_COUNT,
                    rule.getPriority(),
                    DEFAULT_EXECUTION_TIMEOUT
            );

            String taskId = String.valueOf(task.getId());

            // 【关键】将任务ID持久化到 pending_escalations JSON 中，防止重启后丢失
            updatePendingEscalationsWithTaskId(event, rule.getLevel(), taskId, nextEvaluationTime);

            // 记录到内存Map（用于快速访问）
            recordPendingTask(event.getId(), taskId);

            log.info("已创建评估任务: 异常[{}] 规则[{}] 等级[{}] 评估时间[{}] 任务ID[{}]",
                    event.getId(), rule.getId(), rule.getLevel(), nextEvaluationTime, taskId);
        } catch (Exception e) {
            log.error("创建评估任务失败: 异常[{}] 规则[{}]", event.getId(), rule.getId(), e);
        }
    }

    /**
     * 当策略返回 null 时，对相对事件触发进行补偿：
     * - detection_context 缺少事件 → 写入 WAITING，等待外部事件再由 AlertDependencyManager 触发
     * - 事件已存在但时间未到 → 返回计算出的触发时间
     * - 事件已存在且已过触发点 → 立即调度（补偿执行）
     */
    private LocalDateTime recoverRelativeTriggerTime(TriggerCondition condition, ExceptionEvent event, AlertRule rule) {
        // 仅处理相对事件场景，其余直接告警并跳过
        if (condition.getRelativeEventType() == null || condition.getRelativeDurationMinutes() == null) {
            log.warn("无法计算下一次评估时间且非相对事件: exceptionEventId={}, ruleId={}, level={}",
                    event.getId(), rule.getId(), rule.getLevel());
            return null;
        }

        LocalDateTime eventTime = extractEventTime(event, condition.getRelativeEventType());
        if (eventTime == null) {
            // 将该等级写入 pending_escalations=WAITING，等事件到来后由依赖管理器调度
            writeWaitingPending(event, rule, condition);
            log.warn("相对事件时间缺失，已写入待机状态: exceptionEventId={}, level={}, relativeEventType={}",
                    event.getId(), rule.getLevel(), condition.getRelativeEventType());
            return null;
        }

        LocalDateTime triggerTime = eventTime.plusMinutes(condition.getRelativeDurationMinutes());
        LocalDateTime now = LocalDateTime.now();

        if (now.isBefore(triggerTime)) {
            return triggerTime; // 未来时间，正常调度
        }

        // 已过触发点，立即补偿执行
        return now;
    }

    /**
     * 从 detection_context 解析事件时间，支持字符串存储。
     */
    private LocalDateTime extractEventTime(ExceptionEvent event, String eventType) {
        if (event.getDetectionContext() == null) {
            return null;
        }
        Object timeObj = event.getDetectionContext().get(eventType + "_time");
        if (timeObj == null) {
            return null;
        }
        try {
            if (timeObj instanceof LocalDateTime ldt) {
                return ldt;
            }
            return LocalDateTime.parse(timeObj.toString());
        } catch (Exception parseEx) {
            log.warn("解析相对事件时间失败: eventType={}, value={}", eventType, timeObj, parseEx);
            return null;
        }
    }

    /**
     * 将当前等级写入 pending_escalations 为 WAITING，等待相对事件到达。
     */
    private void writeWaitingPending(ExceptionEvent event, AlertRule rule, TriggerCondition condition) {
        Map<String, Object> pending = event.getPendingEscalations();
        if (pending == null) {
            pending = new HashMap<>();
        }

        @SuppressWarnings("unchecked")
        Map<String, Object> levelStatus = (Map<String, Object>) pending.get(rule.getLevel());
        if (levelStatus == null) {
            levelStatus = new HashMap<>();
            pending.put(rule.getLevel(), levelStatus);
        }

        levelStatus.put("status", WAITING);
        levelStatus.put("createdAt", LocalDateTime.now().toString());

        // 写入依赖信息，供 AlertDependencyManager 后续检查
        List<Map<String, Object>> dependencies = new java.util.ArrayList<>();
        Map<String, Object> dep = new HashMap<>();
        dep.put("eventType", condition.getRelativeEventType());
        dep.put("delayMinutes", condition.getRelativeDurationMinutes());
        dep.put("required", true);
        dependencies.add(dep);
        levelStatus.put("dependencies", dependencies);
        levelStatus.put("logicalOperator", "AND");

        event.setPendingEscalations(pending);
        exceptionEventRepository.updateById(event);
    }
    
    /**
     * 更新 pending_escalations，添加任务ID（用于恢复和取消）
     */
    private void updatePendingEscalationsWithTaskId(ExceptionEvent event, String level, 
                                                     String taskId, LocalDateTime scheduledTime) {
        Map<String, Object> pendingEscalations = event.getPendingEscalations();
        if (pendingEscalations == null) {
            pendingEscalations = new HashMap<>();
        }

        @SuppressWarnings("unchecked")
        Map<String, Object> levelStatus = (Map<String, Object>) pendingEscalations.get(level);
        if (levelStatus == null) {
            levelStatus = new HashMap<>();
            pendingEscalations.put(level, levelStatus);
        }

        // 添加任务ID和调度时间
        levelStatus.put("taskId", taskId);
        levelStatus.put("scheduledTime", scheduledTime.toString());
        levelStatus.put("updatedAt", LocalDateTime.now().toString());

        event.setPendingEscalations(pendingEscalations);
        exceptionEventRepository.updateById(event);
        
        log.debug("已更新pending_escalations: exceptionEventId={}, level={}, taskId={}", 
                event.getId(), level, taskId);
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
        
        // 按等级优先级排序（从低到高）
        allRules.sort(Comparator.comparingInt((AlertRule rule) -> AlertConstants.AlertLevels.getPriority(rule.getLevel()))
                                .thenComparingLong(AlertRule::getId));

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
     * 获取报警等级的数字优先级
     */
    private int getAlertLevelPriority(String level) {
        return AlertConstants.AlertLevels.getPriority(level);
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
                .actionStatus(SENT)
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

    /**
     * 为特定等级创建或重新创建评估任务
     * 用于系统启动恢复时重新调度待机任务
     *
     * @param exceptionEventId 异常事件ID
     * @param levelName 等级名称（如 LEVEL_1、LEVEL_2）
     */
    @Transactional
    public void scheduleEscalationEvaluation(Long exceptionEventId, String levelName) {
        log.info("为异常事件 [{}] 的等级 [{}] 创建评估任务（恢复机制）", exceptionEventId, levelName);
        
        try {
            // 1. 查询异常事件
            ExceptionEvent event = exceptionEventRepository.selectById(exceptionEventId);
            if (event == null) {
                log.warn("异常事件不存在: exceptionEventId={}", exceptionEventId);
                return;
            }
            
            // 2. 查询该等级的规则
            List<AlertRule> rules = alertRuleRepository
                    .findEnabledRulesByExceptionType(event.getExceptionTypeId());
            
            // 按等级优先级排序（从低到高）
            rules.sort(Comparator.comparingInt((AlertRule rule) -> AlertConstants.AlertLevels.getPriority(rule.getLevel()))
                                 .thenComparingLong(AlertRule::getId));
            
            AlertRule targetRule = rules.stream()
                    .filter(rule -> rule.getLevel().equals(levelName))
                    .findFirst()
                    .orElse(null);
            
            if (targetRule == null) {
                log.warn("规则不存在: exceptionTypeId={}, level={}", event.getExceptionTypeId(), levelName);
                return;
            }
            
            // 3. 创建评估任务
            createEvaluationTask(event, targetRule);
            
        } catch (Exception e) {
            log.error("恢复等级评估任务失败: exceptionEventId={}, level={}", exceptionEventId, levelName, e);
        }
    }

    /**
     * Schedule an escalation evaluation at an explicit trigger time.
     * This is used when a dependency event occurred with a required delay (delayMinutes > 0).
     */
    public void scheduleEscalationEvaluation(Long exceptionEventId, String levelName, LocalDateTime triggerTime) {
        Map<String, Object> taskData = new HashMap<>();
        taskData.put("exceptionEventId", exceptionEventId);
        taskData.put("levelName", levelName);
        taskData.put("evaluationType", "ALERT_EVALUATION");

        ScheduledTask task = taskManagementService.createOnceTask(
            "报警评估-异常[" + exceptionEventId + "]-等级[" + levelName + "]",
            ScheduledTask.TaskType.ALERT,
            triggerTime,
            taskData,
            1,
            1,
            30L
        );
        recordPendingTask(exceptionEventId, String.valueOf(task.getId()));
        log.info("已为异常事件 [{}] 等级 [{}] 在 [{}] 创建延时评估任务: {}",
            exceptionEventId, levelName, triggerTime, task.getId());
    }

    /**
     * 维护待机任务映射关系（支持任务取消）
     * 当创建评估任务时，记录该任务的ID以便后续取消
     * 
     * 持久化策略（双层设计）：
     * 1. 【主要】ExceptionEvent.pending_escalations JSON 字段 - 持久化到数据库，重启不丢失
     * 2. 【辅助】内存 Map<Long, List<String>> - 快速访问，重启后清空但会自动恢复
     * 
     * 恢复机制：
     * - 系统重启时，AlertRecoveryService 从数据库读取 pending_escalations
     * - 重新调度任务时，会自动调用 recordPendingTask() 重新填充此 Map
     * - 因此重启后内存 Map 会自动恢复
     * 
     * 使用场景：
     * - AlertResolutionService.cancelAllPendingTasks() - 快速取消所有待机任务
     */
    private static final Map<Long, List<String>> PENDING_TASK_MAP = new java.util.concurrent.ConcurrentHashMap<>();

    /**
     * 记录待机任务ID（用于后续取消）
     */
    public void recordPendingTask(Long exceptionEventId, String taskId) {
        PENDING_TASK_MAP.computeIfAbsent(exceptionEventId, k -> new java.util.ArrayList<>()).add(taskId);
        log.debug("已记录待机任务: exceptionEventId={}, taskId={}", exceptionEventId, taskId);
    }

    /**
     * 获取所有待机任务ID
     */
    public List<String> getPendingTasks(Long exceptionEventId) {
        return PENDING_TASK_MAP.getOrDefault(exceptionEventId, new java.util.ArrayList<>());
    }

    /**
     * 清除待机任务记录
     */
    public void clearPendingTasks(Long exceptionEventId) {
        PENDING_TASK_MAP.remove(exceptionEventId);
        log.debug("已清除待机任务记录: exceptionEventId={}", exceptionEventId);
    }
}
