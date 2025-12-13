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
import static com.example.scheduled.alert.constant.AlertConstants.TriggerType.*;

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
            // 2) 混合条件：遍历子条件并取最早时间；无法恢复则写入 WAITING
            // 3) 绝对时间：不应返回 null，否则记警告
            if (nextEvaluationTime == null) {
                String conditionType = condition.getConditionType();
                if (RELATIVE.equals(conditionType)) {
                    LocalDateTime recoveredTime = recoverRelativeTriggerTime(condition, event, rule);
                    if (recoveredTime == null) {
                        // recoverRelativeTriggerTime 中已经写入 WAITING，直接返回
                        return;
                    }
                    nextEvaluationTime = recoveredTime;
                } else if (HYBRID.equals(conditionType)) {
                    LocalDateTime recoveredTime = recoverHybridTriggerTime(condition, event, rule);
                    if (recoveredTime == null) {
                        // recoverHybridTriggerTime 无法恢复，需要降级为 WAITING
                        writeWaitingPendingForHybrid(event, rule, condition);
                        return;
                    }
                    nextEvaluationTime = recoveredTime;
                } else {
                    log.warn("无法计算下一次评估时间且无补偿: exceptionEventId={}, ruleId={}, type={}",
                            event.getId(), rule.getId(), conditionType);
                    return;
                }
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
     * 对混合条件进行补偿：
     * - 遍历子条件，取可恢复的最早触发时间
     * - 子条件为相对事件且缺少上下文时，降级为 WAITING 写入依赖
     */
    private LocalDateTime recoverHybridTriggerTime(TriggerCondition condition, ExceptionEvent event, AlertRule rule) {
        if (condition.getCombinedConditionIds() == null) {
            log.warn("混合条件缺少子条件ID: exceptionEventId={}, ruleId={}, level={}",
                    event.getId(), rule.getId(), rule.getLevel());
            return null;
        }

        List<Long> conditionIds = parseConditionIds(condition.getCombinedConditionIds());
        if (conditionIds.isEmpty()) {
            log.warn("混合条件子条件列表为空: exceptionEventId={}, ruleId={}, level={}",
                    event.getId(), rule.getId(), rule.getLevel());
            return null;
        }

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime earliest = null;

        for (Long id : conditionIds) {
            TriggerCondition subCondition = triggerConditionRepository.selectById(id);
            if (subCondition == null) {
                continue;
            }

            TriggerStrategy subStrategy = triggerStrategyFactory.createStrategy(subCondition);
            LocalDateTime subNext = subStrategy.calculateNextEvaluationTime(subCondition, event, now);

            if (subNext != null) {
                if (earliest == null || subNext.isBefore(earliest)) {
                    earliest = subNext;
                }
                continue;
            }

            if (RELATIVE.equals(subCondition.getConditionType())) {
                LocalDateTime recovered = recoverRelativeTriggerTime(subCondition, event, rule);
                if (recovered != null && (earliest == null || recovered.isBefore(earliest))) {
                    earliest = recovered;
                }
            }
        }

        if (earliest == null) {
            log.warn("混合条件未能恢复评估时间: exceptionEventId={}, ruleId={}, level={}",
                    event.getId(), rule.getId(), rule.getLevel());
        }

        return earliest;
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
     * 
     * 设计说明（增量更新机制）：
     * - 如果该等级不存在，首次创建并设置 status=WAITING
     * - 如果已存在且是 WAITING，则增量追加 dependency，避免覆盖
     * - 支持混合条件中多个相对事件都缺失时的场景
     */
    private void writeWaitingPending(ExceptionEvent event, AlertRule rule, TriggerCondition condition) {
        Map<String, Object> pending = event.getPendingEscalations();
        if (pending == null) {
            pending = new HashMap<>();
        }

        @SuppressWarnings("unchecked")
        Map<String, Object> levelStatus = (Map<String, Object>) pending.get(rule.getLevel());
        boolean isNewLevel = (levelStatus == null);
        if (levelStatus == null) {
            levelStatus = new HashMap<>();
            pending.put(rule.getLevel(), levelStatus);
        }

        // 仅在首次写入时设置 status 和 createdAt
        if (isNewLevel) {
            levelStatus.put("status", WAITING);
            levelStatus.put("createdAt", LocalDateTime.now().toString());
        }

        // 构造当前条件的依赖项
        Map<String, Object> dep = new HashMap<>();
        dep.put("eventType", condition.getRelativeEventType());
        dep.put("delayMinutes", condition.getRelativeDurationMinutes());
        dep.put("required", true);

        // 增量追加依赖（而不是覆盖）
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> dependencies = (List<Map<String, Object>>) levelStatus.get("dependencies");
        if (dependencies == null) {
            dependencies = new java.util.ArrayList<>();
        } else {
            // 检查重复，避免同一事件类型的依赖被重复添加
            boolean exists = dependencies.stream()
                    .anyMatch(d -> condition.getRelativeEventType().equals(d.get("eventType")));
            if (exists) {
                // 依赖已存在，仍然需要更新时间戳表示曾被检查过
                levelStatus.put("updatedAt", LocalDateTime.now().toString());
                event.setPendingEscalations(pending);
                exceptionEventRepository.updateById(event);
                log.debug("依赖已存在，跳过重复添加但更新时间戳: level={}, eventType={}",
                        rule.getLevel(), condition.getRelativeEventType());
                return;
            }
        }
        dependencies.add(dep);
        levelStatus.put("dependencies", dependencies);

        // 仅在首次或尚未设置时，写入逻辑操作符
        if (!levelStatus.containsKey("logicalOperator")) {
            levelStatus.put("logicalOperator", "AND");
        }

        // 更新时间戳
        levelStatus.put("updatedAt", LocalDateTime.now().toString());

        event.setPendingEscalations(pending);
        exceptionEventRepository.updateById(event);
        
        log.info("已写入待机状态: level={}, eventType={}, isNewLevel={}, totalDependencies={}",
                rule.getLevel(), condition.getRelativeEventType(), isNewLevel, dependencies.size());
    }

    /**
     * 对混合条件无法恢复时，将整个混合条件作为待机状态（不展开子条件的依赖）
     * 仅在 recoverHybridTriggerTime 完全失败时调用
     */
    private void writeWaitingPendingForHybrid(ExceptionEvent event, AlertRule rule, TriggerCondition condition) {
        Map<String, Object> pending = event.getPendingEscalations();
        if (pending == null) {
            pending = new HashMap<>();
        }

        @SuppressWarnings("unchecked")
        Map<String, Object> levelStatus = (Map<String, Object>) pending.get(rule.getLevel());
        boolean isNewLevel = (levelStatus == null);
        if (levelStatus == null) {
            levelStatus = new HashMap<>();
            pending.put(rule.getLevel(), levelStatus);
        }

        // 仅在首次写入时设置 status
        if (isNewLevel) {
            levelStatus.put("status", WAITING);
            levelStatus.put("createdAt", LocalDateTime.now().toString());
        }

        // 记录混合条件的ID和逻辑操作符，供后续 AlertDependencyManager 识别
        levelStatus.put("hybridConditionId", condition.getId());
        levelStatus.put("hybridLogicalOperator", condition.getLogicalOperator());
        levelStatus.put("updatedAt", LocalDateTime.now().toString());

        event.setPendingEscalations(pending);
        exceptionEventRepository.updateById(event);
        
        log.warn("混合条件无法恢复，已写入待机状态: level={}, hybridConditionId={}, logicalOp={}",
                rule.getLevel(), condition.getId(), condition.getLogicalOperator());
    }

    private List<Long> parseConditionIds(String idString) {
        if (idString == null || idString.trim().isEmpty()) {
            return List.of();
        }

        String[] parts = idString.split(",");
        List<Long> ids = new java.util.ArrayList<>();
        for (String part : parts) {
            try {
                ids.add(Long.parseLong(part.trim()));
            } catch (NumberFormatException ex) {
                log.warn("无效的条件ID: {}", part);
            }
        }
        return ids;
    }
    
    /**
     * 更新 pending_escalations，添加任务ID（用于恢复和取消）
     * 
     * 注意：仅更新taskId/scheduledTime/updatedAt，不修改status和dependencies
     * 这样可以保持WAITING状态的原始依赖信息
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
            // 如果该等级还未记录，创建新的记录，这种情况应该很少发生（因为通常已由recoverXxx方法写入）
            levelStatus = new HashMap<>();
            levelStatus.put("status", "SCHEDULED");  // 标记为已调度，与WAITING区分
            levelStatus.put("createdAt", LocalDateTime.now().toString());
            pendingEscalations.put(level, levelStatus);
        }

        // 仅更新任务相关字段，不修改状态和依赖信息
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
    @Transactional
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
