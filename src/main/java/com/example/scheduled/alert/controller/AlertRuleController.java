package com.example.scheduled.alert.controller;

import com.example.scheduled.alert.constant.AlertConstants;
import com.example.scheduled.alert.entity.*;
import com.example.scheduled.alert.repository.*;
import com.example.scheduled.alert.service.AlertEscalationService;
import com.example.scheduled.dto.ApiResponse;
import com.example.scheduled.dto.AlertRuleConfigDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 报警规则管理 API 控制器
 */
@Slf4j
@RestController
@RequestMapping("/api/alert")
@RequiredArgsConstructor
public class AlertRuleController {

    private final ExceptionTypeRepository exceptionTypeRepository;
    private final TriggerConditionRepository triggerConditionRepository;
    private final AlertRuleRepository alertRuleRepository;
    private final ExceptionEventRepository exceptionEventRepository;
    private final AlertEventLogRepository alertEventLogRepository;
    private final AlertEscalationService alertEscalationService;

    // ==================== 异常类型管理 ====================

    /**
     * 创建异常类型
     * 
     * 【示例数据 - RECORD_CHECK 类型】
     * POST /api/alert/exception-type
     * {
     *   "name": "入井记录不足",
     *   "description": "班次内入井记录数不足指定数量",
     *   "detectionLogicType": "RECORD_CHECK",
     *   "detectionConfig": {
     *     "tableName": "work_log",
     *     "dateField": "entry_time",
     *     "duration": "3h",
     *     "minCount": 1,
     *     "conditionField": "entry_type",
     *     "conditionValue": "入井",
     *     "businessIdField": "shift_id"
     *   },
     *   "enabled": true
     * }
     * 
     * 【示例数据 - TIME_CHECK 类型】
     * {
     *   "name": "班次时长超限",
     *   "description": "班次工作时间超过8小时",
     *   "detectionLogicType": "TIME_CHECK",
     *   "detectionConfig": {
     *     "tableName": "shift_operation",
     *     "startTimeField": "operation_start_time",
     *     "endTimeField": "operation_end_time",
     *     "maxDuration": "8h",
     *     "alertWhen": "EXCEED"
     *   },
     *   "enabled": true
     * }
     * 
     * 【示例数据 - CUSTOM 类型】
     * {
     *   "name": "自定义异常",
     *   "description": "自定义 SQL 检测异常",
     *   "detectionLogicType": "CUSTOM",
     *   "detectionConfig": {
     *     "scriptType": "SQL",
     *     "query": "SELECT COUNT(*) as cnt FROM work_log WHERE entry_time > DATE_SUB(NOW(), INTERVAL 3 HOUR) AND entry_type='入井'",
     *     "threshold": 1,
     *     "operator": "<"
     *   },
     *   "enabled": true
     * }
     */
    @PostMapping("/exception-type")
    public ApiResponse<?> createExceptionType(@RequestBody ExceptionType exceptionType) {
        try {
            exceptionType.setCreatedAt(LocalDateTime.now());
            exceptionType.setUpdatedAt(LocalDateTime.now());
            exceptionType.setEnabled(true);
            exceptionTypeRepository.insert(exceptionType);
            return ApiResponse.success("异常类型创建成功", exceptionType);
        } catch (Exception e) {
            log.error("创建异常类型失败", e);
            return ApiResponse.error("创建异常类型失败: " + e.getMessage());
        }
    }

    /**
     * 获取所有异常类型
     */
    @GetMapping("/exception-types")
    public ApiResponse<?> getExceptionTypes() {
        try {
            List<ExceptionType> types = exceptionTypeRepository.selectList(null);
            return ApiResponse.success("查询成功", types);
        } catch (Exception e) {
            log.error("查询异常类型失败", e);
            return ApiResponse.error("查询异常类型失败: " + e.getMessage());
        }
    }

    // ==================== 触发条件管理 ====================

    /**
     * 创建触发条件
     * 
     * 【示例数据 - ABSOLUTE 类型（固定时刻触发）】
     * POST /api/alert/trigger-condition
     * {
     *   "conditionType": "ABSOLUTE",
     *   "absoluteTime": "16:00:00",
     *   "timeWindowStart": null,
     *   "timeWindowEnd": null
     * }
     * 说明：每天下午16:00 触发报警评估
     * 
     * 【示例数据 - RELATIVE 类型（相对事件触发）】
     * {
     *   "conditionType": "RELATIVE",
     *   "relativeEventType": "FIRST_BOREHOLE_START",
     *   "relativeDurationMinutes": 480,
     *   "timeWindowStart": "08:00:00",
     *   "timeWindowEnd": "22:00:00"
     * }
     * 说明：钻孔开始后 480 分钟（8小时），在 08:00-22:00 时间窗口内触发
     * 
     * 【示例数据 - RELATIVE 类型（另一个示例）】
     * {
     *   "conditionType": "RELATIVE",
     *   "relativeEventType": "OPERATION_END",
     *   "relativeDurationMinutes": 60,
     *   "timeWindowStart": null,
     *   "timeWindowEnd": null
     * }
     * 说明：操作结束后 60 分钟触发，不限制时间窗口
     * 
     * 【示例数据 - HYBRID 类型（混合条件）】
     * {
     *   "conditionType": "HYBRID",
     *   "logicalOperator": "AND",
     *   "combinedConditionIds": "10,11"
     * }
     * 说明：条件10 AND 条件11 都满足时触发（如：绝对时刻16:00 AND 相对事件8小时后）
     * 
     * 【示例数据 - HYBRID 类型（OR 逻辑）】
     * {
     *   "conditionType": "HYBRID",
     *   "logicalOperator": "OR",
     *   "combinedConditionIds": "10,12"
     * }
     * 说明：条件10 OR 条件12 任一满足即触发
     */
    @PostMapping("/trigger-condition")
    public ApiResponse<?> createTriggerCondition(@RequestBody TriggerCondition condition) {
        try {
            condition.setCreatedAt(LocalDateTime.now());
            condition.setUpdatedAt(LocalDateTime.now());
            triggerConditionRepository.insert(condition);
            return ApiResponse.success("触发条件创建成功", condition);
        } catch (Exception e) {
            log.error("创建触发条件失败", e);
            return ApiResponse.error("创建触发条件失败: " + e.getMessage());
        }
    }

