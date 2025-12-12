package com.example.scheduled.alert.controller;

import com.example.scheduled.alert.entity.*;
import com.example.scheduled.alert.repository.*;
import com.example.scheduled.alert.service.AlertEscalationService;
import com.example.scheduled.dto.ApiResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
     */
    @GetMapping("/rules/{exceptionTypeId}")
    public ApiResponse<?> getRulesByExceptionType(@PathVariable Long exceptionTypeId) {
        try {
            List<AlertRule> rules = alertRuleRepository.findEnabledRulesByExceptionType(exceptionTypeId);
            return ApiResponse.success("查询成功", rules);
        } catch (Exception e) {
            log.error("查询报警规则失败", e);
            return ApiResponse.error("查询报警规则失败: " + e.getMessage());
        }
    }

    /**
     * 获取所有报警规则（列表接口）
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

    /** 获取单个报警规则 */
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

    /** 更新报警规则 */
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

    /** 删除报警规则 */
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

    /** 启用/禁用报警规则 */
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
     */
    @PostMapping("/event")
    public ApiResponse<?> reportExceptionEvent(@RequestBody ExceptionEvent event) {
        try {
            event.setDetectedAt(LocalDateTime.now());
            event.setStatus("ACTIVE");
            event.setCurrentAlertLevel("NONE");
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
     * 获取异常事件详情
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
     * 解决异常事件
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
}
