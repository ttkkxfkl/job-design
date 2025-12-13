package com.example.scheduled.alert.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.scheduled.alert.entity.ExceptionEvent;
import com.example.scheduled.alert.enums.ExceptionStatus;
import com.example.scheduled.alert.event.AlertRecoveredEvent;
import com.example.scheduled.alert.repository.ExceptionEventRepository;
import com.example.scheduled.service.TaskManagementService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

import static com.example.scheduled.alert.constant.AlertConstants.PendingEscalationStatus.WAITING;

/**
 * 告警系统恢复服务
 * 在系统启动时执行，负责恢复所有未完成的异常处理流程
 * 
 * 处理场景：
 * 1. 系统在 RESOLVING 状态下崩溃 -> 恢复为 RESOLVED
 * 2. 系统在等待事件状态下崩溃 -> 重新调度待机任务
 * 3. 系统在执行任务状态下崩溃 -> 继续执行任务
 * 
 * 设计原则：
 * - 不依赖 recovery_flag 字段（已废弃）
 * - 基于 pending_escalations 实际状态（WAITING/READY）判断是否需要恢复
 * - 支持多次崩溃恢复，每次启动都检查实际状态
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AlertRecoveryService {

    private final ExceptionEventRepository exceptionEventRepository;
    private final AlertEscalationService alertEscalationService;
    private final ApplicationEventPublisher eventPublisher;
    private final TaskManagementService taskManagementService;

    /**
     * 监听 Spring 应用启动完成事件
     * 在应用完全启动后执行恢复逻辑
     */
    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        log.info("=== 开始执行告警系统恢复机制 ===");
        try {
            recoverAlertSystem();
            log.info("=== 告警系统恢复完成 ===");
        } catch (Exception e) {
            log.error("告警系统恢复过程中出现异常，需要人工干预", e);
        }
    }

    /**
     * 执行告警系统恢复
     * 核心逻辑：
     * 1. 查找所有 ACTIVE 状态的异常事件
     * 2. 基于 pending_escalations 实际状态判断是否需要恢复（有 WAITING/READY 状态）
     * 3. 对需要恢复的异常事件重新调度任务
     * 
     * 注意：已废弃 recovery_flag 字段，改用 pending_escalations 状态作为判断依据
     */
    @Transactional(rollbackFor = Exception.class)
    public void recoverAlertSystem() {
        try {
            // 1. 查询所有待恢复的异常事件
            // 条件：status = ACTIVE（不再依赖 recovery_flag，而是检查 pending_escalations 状态）
            List<ExceptionEvent> activeEvents = exceptionEventRepository.selectList(
                    new LambdaQueryWrapper<ExceptionEvent>()
                            .eq(ExceptionEvent::getStatus, ExceptionStatus.ACTIVE.name())
                            .orderByAsc(ExceptionEvent::getCreatedAt)
            );

            // 过滤出真正需要恢复的事件：有 WAITING 或 READY 状态的待机升级
            List<ExceptionEvent> pendingRecoveryEvents = activeEvents.stream()
                    .filter(event -> hasUnfinishedEscalations(event))
                    .toList();

            log.info("发现 {} 个待恢复的异常事件（共 {} 个ACTIVE事件）", 
                    pendingRecoveryEvents.size(), activeEvents.size());

            int successCount = 0;
            int failureCount = 0;

            for (ExceptionEvent event : pendingRecoveryEvents) {
                try {
                    recoverSingleEvent(event);
                    successCount++;
                } catch (Exception e) {
                    log.error("恢复异常事件失败: exceptionEventId={}", event.getId(), e);
                    failureCount++;
                }
            }

            log.info("异常事件恢复完成: 成功={}, 失败={}", successCount, failureCount);

            // 2. 查询所有 RESOLVING 状态的异常（系统在解除过程中崩溃）
            List<ExceptionEvent> resolvingEvents = exceptionEventRepository.selectList(
                    new LambdaQueryWrapper<ExceptionEvent>()
                            .eq(ExceptionEvent::getStatus, ExceptionStatus.RESOLVING.name())
            );

            log.info("发现 {} 个RESOLVING状态的异常事件（解除过程中崩溃）", resolvingEvents.size());

            for (ExceptionEvent event : resolvingEvents) {
                try {
                    // 完成解除过程，转换为 RESOLVED
                    event.setStatus(ExceptionStatus.RESOLVED.name());
                    exceptionEventRepository.updateById(event);
                    log.info("已完成RESOLVING异常的解除: exceptionEventId={}", event.getId());
                } catch (Exception e) {
                    log.error("完成RESOLVING异常解除失败: exceptionEventId={}", event.getId(), e);
                }
            }

        } catch (Exception e) {
            log.error("告警系统恢复过程中出现严重异常", e);
            throw new RuntimeException("告警系统恢复失败", e);
        }
    }

    /**
     * 恢复单个异常事件
     * 注意：此方法在 recoverAlertSystem() 的事务中执行
     * 
     * @param event 异常事件
     */
    private void recoverSingleEvent(ExceptionEvent event) {
        log.info("开始恢复异常事件: id={}, level={}, createdAt={}", 
                event.getId(), event.getCurrentAlertLevel(), event.getCreatedAt());

        try {
            // 【关键】步骤1: 清理调度系统中的旧任务，避免与新任务重复执行
            if (event.getPendingEscalations() != null && !event.getPendingEscalations().isEmpty()) {
                cleanupOldScheduledTasks(event);
                log.info("已清理旧的调度任务: exceptionEventId={}", event.getId());
            }

            // 步骤2: 根据 pending_escalations 重新调度所有待机的升级任务
            if (event.getPendingEscalations() != null && !event.getPendingEscalations().isEmpty()) {
                reschedulePendingEscalations(event);
                log.info("已重新调度待机升级任务: exceptionEventId={}", event.getId());
            }

            // 注意：不再设置 recovery_flag，而是依靠 pending_escalations 状态判断
            // 当所有级别都完成（不再有 WAITING/READY 状态）时，自然不会再恢复

            // 步骤3: 发布恢复事件
            eventPublisher.publishEvent(new AlertRecoveredEvent(
                    this,
                    event.getId(),
                    event.getBusinessId(),
                    event.getBusinessType(),
                    1,  // 恢复的任务数量
                    String.format("系统恢复: 已清理旧任务并重新调度")
            ));

            log.info("异常事件恢复完成: exceptionEventId={}", event.getId());

        } catch (Exception e) {
            log.error("恢复异常事件失败: exceptionEventId={}", event.getId(), e);
            throw new RuntimeException("恢复异常事件失败: " + e.getMessage(), e);
        }
    }
    
    /**
     * 清理调度系统中的旧任务
     * 防止旧任务与新任务重复执行（适用于 Quartz 持久化模式和 Simple 内存模式）
     */
    private void cleanupOldScheduledTasks(ExceptionEvent event) {
        for (String levelName : event.getPendingEscalations().keySet()) {
            Object levelData = event.getPendingEscalations().get(levelName);
            
            if (levelData instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> levelStatus = (Map<String, Object>) levelData;
                String taskIdStr = (String) levelStatus.get("taskId");
                
                if (taskIdStr != null) {
                    try {
                        // 调用TaskManagementService取消旧任务
                        boolean cancelled = taskManagementService.cancelTask(Long.parseLong(taskIdStr));
                        if (cancelled) {
                            log.info("已取消旧任务: exceptionEventId={}, level={}, taskId={}", 
                                    event.getId(), levelName, taskIdStr);
                        } else {
                            log.warn("取消旧任务失败（可能已执行或不存在）: taskId={}", taskIdStr);
                        }
                    } catch (NumberFormatException e) {
                        log.warn("任务ID格式错误: taskId={}", taskIdStr, e);
                    } catch (Exception e) {
                        log.warn("取消旧任务时出现异常: taskId={}", taskIdStr, e);
                        // 不抛出异常，继续处理
                    }
                }
            }
        }
    }

    /**
     * 重新调度所有待机的升级任务
     * 
     * @param event 异常事件
     */
    private void reschedulePendingEscalations(ExceptionEvent event) {
        try {
            // pending_escalations 结构示例：
            // {
            //   "LEVEL_2": {
            //     "status": "WAITING",
            //     "dependencies": [...],
            //     "createdAt": "2025-12-12T10:02:00"
            //   }
            // }

            // 遍历每个待机的等级，判断是否需要重新调度
            for (String levelName : event.getPendingEscalations().keySet()) {
                Object levelData = event.getPendingEscalations().get(levelName);
                
                if (levelData instanceof Map) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> levelStatus = (Map<String, Object>) levelData;
                    String status = (String) levelStatus.get("status");
                    
                    // 如果是 WAITING 状态，需要重新检查依赖和调度
                    if (WAITING.equals(status)) {
                        log.info("重新调度待机等级: exceptionEventId={}, level={}", event.getId(), levelName);
                        
                        // 重新调度评估任务
                        // alertEscalationService 会自动检查依赖条件
                        alertEscalationService.scheduleEscalationEvaluation(event.getId(), levelName);
                    }
                }
            }

        } catch (Exception e) {
            log.error("重新调度待机升级任务时出现异常: exceptionEventId={}", event.getId(), e);
            // 不抛出异常，继续处理其他任务
        }
    }

    /**
     * 检查异常事件是否有未完成的升级任务
     * 
     * @param event 异常事件
     * @return true 表示有 WAITING 或 READY 状态的待机升级，需要恢复
     */
    private boolean hasUnfinishedEscalations(ExceptionEvent event) {
        if (event.getPendingEscalations() == null || event.getPendingEscalations().isEmpty()) {
            return false;
        }

        for (Object levelData : event.getPendingEscalations().values()) {
            if (levelData instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> levelStatus = (Map<String, Object>) levelData;
                String status = (String) levelStatus.get("status");
                
                // WAITING 或 READY 状态表示还未完成
                if (WAITING.equals(status) || "READY".equals(status)) {
                    return true;
                }
            }
        }

        return false;
    }

    /**
     * 手动触发一次恢复（用于测试或管理界面）
     */
    public void manualRecover() {
        log.info("执行手动触发的告警系统恢复");
        recoverAlertSystem();
    }
}