    // ==================== 报警规则管理 ====================

    /**
     * 创建报警规则（兼容：POST /api/alert/rule 与 /api/alert/rules）
     * 
     * 【示例数据 - LEVEL_1 + LOG 动作】
     * POST /api/alert/rule
     * {
     *   "exceptionTypeId": 1,
     *   "level": "LEVEL_1",
     *   "triggerConditionId": 10,
     *   "actionType": "LOG",
     *   "actionConfig": {
     *     "logLevel": "WARN",
     *     "message": "异常LEVEL_1告警已触发，请关注"
     *   },
     *   "priority": 5,
     *   "enabled": true
     * }
     * 说明：异常类型1 的一级告警，在条件10满足时记录日志
     * 
     * 【示例数据 - LEVEL_2 + EMAIL 动作】
     * {
     *   "exceptionTypeId": 1,
     *   "level": "LEVEL_2",
     *   "triggerConditionId": 11,
     *   "actionType": "EMAIL",
     *   "actionConfig": {
     *     "recipients": ["admin@company.com", "team_lead@company.com"],
     *     "subject": "【重要】入井记录不足预警 - LEVEL_2",
     *     "template": "alert_level_2_email_template",
     *     "cc": ["supervisor@company.com"],
     *     "priority": "high"
     *   },
     *   "priority": 6,
     *   "enabled": true
     * }
     * 说明：异常类型1 的二级告警，发送邮件通知多个人员
     * 
     * 【示例数据 - LEVEL_2 + SMS 动作】
     * {
     *   "exceptionTypeId": 1,
     *   "level": "LEVEL_2",
     *   "triggerConditionId": 11,
     *   "actionType": "SMS",
     *   "actionConfig": {
     *     "phoneNumbers": ["13800138000", "13900139000"],
     *     "content": "【告警】班次入井记录不足，请立即处理"
     *   },
     *   "priority": 6,
     *   "enabled": true
     * }
     * 说明：异常类型1 的二级告警，发送短信到指定手机
     * 
     * 【示例数据 - LEVEL_3 + WEBHOOK 动作】
     * {
     *   "exceptionTypeId": 1,
     *   "level": "LEVEL_3",
     *   "triggerConditionId": 12,
     *   "actionType": "WEBHOOK",
     *   "actionConfig": {
     *     "url": "https://api.company.com/alert/notify",
     *     "method": "POST",
     *     "headers": {
     *       "Authorization": "Bearer token_xxx",
     *       "Content-Type": "application/json"
     *     },
     *     "timeout": 5000,
     *     "retries": 3
     *   },
     *   "priority": 8,
     *   "enabled": true
     * }
     * 说明：异常类型1 的三级告警，调用外部 API 集成告警
     * 
     * 【示例数据 - LEVEL_3 + EMAIL + SMS 组合】
     * {
     *   "exceptionTypeId": 2,
     *   "level": "LEVEL_3",
     *   "triggerConditionId": 12,
     *   "actionType": "EMAIL",
     *   "actionConfig": {
     *     "recipients": ["executive@company.com", "director@company.com"],
     *     "subject": "【紧急】严重异常告警 - 需要立即处理",
     *     "template": "alert_critical_template"
     *   },
     *   "priority": 10,
     *   "enabled": true
     * }
     * 说明：可创建多条不同动作类型的规则，系统会依次执行（先EMAIL、再SMS、再WEBHOOK等）
     */
    @PostMapping({"/rule", "/rules"})
    public ApiResponse<?> createAlertRule(@RequestBody AlertRule rule) {
        try {
            rule.setCreatedAt(LocalDateTime.now());
            rule.setUpdatedAt(LocalDateTime.now());
            if (rule.getEnabled() == null) {
                rule.setEnabled(true);
            }
            if (rule.getPriority() == null) {
                rule.setPriority(5);
            }
            alertRuleRepository.insert(rule);
            return ApiResponse.success("报警规则创建成功", rule);
        } catch (Exception e) {
            log.error("创建报警规则失败", e);
            return ApiResponse.error("创建报警规则失败: " + e.getMessage());
        }
    }

