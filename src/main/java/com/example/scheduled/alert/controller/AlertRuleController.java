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
     * 创建报警规则
     */
    @PostMapping("/rule")
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
