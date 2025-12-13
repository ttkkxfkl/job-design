package com.example.scheduled.alert.service;

import com.example.scheduled.alert.entity.AlertEventLog;
import com.example.scheduled.alert.entity.ExceptionEvent;
import com.example.scheduled.alert.event.AlertResolutionEvent;
import com.example.scheduled.alert.repository.AlertEventLogRepository;
import com.example.scheduled.alert.repository.ExceptionEventRepository;
import com.example.scheduled.service.TaskManagementService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

import static com.example.scheduled.alert.constant.AlertConstants.ActionStatus.COMPLETED;
import static com.example.scheduled.alert.constant.AlertConstants.AlertEventType.ALERT_RESOLVED;
import static com.example.scheduled.alert.constant.AlertConstants.AlertEventType.TASK_CANCELLED;
import static com.example.scheduled.alert.constant.AlertConstants.ExceptionEventStatus.*;

/**
 * 报警解除服务
 * 负责处理报警的解除流程，包括：
 * 1. 取消所有后续的待机任务
 * 2. 更新异常事件状态（ACTIVE -> RESOLVING -> RESOLVED）
 * 3. 记录解除事件日志
 * 4. 发布解除事件供其他系统监听
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AlertResolutionService {

    private final ExceptionEventRepository exceptionEventRepository;
    private final AlertEventLogRepository alertEventLogRepository;
    private final TaskManagementService taskManagementService;
    private final AlertEscalationService alertEscalationService;
    private final ApplicationEventPublisher eventPublisher;

    /**
     * 通用报警解除接口
     * 支持手动解除、自动恢复、系统取消等多种方式
     *
     * @param exceptionEventId 异常事件ID
     * @param resolutionReason 解除原因
     * @return 是否解除成功
     */
    @Transactional(rollbackFor = Exception.class)
    public boolean resolveAlert(Long exceptionEventId, String resolutionReason) {
        log.info("开始解除报警: exceptionEventId={}, reason={}", exceptionEventId, resolutionReason);

        try {
            // 1. 查询异常事件
            ExceptionEvent exceptionEvent = exceptionEventRepository.selectById(exceptionEventId);
            if (exceptionEvent == null) {
                log.warn("异常事件不存在: exceptionEventId={}", exceptionEventId);
                return false;
            }

            // 2. 如果已经是 RESOLVED 状态，直接返回成功
            if (RESOLVED.equals(exceptionEvent.getStatus())) {
                log.warn("异常事件已处于RESOLVED状态，无需重复解除: exceptionEventId={}", exceptionEventId);
                return true;
            }

            // 3. 转换为 RESOLVING 状态（防止系统中途崩溃）
            exceptionEvent.setStatus(RESOLVING);
            exceptionEventRepository.updateById(exceptionEvent);
            log.info("异常事件状态转换为RESOLVING: exceptionEventId={}", exceptionEventId);

            // 4. 查询所有相关的待机任务，并进行取消
            int cancelledTaskCount = cancelAllPendingTasks(exceptionEventId);
            log.info("已取消待机任务: exceptionEventId={}, count={}", exceptionEventId, cancelledTaskCount);

            // 5. 记录解除事件日志
            recordResolutionLog(exceptionEventId, resolutionReason);

            // 6. 最终更新为 RESOLVED 状态
            exceptionEvent.setStatus(RESOLVED);
            exceptionEvent.setResolvedAt(LocalDateTime.now());
            exceptionEvent.setResolutionReason(resolutionReason);
            exceptionEventRepository.updateById(exceptionEvent);
            log.info("异常事件状态转换为RESOLVED: exceptionEventId={}", exceptionEventId);

            // 7. 发布报警解除事件（供其他系统监听）
            eventPublisher.publishEvent(new AlertResolutionEvent(
                    this,
                    exceptionEventId,
                    exceptionEvent.getBusinessId(),
                    exceptionEvent.getBusinessType()
            ));

            log.info("报警解除完成: exceptionEventId={}", exceptionEventId);
            return true;

        } catch (Exception e) {
            log.error("报警解除过程中出现异常: exceptionEventId={}", exceptionEventId, e);
            throw new RuntimeException("报警解除失败: " + e.getMessage(), e);
        }
    }

    /**
     * 取消所有待机任务
     * 根据 pending_escalations 字段记录的所有待机任务，逐一取消
     * 同时也使用内存Map作为补充，确保没有遗漏
     *
     * @param exceptionEventId 异常事件ID
     * @return 取消的任务数量
     */
    private int cancelAllPendingTasks(Long exceptionEventId) {
        try {
            int cancelledCount = 0;
            
            // 方案1：从 AlertEscalationService 的内存Map中获取待机任务ID（快速路径）
            List<String> pendingTaskIds = alertEscalationService.getPendingTasks(exceptionEventId);
            for (String taskId : pendingTaskIds) {
                if (cancelTaskById(exceptionEventId, taskId)) {
                    cancelledCount++;
                }
            }
            
            // 方案2：从数据库 pending_escalations 中补充获取（保证完整性）
            ExceptionEvent event = exceptionEventRepository.selectById(exceptionEventId);
            if (event != null && event.getPendingEscalations() != null) {
                for (Object levelData : event.getPendingEscalations().values()) {
                    if (levelData instanceof java.util.Map) {
                        @SuppressWarnings("unchecked")
                        java.util.Map<String, Object> levelStatus = 
                            (java.util.Map<String, Object>) levelData;
                        Object taskIdObj = levelStatus.get("taskId");
                        
                        if (taskIdObj != null) {
                            String taskId = taskIdObj.toString();
                            if (!pendingTaskIds.contains(taskId)) {
                                if (cancelTaskById(exceptionEventId, taskId)) {
                                    cancelledCount++;
                                }
                            }
                        }
                    }
                }
            }

            alertEscalationService.clearPendingTasks(exceptionEventId);

            log.info("待机任务取消完成: exceptionEventId={}, cancelledCount={}", exceptionEventId, cancelledCount);
            return cancelledCount;

        } catch (Exception e) {
            log.error("取消待机任务时出现异常: exceptionEventId={}", exceptionEventId, e);
            throw new RuntimeException("取消待机任务失败: " + e.getMessage(), e);
        }
    }
    
    /**
     * 根据任务ID取消单个任务
     */
    private boolean cancelTaskById(Long exceptionEventId, String taskId) {
        try {
            taskManagementService.cancelTask(Long.parseLong(taskId));
            log.info("已取消任务: exceptionEventId={}, taskId={}", exceptionEventId, taskId);
            recordTaskCancelledLog(exceptionEventId, taskId);
            return true;
        } catch (NumberFormatException e) {
            log.warn("任务ID格式错误: taskId={}", taskId, e);
            return false;
        } catch (Exception e) {
            log.error("取消任务失败: exceptionEventId={}, taskId={}", exceptionEventId, taskId, e);
            return false;
        }
    }

    /**
     * 记录任务取消日志
     */
    private void recordTaskCancelledLog(Long exceptionEventId, String taskId) {
        try {
            AlertEventLog log = AlertEventLog.builder()
                    .exceptionEventId(exceptionEventId)
                    .triggeredAt(LocalDateTime.now())
                    .alertLevel("CANCELLED")
                    .eventType(TASK_CANCELLED)
                    .triggerReason(String.format("任务已取消: taskId=%s", taskId))
                    .actionStatus(COMPLETED)
                    .createdAt(LocalDateTime.now())
                    .build();

            alertEventLogRepository.insert(log);

        } catch (Exception e) {
            log.error("记录任务取消日志失败: exceptionEventId={}, taskId={}", exceptionEventId, taskId, e);
        }
    }

    /**
     * 记录解除事件日志
     * 记录到 alert_event_log 表中，event_type 为 ALERT_RESOLVED
     *
     * @param exceptionEventId 异常事件ID
     * @param resolutionReason 解除原因
     */
    private void recordResolutionLog(Long exceptionEventId, String resolutionReason) {
        try {
            AlertEventLog alertLog = AlertEventLog.builder()
                    .exceptionEventId(exceptionEventId)
                    .triggeredAt(LocalDateTime.now())
                    .alertLevel("RESOLVED")
                    .eventType(ALERT_RESOLVED)
                    .triggerReason(String.format("解除原因: %s", resolutionReason))
                    .actionStatus(COMPLETED)
                    .createdAt(LocalDateTime.now())
                    .build();
            
            alertEventLogRepository.insert(alertLog);
            log.info("已记录解除事件日志: exceptionEventId={}", exceptionEventId);
            
        } catch (Exception e) {
            log.error("记录解除事件日志时出现异常: exceptionEventId={}", exceptionEventId, e);
            // 日志记录失败不影响解除流程
        }
    }

    /**
     * 手动解除报警
     * 用户点击解除按钮时调用此接口
     *
     * @param exceptionEventId 异常事件ID
     * @param resolutionReason 解除原因
     * @return 是否解除成功
     */
    public boolean manualResolveAlert(Long exceptionEventId, String resolutionReason) {
        log.info("执行手动报警解除: exceptionEventId={}", exceptionEventId);
        return resolveAlert(exceptionEventId, resolutionReason);
    }

    /**
     * 自动恢复报警
     * 业务系统检测到异常已恢复，自动调用此接口
     *
     * @param exceptionEventId 异常事件ID
     * @param recoveryReason 恢复原因
     * @return 是否解除成功
     */
    public boolean autoRecoverAlert(Long exceptionEventId, String recoveryReason) {
        log.info("执行自动报警恢复: exceptionEventId={}", exceptionEventId);
        return resolveAlert(exceptionEventId, recoveryReason);
    }

    /**
     * 系统管理员取消报警
     *
     * @param exceptionEventId 异常事件ID
     * @param cancellationReason 取消原因
     * @return 是否解除成功
     */
    public boolean systemCancelAlert(Long exceptionEventId, String cancellationReason) {
        log.info("执行系统取消报警: exceptionEventId={}", exceptionEventId);
        return resolveAlert(exceptionEventId, cancellationReason);
    }
}