    /**
     * 获取异常类型的所有报警规则
     * 
     * 【示例请求】
     * GET /api/alert/rules/1
     * 
     * 【示例响应】
     * {
     *   "code": 0,
     *   "message": "查询成功",
     *   "data": [
     *     {
     *       "id": 1,
     *       "exceptionTypeId": 1,
     *       "level": "LEVEL_1",
     *       "triggerConditionId": 10,
     *       "actionType": "LOG",
     *       "actionConfig": {"logLevel": "WARN"},
     *       "priority": 5,
     *       "enabled": true
     *     },
     *     {
     *       "id": 2,
     *       "exceptionTypeId": 1,
     *       "level": "LEVEL_2",
     *       "triggerConditionId": 11,
     *       "actionType": "EMAIL",
     *       "actionConfig": {"recipients": ["admin@company.com"]},
     *       "priority": 6,
     *       "enabled": true
     *     },
     *     {
     *       "id": 3,
     *       "exceptionTypeId": 1,
     *       "level": "LEVEL_3",
     *       "triggerConditionId": 12,
     *       "actionType": "SMS",
     *       "actionConfig": {"phoneNumbers": ["13800138000"]},
     *       "priority": 8,
     *       "enabled": true
     *     }
     *   ]
     * }
     * 说明：返回异常类型1 的所有规则，按优先级排序（LEVEL_1 < LEVEL_2 < LEVEL_3）
     */
    @GetMapping("/rules/{exceptionTypeId}")
    public ApiResponse<?> getRulesByExceptionType(@PathVariable Long exceptionTypeId) {
        try {
            List<AlertRule> rules = alertRuleRepository.findEnabledRulesByExceptionType(exceptionTypeId);
            // 按等级优先级排序（从低到高）
            // BLUE/LEVEL_1 (priority=1) < YELLOW/LEVEL_2 (priority=2) < RED/LEVEL_3 (priority=3)
            rules.sort(Comparator.comparingInt((AlertRule rule) -> AlertConstants.AlertLevels.getPriority(rule.getLevel()))
                                 .thenComparingLong(AlertRule::getId));
            return ApiResponse.success("查询成功", rules);
        } catch (Exception e) {
            log.error("查询报警规则失败", e);
            return ApiResponse.error("查询报警规则失败: " + e.getMessage());
        }
    }

    /**
     * 获取所有报警规则（列表接口）
     * 
     * 【示例请求】
     * GET /api/alert/rules
     * GET /api/alert/rules?level=LEVEL_1
     * GET /api/alert/rules?enabled=true
     * 
     * 【示例响应】
     * {
     *   "code": 0,
     *   "message": "查询成功",
     *   "data": [
     *     {
     *       "id": 1,
     *       "exceptionTypeId": 1,
     *       "level": "LEVEL_1",
     *       "triggerConditionId": 10,
     *       "actionType": "LOG",
     *       "priority": 5,
     *       "enabled": true
     *     },
     *     ... (其他规则)
     *   ]
     * }
     */
    @GetMapping("/rules")
    public ApiResponse<?> listAllRules(@RequestParam(required = false) String level,
                                       @RequestParam(required = false) Boolean enabled,
                                       @RequestParam(required = false) String orgScope) {
        try {
            // 简化：直接返回全部，必要时可加过滤条件
            List<AlertRule> rules = alertRuleRepository.selectList(null);
            return ApiResponse.success("查询成功", rules);
        } catch (Exception e) {
            log.error("查询报警规则列表失败", e);
            return ApiResponse.error("查询报警规则列表失败: " + e.getMessage());
        }
    }

    /**
     * 获取单个报警规则
     * 
     * 【示例请求】
     * GET /api/alert/rules/item/2
     * 
     * 【示例响应】
     * {
     *   "code": 0,
     *   "message": "查询成功",
     *   "data": {
     *     "id": 2,
     *     "exceptionTypeId": 1,
     *     "level": "LEVEL_2",
     *     "triggerConditionId": 11,
     *     "actionType": "EMAIL",
     *     "actionConfig": {
     *       "recipients": ["admin@company.com", "team@company.com"],
     *       "subject": "入井记录不足预警 - LEVEL_2",
     *       "template": "alert_level_2_template"
     *     },
     *     "priority": 6,
     *     "enabled": true,
     *     "createdAt": "2025-12-01T09:00:00",
     *     "updatedAt": "2025-12-12T10:00:00"
     *   }
     * }
     */
    @GetMapping("/rules/item/{id}")
    public ApiResponse<?> getRule(@PathVariable Long id) {
        try {
            AlertRule rule = alertRuleRepository.selectById(id);
            if (rule == null) return ApiResponse.error("报警规则不存在");
            return ApiResponse.success("查询成功", rule);
        } catch (Exception e) {
            log.error("查询报警规则失败", e);
            return ApiResponse.error("查询报警规则失败: " + e.getMessage());
        }
    }

    /**
     * 更新报警规则
     * 
     * 【示例请求 - 修改收件人邮箱】
     * PUT /api/alert/rules/2
     * {
     *   "actionConfig": {
     *     "recipients": ["admin@company.com", "new_manager@company.com"],
     *     "subject": "【更新】入井记录不足预警 - LEVEL_2",
     *     "template": "alert_level_2_template"
     *   }
     * }
     * 
     * 【示例请求 - 修改优先级】
     * PUT /api/alert/rules/3
     * {
     *   "priority": 9
     * }
     * 
     * 【示例请求 - 修改触发条件】
     * PUT /api/alert/rules/2
     * {
     *   "triggerConditionId": 13,
     *   "level": "LEVEL_2"
     * }
     * 
     * 【示例响应】
     * {
     *   "code": 0,
     *   "message": "更新成功",
     *   "data": {
     *     "id": 2,
     *     "exceptionTypeId": 1,
     *     "level": "LEVEL_2",
     *     "triggerConditionId": 11,
     *     "actionType": "EMAIL",
     *     "actionConfig": {
     *       "recipients": ["admin@company.com", "new_manager@company.com"],
     *       "subject": "【更新】入井记录不足预警 - LEVEL_2"
     *     },
     *     "priority": 6,
     *     "enabled": true,
     *     "updatedAt": "2025-12-13T14:30:00"
     *   }
     * }
     */
    @PutMapping("/rules/{id}")
    public ApiResponse<?> updateRule(@PathVariable Long id, @RequestBody AlertRule patch) {
        try {
            AlertRule existing = alertRuleRepository.selectById(id);
            if (existing == null) return ApiResponse.error("报警规则不存在");
            patch.setId(id);
            patch.setUpdatedAt(LocalDateTime.now());
            alertRuleRepository.updateById(patch);
            return ApiResponse.success("更新成功", alertRuleRepository.selectById(id));
        } catch (Exception e) {
            log.error("更新报警规则失败", e);
            return ApiResponse.error("更新报警规则失败: " + e.getMessage());
        }
    }

    /**
     * 删除报警规则
     * 
     * 【示例请求】
     * DELETE /api/alert/rules/5
     * 
     * 【示例响应 - 删除成功】
     * {
     *   "code": 0,
     *   "message": "删除成功",
     *   "data": 5
     * }
     * 
     * 【示例响应 - 规则不存在】
     * {
     *   "code": 1,
     *   "message": "报警规则不存在或已删除"
     * }
     * 
     * 注意：删除后，该规则关联的计划任务仍可能存在，需要单独清理
     */
    @DeleteMapping("/rules/{id}")
    public ApiResponse<?> deleteRule(@PathVariable Long id) {
        try {
            int rows = alertRuleRepository.deleteById(id);
            if (rows == 0) return ApiResponse.error("报警规则不存在或已删除");
            return ApiResponse.success("删除成功", id);
        } catch (Exception e) {
            log.error("删除报警规则失败", e);
            return ApiResponse.error("删除报警规则失败: " + e.getMessage());
        }
    }

    /**
     * 启用/禁用报警规则
     * 
     * 【示例请求 - 禁用规则】
     * PUT /api/alert/rules/2/enabled
     * {
     *   "enabled": false
     * }
     * 
     * 【示例请求 - 启用规则】
     * PUT /api/alert/rules/2/enabled
     * {
     *   "enabled": true
     * }
     * 
     * 【示例响应】
     * {
     *   "code": 0,
     *   "message": "更新成功",
     *   "data": {
     *     "id": 2,
     *     "exceptionTypeId": 1,
     *     "level": "LEVEL_2",
     *     "actionType": "EMAIL",
     *     "priority": 6,
     *     "enabled": false,
     *     "updatedAt": "2025-12-13T14:30:00"
     *   }
     * }
     * 
     * 说明：禁用后，该规则不再被用于报警评估，但数据保留
     */
    @PutMapping("/rules/{id}/enabled")
    public ApiResponse<?> toggleEnabled(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        try {
            AlertRule rule = alertRuleRepository.selectById(id);
            if (rule == null) return ApiResponse.error("报警规则不存在");
            Object enabledObj = body.get("enabled");
            if (!(enabledObj instanceof Boolean)) {
                return ApiResponse.error("参数错误: enabled");
            }
            rule.setEnabled((Boolean) enabledObj);
            rule.setUpdatedAt(LocalDateTime.now());
            alertRuleRepository.updateById(rule);
            return ApiResponse.success("更新成功", rule);
        } catch (Exception e) {
            log.error("更新启用状态失败", e);
            return ApiResponse.error("更新启用状态失败: " + e.getMessage());
        }
    }

    // ==================== 异常事件管理 ====================

    /**
     * 报告异常事件
     * 
     * 【示例数据 - 最小必填】
     * POST /api/alert/event
     * {
     *   "exceptionTypeId": 1,
     *   "businessId": "SHIFT_20251213_001",
     *   "businessType": "SHIFT",
     *   "detectionContext": {
     *     "team": "A队",
     *     "shift_id": "SHIFT_20251213_001"
     *   }
     * }
     * 说明：创建一个班次异常事件，系统自动设置 status=ACTIVE、current_alert_level=NONE
     * 
     * 【示例数据 - 完整信息】
     * {
     *   "exceptionTypeId": 1,
     *   "businessId": "BOREHOLE_20251213_001",
     *   "businessType": "BOREHOLE",
     *   "detectionContext": {
     *     "borehole_id": "BOREHOLE_20251213_001",
     *     "team": "B队",
     *     "location": "3号矿井",
     *     "shift_start_time": "2025-12-13T08:00:00",
     *     "detected_by": "RECORD_CHECK",
     *     "operation_start_time": "2025-12-13T10:00:00"
     *   }
     * }
     * 说明：钻孔异常事件，包含丰富的上下文信息
     * 
     * 【示例响应】
     * {
     *   "code": 0,
     *   "message": "异常事件报告成功",
     *   "data": {
     *     "id": 100,
     *     "exceptionTypeId": 1,
     *     "businessId": "SHIFT_20251213_001",
     *     "businessType": "SHIFT",
     *     "detectedAt": "2025-12-13T14:30:00",
     *     "status": "ACTIVE",
     *     "currentAlertLevel": "NONE",
     *     "detectionContext": {...},
     *     "pendingEscalations": {},
     *     "createdAt": "2025-12-13T14:30:00",
     *     "updatedAt": "2025-12-13T14:30:00"
     *   }
     * }
     * 
     * 【自动处理】
     * 1. 异常事件创建后，系统自动调用 alertEscalationService.scheduleInitialEvaluation()
     * 2. 根据异常类型的所有 LEVEL_1 规则创建计划任务 (scheduled_task)
     * 3. 初始化 pending_escalations，标记 LEVEL_2/3 为 WAITING 状态
     */
    @PostMapping("/event")
    public ApiResponse<?> reportExceptionEvent(@RequestBody ExceptionEvent event) {
        try {
            event.setDetectedAt(LocalDateTime.now());
            event.setStatus(AlertConstants.ExceptionEventStatus.ACTIVE);
            event.setCurrentAlertLevel(AlertConstants.AlertLevels.NONE);
            event.setCreatedAt(LocalDateTime.now());
            event.setUpdatedAt(LocalDateTime.now());

            exceptionEventRepository.insert(event);

            // 为这个异常事件创建初始的评估任务
            alertEscalationService.scheduleInitialEvaluation(event);

            return ApiResponse.success("异常事件报告成功", event);
        } catch (Exception e) {
            log.error("报告异常事件失败", e);
            return ApiResponse.error("报告异常事件失败: " + e.getMessage());
        }
    }

    /**
     * 获取所有活跃异常事件
     * 
     * 【示例请求】
     * GET /api/alert/events/active
     * 
     * 【示例响应】
     * {
     *   "code": 0,
     *   "message": "查询成功",
     *   "data": [
     *     {
     *       "id": 100,
     *       "exceptionTypeId": 1,
     *       "businessId": "SHIFT_20251213_001",
     *       "businessType": "SHIFT",
     *       "status": "ACTIVE",
     *       "currentAlertLevel": "LEVEL_1",
     *       "detectedAt": "2025-12-13T08:00:00",
     *       "lastEscalatedAt": "2025-12-13T12:00:00",
     *       "pendingEscalations": {
     *         "LEVEL_2": {
     *           "status": "READY",
     *           "createdAt": "2025-12-13T08:02:00",
     *           "readyAt": "2025-12-13T12:00:00",
     *           "scheduledTime": "2025-12-13T12:00:00",
     *           "taskId": "12346"
     *         },
     *         "LEVEL_3": {
     *           "status": "WAITING",
     *           "createdAt": "2025-12-13T08:02:00"
     *         }
     *       },
     *       "createdAt": "2025-12-13T08:00:00"
     *     },
     *     {
     *       "id": 101,
     *       "exceptionTypeId": 2,
     *       "businessId": "BOREHOLE_20251213_002",
     *       "businessType": "BOREHOLE",
     *       "status": "ACTIVE",
     *       "currentAlertLevel": "NONE",
     *       "detectedAt": "2025-12-13T14:00:00",
     *       "createdAt": "2025-12-13T14:00:00"
     *     }
     *   ]
     * }
     * 
     * 说明：只返回 status=ACTIVE 的异常，RESOLVING/RESOLVED 的异常不显示
     */
    @GetMapping("/events/active")
    public ApiResponse<?> getActiveEvents() {
        try {
            List<ExceptionEvent> events = exceptionEventRepository.findActiveEvents();
            return ApiResponse.success("查询成功", events);
        } catch (Exception e) {
            log.error("查询活跃异常事件失败", e);
            return ApiResponse.error("查询活跃异常事件失败: " + e.getMessage());
        }
    }

    /**
     * 获取异常事件详情（包括完整的报警日志历史）
     * 
     * 【示例请求】
     * GET /api/alert/event/100
     * 
     * 【示例响应】
     * {
     *   "code": 0,
     *   "message": "查询成功",
     *   "data": {
     *     "event": {
     *       "id": 100,
     *       "exceptionTypeId": 1,
     *       "businessId": "SHIFT_20251213_001",
     *       "businessType": "SHIFT",
     *       "status": "ACTIVE",
     *       "currentAlertLevel": "LEVEL_2",
     *       "detectedAt": "2025-12-13T08:00:00",
     *       "lastEscalatedAt": "2025-12-13T12:00:00",
     *       "detectionContext": {
     *         "shift_id": "SHIFT_20251213_001",
     *         "team": "A队",
     *         "FIRST_BOREHOLE_START_time": "2025-12-13T10:00:00"
     *       },
     *       "pendingEscalations": {
     *         "LEVEL_3": {
     *           "status": "WAITING",
     *           "dependencies": [{"eventType": "OPERATION_END", "delayMinutes": 60}]
     *         }
     *       }
     *     },
     *     "logs": [
     *       {
     *         "id": 1001,
     *         "exception_event_id": 100,
     *         "alert_rule_id": 1,
     *         "alert_level": "LEVEL_1",
     *         "event_type": "ALERT_TRIGGERED",
     *         "triggered_at": "2025-12-13T08:30:00",
     *         "trigger_reason": "条件满足，触发LEVEL_1报警",
     *         "action_status": "SENT",
     *         "action_error_message": null
     *       },
     *       {
     *         "id": 1002,
     *         "exception_event_id": 100,
     *         "alert_rule_id": 2,
     *         "alert_level": "LEVEL_2",
     *         "event_type": "ALERT_ESCALATED",
     *         "triggered_at": "2025-12-13T12:00:00",
     *         "trigger_reason": "钻孔开始事件已发生，升级到LEVEL_2",
     *         "action_status": "SENT",
     *         "action_error_message": null
     *       }
     *     ]
     *   }
     * }
     * 
     * 说明：event 包含完整的异常状态和 pending_escalations；logs 包含所有报警、升级、解除的历史记录
     */
    @GetMapping("/event/{eventId}")
    public ApiResponse<?> getEventDetail(@PathVariable Long eventId) {
        try {
            ExceptionEvent event = exceptionEventRepository.selectById(eventId);
            if (event == null) {
                return ApiResponse.error("异常事件不存在");
            }

            // 获取该事件的所有报警日志
            List<AlertEventLog> logs = alertEventLogRepository.findByExceptionEventId(eventId);

            Map<String, Object> result = new HashMap<>();
            result.put("event", event);
            result.put("logs", logs);

            return ApiResponse.success("查询成功", result);
        } catch (Exception e) {
            log.error("查询异常事件详情失败", e);
            return ApiResponse.error("查询异常事件详情失败: " + e.getMessage());
        }
    }

    /**
     * 解决异常事件（手动解除）
     * 
     * 【示例请求】
     * PUT /api/alert/event/100/resolve
     * 
     * 【处理流程】
     * 1. 检查异常是否存在
     * 2. 将 status 改为 RESOLVING（防护状态，防止重复处理）
     * 3. 查询并取消所有 status=PENDING 的 scheduled_task
     * 4. 对每个被取消的任务记录 alert_event_log (eventType=TASK_CANCELLED)
     * 5. 记录 alert_event_log (eventType=ALERT_RESOLVED，resolution_source=MANUAL_RESOLUTION)
     * 6. 最终更新 exception_event：
     *    - status = RESOLVED
     *    - resolved_at = NOW()
     *    - pending_escalations = NULL
     *    - resolution_reason = "用户手动解除"
     *    - resolution_source = "MANUAL_RESOLUTION"
     * 
     * 【示例响应 - 成功】
     * {
     *   "code": 0,
     *   "message": "异常事件已解决"
     * }
     * 
     * 【示例响应 - 异常不存在】
     * {
     *   "code": 1,
     *   "message": "解决异常事件失败: 异常事件不存在"
     * }
     * 
     * 【重要说明】
     * - 解除后，异常不再产生新的报警
     * - 所有待机的升级任务（LEVEL_2、LEVEL_3）都会被取消
     * - 若异常重新被检测到，会创建新的 exception_event
     * - 完整的历史日志保存在 alert_event_log 中
     * - 若需要追溯，可通过 GET /api/alert/event/{eventId} 查询历史
     */
    @PutMapping("/event/{eventId}/resolve")
    public ApiResponse<?> resolveEvent(@PathVariable Long eventId) {
        try {
            alertEscalationService.resolveEvent(eventId);
            return ApiResponse.success("异常事件已解决");
        } catch (Exception e) {
            log.error("解决异常事件失败", e);
            return ApiResponse.error("解决异常事件失败: " + e.getMessage());
        }
    }

    // ==================== 规则配置管理（规则+触发条件一体） ====================

    /**
     * 创建报警规则配置（规则 + 触发条件 + 阈值一体）
     * 
     * 【示例请求】
     * POST /api/alert/rule-config
     * {
     *   "exceptionTypeId": 1,
     *   "level": "LEVEL_1",
     *   "orgScope": "山西省",
     *   "enabled": true,
     *   "triggerCondition": {
     *     "conditionType": "ABSOLUTE",
     *     "absoluteTime": "16:00:00"
     *   },
     *   "operator": ">",
     *   "thresholdValue": 16,
     *   "timeUnit": "HOUR",
     *   "actionType": "LOG",
     *   "actionConfig": {
     *     "logLevel": "WARN",
     *     "message": "异常告警已触发"
     *   },
     *   "priority": 5
     * }
     * 
     * 【示例响应】
     * {
     *   "code": 0,
     *   "message": "规则配置创建成功",
     *   "data": {
     *     "id": 1,
     *     "exceptionTypeId": 1,
     *     "level": "LEVEL_1",
     *     "orgScope": "山西省",
     *     "enabled": true,
     *     "triggerCondition": {
     *       "id": 10,
     *       "conditionType": "ABSOLUTE",
     *       "absoluteTime": "16:00:00",
     *       "displayName": "每天16:00触发"
     *     },
     *     "operator": ">",
     *     "thresholdValue": 16,
     *     "timeUnit": "HOUR",
     *     "actionType": "LOG",
     *     "actionConfig": {...},
     *     "priority": 5
     *   }
     * }
     */
    @PostMapping("/rule-config")
    public ApiResponse<?> createRuleConfig(@RequestBody AlertRuleConfigDTO configDTO) {
        try {
            // 1. 创建或获取触发条件
            TriggerCondition triggerCondition = new TriggerCondition();
            AlertRuleConfigDTO.TriggerConditionInfo tcInfo = configDTO.getTriggerCondition();
            
            triggerCondition.setConditionType(tcInfo.getConditionType());
            // 时间字段转换：String -> LocalTime
            if (tcInfo.getAbsoluteTime() != null) {
                triggerCondition.setAbsoluteTime(java.time.LocalTime.parse(tcInfo.getAbsoluteTime()));
            }
            triggerCondition.setRelativeEventType(tcInfo.getRelativeEventType());
            triggerCondition.setRelativeDurationMinutes(tcInfo.getRelativeDurationMinutes());
            if (tcInfo.getTimeWindowStart() != null) {
                triggerCondition.setTimeWindowStart(java.time.LocalTime.parse(tcInfo.getTimeWindowStart()));
            }
            if (tcInfo.getTimeWindowEnd() != null) {
                triggerCondition.setTimeWindowEnd(java.time.LocalTime.parse(tcInfo.getTimeWindowEnd()));
            }
            triggerCondition.setLogicalOperator(tcInfo.getLogicalOperator());
            triggerCondition.setCombinedConditionIds(tcInfo.getCombinedConditionIds());
            triggerCondition.setCreatedAt(LocalDateTime.now());
            triggerCondition.setUpdatedAt(LocalDateTime.now());
            triggerConditionRepository.insert(triggerCondition);

            // 2. 创建报警规则
            AlertRule rule = new AlertRule();
            rule.setExceptionTypeId(configDTO.getExceptionTypeId());
            rule.setLevel(configDTO.getLevel());
            rule.setTriggerConditionId(triggerCondition.getId());
            rule.setActionType(configDTO.getActionType());
            rule.setActionConfig(configDTO.getActionConfig());
            rule.setPriority(configDTO.getPriority() != null ? configDTO.getPriority() : 5);
            rule.setEnabled(configDTO.getEnabled() != null ? configDTO.getEnabled() : true);
            rule.setCreatedAt(LocalDateTime.now());
            rule.setUpdatedAt(LocalDateTime.now());
            
            // 扩展字段（可选）
            if (configDTO.getOrgScope() != null) {
                // 如果 AlertRule 添加了 orgScope 字段，这里设置
                // rule.setOrgScope(configDTO.getOrgScope());
            }
            if (configDTO.getOperator() != null) {
                // 如果 AlertRule 添加了 operator 字段，这里设置
                // rule.setOperator(configDTO.getOperator());
            }
            if (configDTO.getThresholdValue() != null) {
                // rule.setThresholdValue(configDTO.getThresholdValue());
            }
            if (configDTO.getTimeUnit() != null) {
                // rule.setTimeUnit(configDTO.getTimeUnit());
            }
            
            alertRuleRepository.insert(rule);

            // 3. 返回完整的配置信息
            return ApiResponse.success("规则配置创建成功", convertToDTO(rule, triggerCondition));
        } catch (Exception e) {
            log.error("创建规则配置失败", e);
            return ApiResponse.error("创建规则配置失败: " + e.getMessage());
        }
    }

    /**
     * 获取单个规则配置
     * 
     * 【示例请求】
     * GET /api/alert/rule-config/1
     * 
     * 【示例响应】
     * {
     *   "code": 0,
     *   "message": "查询成功",
     *   "data": {
     *     "id": 1,
     *     "exceptionTypeId": 1,
     *     "level": "LEVEL_1",
     *     "orgScope": "山西省",
     *     "enabled": true,
     *     "triggerCondition": {...},
     *     "operator": ">",
     *     "thresholdValue": 16,
     *     "timeUnit": "HOUR",
     *     "actionType": "LOG",
     *     "actionConfig": {...},
     *     "priority": 5,
     *     "createdAt": "2025-12-13T10:00:00",
     *     "updatedAt": "2025-12-13T10:00:00"
     *   }
     * }
     */
    @GetMapping("/rule-config/{id}")
    public ApiResponse<?> getRuleConfig(@PathVariable Long id) {
        try {
            AlertRule rule = alertRuleRepository.selectById(id);
            if (rule == null) {
                return ApiResponse.error("规则不存在");
            }
            
            TriggerCondition triggerCondition = triggerConditionRepository.selectById(rule.getTriggerConditionId());
            
            return ApiResponse.success("查询成功", convertToDTO(rule, triggerCondition));
        } catch (Exception e) {
            log.error("查询规则配置失败", e);
            return ApiResponse.error("查询规则配置失败: " + e.getMessage());
        }
    }

    /**
     * 更新规则配置
     * 
     * 【示例请求】
     * PUT /api/alert/rule-config/1
     * {
     *   "level": "LEVEL_2",
     *   "enabled": false,
     *   "operator": ">=",
     *   "thresholdValue": 20,
     *   "actionConfig": {
     *     "recipients": ["admin@company.com"],
     *     "subject": "【更新】报警通知"
     *   }
     * }
     * 
     * 【示例响应】
     * {
     *   "code": 0,
     *   "message": "规则配置更新成功",
     *   "data": {...}
     * }
     */
    @PutMapping("/rule-config/{id}")
    public ApiResponse<?> updateRuleConfig(@PathVariable Long id, @RequestBody AlertRuleConfigDTO configDTO) {
        try {
            AlertRule existingRule = alertRuleRepository.selectById(id);
            if (existingRule == null) {
                return ApiResponse.error("规则不存在");
            }

            // 更新规则字段
            if (configDTO.getLevel() != null) {
                existingRule.setLevel(configDTO.getLevel());
            }
            if (configDTO.getEnabled() != null) {
                existingRule.setEnabled(configDTO.getEnabled());
            }
            if (configDTO.getPriority() != null) {
                existingRule.setPriority(configDTO.getPriority());
            }
            if (configDTO.getActionType() != null) {
                existingRule.setActionType(configDTO.getActionType());
            }
            if (configDTO.getActionConfig() != null) {
                existingRule.setActionConfig(configDTO.getActionConfig());
            }
            
            // 扩展字段更新
            if (configDTO.getOrgScope() != null) {
                // existingRule.setOrgScope(configDTO.getOrgScope());
            }
            if (configDTO.getOperator() != null) {
                // existingRule.setOperator(configDTO.getOperator());
            }
            if (configDTO.getThresholdValue() != null) {
                // existingRule.setThresholdValue(configDTO.getThresholdValue());
            }
            if (configDTO.getTimeUnit() != null) {
                // existingRule.setTimeUnit(configDTO.getTimeUnit());
            }
            
            existingRule.setUpdatedAt(LocalDateTime.now());
            alertRuleRepository.updateById(existingRule);

            TriggerCondition triggerCondition = triggerConditionRepository.selectById(existingRule.getTriggerConditionId());
            
            return ApiResponse.success("规则配置更新成功", convertToDTO(existingRule, triggerCondition));
        } catch (Exception e) {
            log.error("更新规则配置失败", e);
            return ApiResponse.error("更新规则配置失败: " + e.getMessage());
        }
    }

    /**
     * 删除规则配置（同时删除关联的触发条件）
     * 
     * 【示例请求】
     * DELETE /api/alert/rule-config/1
     * 
     * 【示例响应 - 成功】
     * {
     *   "code": 0,
     *   "message": "规则配置删除成功",
     *   "data": 1
     * }
     */
    @DeleteMapping("/rule-config/{id}")
    public ApiResponse<?> deleteRuleConfig(@PathVariable Long id) {
        try {
            AlertRule rule = alertRuleRepository.selectById(id);
            if (rule == null) {
                return ApiResponse.error("规则不存在");
            }

            // 删除触发条件
            triggerConditionRepository.deleteById(rule.getTriggerConditionId());
            
            // 删除规则
            alertRuleRepository.deleteById(id);

            return ApiResponse.success("规则配置删除成功", id);
        } catch (Exception e) {
            log.error("删除规则配置失败", e);
            return ApiResponse.error("删除规则配置失败: " + e.getMessage());
        }
    }

    /**
     * 获取规则配置列表
     * 
     * 【示例请求】
     * GET /api/alert/rule-configs
     * GET /api/alert/rule-configs?exceptionTypeId=1
     * GET /api/alert/rule-configs?level=LEVEL_1
     * GET /api/alert/rule-configs?enabled=true
     * 
     * 【示例响应】
     * {
     *   "code": 0,
     *   "message": "查询成功",
     *   "data": [
     *     {
     *       "id": 1,
     *       "exceptionTypeId": 1,
     *       "level": "LEVEL_1",
     *       "enabled": true,
     *       "triggerCondition": {...},
     *       "actionType": "LOG",
     *       "priority": 5
     *     },
     *     {
     *       "id": 2,
     *       "exceptionTypeId": 1,
     *       "level": "LEVEL_2",
     *       "enabled": true,
     *       "triggerCondition": {...},
     *       "actionType": "EMAIL",
     *       "priority": 6
     *     }
     *   ]
     * }
     */
    @GetMapping("/rule-configs")
    public ApiResponse<?> listRuleConfigs(
            @RequestParam(required = false) Long exceptionTypeId,
            @RequestParam(required = false) String level,
            @RequestParam(required = false) Boolean enabled) {
        try {
            List<AlertRule> rules = alertRuleRepository.selectList(null);
            
            // 过滤
            List<AlertRuleConfigDTO> configs = rules.stream()
                    .filter(rule -> exceptionTypeId == null || rule.getExceptionTypeId().equals(exceptionTypeId))
                    .filter(rule -> level == null || rule.getLevel().equals(level))
                    .filter(rule -> enabled == null || rule.getEnabled().equals(enabled))
                    .map(rule -> {
                        TriggerCondition tc = triggerConditionRepository.selectById(rule.getTriggerConditionId());
                        return convertToDTO(rule, tc);
                    })
                    .collect(Collectors.toList());

            return ApiResponse.success("查询成功", configs);
        } catch (Exception e) {
            log.error("查询规则配置列表失败", e);
            return ApiResponse.error("查询规则配置列表失败: " + e.getMessage());
        }
    }

    /**
     * 辅助方法：将 AlertRule + TriggerCondition 转换为 AlertRuleConfigDTO
     */
    private AlertRuleConfigDTO convertToDTO(AlertRule rule, TriggerCondition triggerCondition) {
        // 时间字段转换：LocalTime -> String
        String absoluteTimeStr = triggerCondition.getAbsoluteTime() != null 
            ? triggerCondition.getAbsoluteTime().toString() 
            : null;
        String timeWindowStartStr = triggerCondition.getTimeWindowStart() != null 
            ? triggerCondition.getTimeWindowStart().toString() 
            : null;
        String timeWindowEndStr = triggerCondition.getTimeWindowEnd() != null 
            ? triggerCondition.getTimeWindowEnd().toString() 
            : null;

        AlertRuleConfigDTO.TriggerConditionInfo tcInfo = AlertRuleConfigDTO.TriggerConditionInfo.builder()
                .id(triggerCondition.getId())
                .conditionType(triggerCondition.getConditionType())
                .absoluteTime(absoluteTimeStr)
                .relativeEventType(triggerCondition.getRelativeEventType())
                .relativeDurationMinutes(triggerCondition.getRelativeDurationMinutes())
                .timeWindowStart(timeWindowStartStr)
                .timeWindowEnd(timeWindowEndStr)
                .logicalOperator(triggerCondition.getLogicalOperator())
                .combinedConditionIds(triggerCondition.getCombinedConditionIds())
                .displayName(generateDisplayName(triggerCondition))
                .build();

        return AlertRuleConfigDTO.builder()
                .id(rule.getId())
                .exceptionTypeId(rule.getExceptionTypeId())
                .level(rule.getLevel())
                // .orgScope(rule.getOrgScope())  // 待扩展字段
                .enabled(rule.getEnabled())
                .priority(rule.getPriority())
                .triggerCondition(tcInfo)
                // .operator(rule.getOperator())  // 待扩展字段
                // .thresholdValue(rule.getThresholdValue())  // 待扩展字段
                // .timeUnit(rule.getTimeUnit())  // 待扩展字段
                .actionType(rule.getActionType())
                .actionConfig(rule.getActionConfig())
                .createdAt(rule.getCreatedAt())
                .updatedAt(rule.getUpdatedAt())
                .build();
    }

    /**
     * 辅助方法：生成触发条件的显示名称
     */
    private String generateDisplayName(TriggerCondition tc) {
        if (tc == null) return "";
        
        switch (tc.getConditionType()) {
            case "ABSOLUTE":
                return "绝对时刻: " + tc.getAbsoluteTime();
            case "RELATIVE":
                return "相对事件: " + tc.getRelativeEventType() + " 后 " + tc.getRelativeDurationMinutes() + " 分钟";
            case "HYBRID":
                return "混合条件: " + tc.getLogicalOperator() + " (" + tc.getCombinedConditionIds() + ")";
            default:
                return tc.getConditionType();
        }
    }
}
