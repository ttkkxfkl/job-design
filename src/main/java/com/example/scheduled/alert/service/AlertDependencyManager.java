package com.example.scheduled.alert.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.scheduled.alert.constant.AlertConstants;
import com.example.scheduled.alert.entity.ExceptionEvent;
import com.example.scheduled.alert.event.AlertSystemEvent;
import com.example.scheduled.alert.repository.ExceptionEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static com.example.scheduled.alert.constant.AlertConstants.ExceptionEventStatus.ACTIVE;
import static com.example.scheduled.alert.constant.AlertConstants.JsonFields.*;
import static com.example.scheduled.alert.constant.AlertConstants.LogicalOperator.AND;
import static com.example.scheduled.alert.constant.AlertConstants.LogicalOperator.OR;
import static com.example.scheduled.alert.constant.AlertConstants.PendingEscalationStatus.*;
import static com.example.scheduled.alert.constant.AlertConstants.TimeFieldSuffix.TIME;

/**
 * 告警依赖管理服务
 * 负责监听外部系统的事件，并检查是否有待机的报警可以升级
 * 
 * 事件流：
 * 1. 外部系统（如钻孔系统）发布事件（如 BoreholStartEvent）
 * 2. Spring ApplicationEventPublisher 广播事件
 * 3. AlertDependencyManager 监听事件 (@EventListener)
 * 4. 更新 detection_context 记录事件时间
 * 5. 检查所有 ACTIVE 异常的 pending_escalations
 * 6. 如果依赖满足，调用 AlertEscalationService 创建下一级任务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AlertDependencyManager {

    private final ExceptionEventRepository exceptionEventRepository;
    private final AlertEscalationService alertEscalationService;

    /**
     * 监听所有告警系统事件
     * 包括外部业务事件（如钻孔开始、班次结束等）
     * 
     * @param event Spring 事件
     */
    @EventListener
    @Transactional(rollbackFor = Exception.class)
    public void onAlertSystemEvent(AlertSystemEvent event) {
        log.info("监听到告警系统事件: eventType={}, exceptionEventId={}", 
                event.getEventType(), event.getExceptionEventId());

        try {
            // 记录事件时间到 detection_context
            recordEventToContext(event);

            // 检查所有待机的升级任务
            checkAndTriggerPendingEscalations(event);

        } catch (Exception e) {
            log.error("处理告警系统事件时出现异常: eventType={}", event.getEventType(), e);
        }
    }

    /**
     * 将事件记录到 detection_context
     * detection_context 用于追踪已发生的事件时间
     * 
     * 逻辑：
     * 1. 如果事件指定了 exceptionEventId，则只更新该异常事件
     * 2. 如果事件只有 businessId，则更新所有归属于该业务的活跃异常事件
     */
    private void recordEventToContext(AlertSystemEvent event) {
        try {
            java.util.List<ExceptionEvent> eventsToUpdate = new java.util.ArrayList<>();
            
            // 情况1：指定了异常事件ID，直接更新该异常
            if (event.getExceptionEventId() != null) {
                ExceptionEvent exceptionEvent = exceptionEventRepository.selectById(event.getExceptionEventId());
                if (exceptionEvent != null && ACTIVE.equals(exceptionEvent.getStatus())) {
                    eventsToUpdate.add(exceptionEvent);
                }
            }
            // 情况2：只有业务ID，查询所有归属于该业务的活跃异常事件
            else if (event.getBusinessId() != null) {
                eventsToUpdate = exceptionEventRepository.findActiveEventsByBusinessIdAndType(
                    event.getBusinessId(), 
                    event.getBusinessType()
                );
                log.debug("根据业务ID查询到 {} 个活跃异常事件: businessId={}, businessType={}",
                    eventsToUpdate.size(), event.getBusinessId(), event.getBusinessType());
            }
            
            // 更新所有匹配的异常事件的 detection_context
            for (ExceptionEvent exceptionEvent : eventsToUpdate) {
                // 初始化或更新 detection_context
                if (exceptionEvent.getDetectionContext() == null) {
                    exceptionEvent.setDetectionContext(new java.util.HashMap<>());
                }

                // 记录事件发生时间
                exceptionEvent.getDetectionContext().put(
                        event.getEventType() + TIME,
                        LocalDateTime.now().toString()
                );

                exceptionEventRepository.updateById(exceptionEvent);
                log.info("已更新事件上下文: exceptionEventId={}, businessId={}, eventType={}", 
                        exceptionEvent.getId(), exceptionEvent.getBusinessId(), event.getEventType());
            }
            
            if (eventsToUpdate.isEmpty()) {
                log.debug("未找到需要更新的异常事件: eventType={}, businessId={}, exceptionEventId={}",
                    event.getEventType(), event.getBusinessId(), event.getExceptionEventId());
            }

        } catch (Exception e) {
            log.error("记录事件到 detection_context 时出现异常: businessId={}", event.getBusinessId(), e);
        }
    }

    /**
     * 检查并触发待机的报警升级
     * 遍历所有匹配业务ID的 ACTIVE 状态异常，检查其 pending_escalations 中的依赖是否满足
     * 
     * 逻辑：
     * 1. 如果事件指定了 exceptionEventId，只检查该异常
     * 2. 如果事件只有 businessId，检查所有归属于该业务的活跃异常
     */
    private void checkAndTriggerPendingEscalations(AlertSystemEvent event) {
        try {
            List<ExceptionEvent> activeEvents = new ArrayList<>();
            
            // 情况1：指定了异常事件ID，只检查该异常
            if (event.getExceptionEventId() != null) {
                ExceptionEvent exceptionEvent = exceptionEventRepository.selectById(event.getExceptionEventId());
                if (exceptionEvent != null && ACTIVE.equals(exceptionEvent.getStatus()) 
                    && exceptionEvent.getPendingEscalations() != null) {
                    activeEvents.add(exceptionEvent);
                }
            }
            // 情况2：根据业务ID查询所有相关的活跃异常事件
            else if (event.getBusinessId() != null) {
                // 构建查询条件：ACTIVE状态 + 相同businessId + 有待机升级
                LambdaQueryWrapper<ExceptionEvent> wrapper =
                    new LambdaQueryWrapper<ExceptionEvent>()
                        .eq(ExceptionEvent::getStatus, ACTIVE)
                        .eq(ExceptionEvent::getBusinessId, event.getBusinessId())
                        .isNotNull(ExceptionEvent::getPendingEscalations);
                
                // 如果指定了业务类型，也加入过滤条件
                if (event.getBusinessType() != null) {
                    wrapper.eq(ExceptionEvent::getBusinessType, event.getBusinessType());
                }
                
                activeEvents = exceptionEventRepository.selectList(wrapper);
                
                log.debug("根据业务ID查询到 {} 个待检查的异常事件: businessId={}, businessType={}",
                    activeEvents.size(), event.getBusinessId(), event.getBusinessType());
            }

            // 检查每个匹配的异常事件
            for (ExceptionEvent exceptionEvent : activeEvents) {
                checkPendingEscalationsForEvent(exceptionEvent, event);
            }
            
            if (activeEvents.isEmpty()) {
                log.debug("未找到需要检查待机升级的异常事件: eventType={}, businessId={}",
                    event.getEventType(), event.getBusinessId());
            }

        } catch (Exception e) {
            log.error("检查待机升级时出现异常: businessId={}", event.getBusinessId(), e);
        }
    }

    /**
     * 检查单个异常的待机升级
     * 遍历其 pending_escalations 中的每个等级，检查依赖是否满足
     */
    private void checkPendingEscalationsForEvent(ExceptionEvent exceptionEvent, AlertSystemEvent triggeringEvent) {
        try {
            if (exceptionEvent.getPendingEscalations() == null || exceptionEvent.getPendingEscalations().isEmpty()) {
                return;
            }

            // 遍历每个待机的等级
            for (Map.Entry<String, Object> entry : exceptionEvent.getPendingEscalations().entrySet()) {
                String levelName = entry.getKey();
                Object levelData = entry.getValue();

                if (!(levelData instanceof Map)) {
                    continue;
                }

                @SuppressWarnings("unchecked")
                Map<String, Object> levelStatus = (Map<String, Object>) levelData;
                String status = (String) levelStatus.get(STATUS);

                // 只处理 WAITING 状态的升级
                if (!WAITING.equals(status)) {
                    continue;
                }

                // 检查该等级的依赖是否满足
                if (checkDependenciesSatisfied(levelStatus, exceptionEvent)) {
                    log.info("报警升级依赖满足: exceptionEventId={}, level={}, triggeringEvent={}", 
                            exceptionEvent.getId(), levelName, triggeringEvent.getEventType());

                    // 更新 pending_escalations 状态为 READY
                    levelStatus.put(STATUS, READY);
                    levelStatus.put(READY_AT, LocalDateTime.now().toString());
                    exceptionEventRepository.updateById(exceptionEvent);

                    // 为该等级创建评估任务：若存在延迟要求，按事件时间+延迟调度，否则立即调度
                    LocalDateTime maxRequiredTime = null;
                    Object depsObj = levelStatus.get(DEPENDENCIES);
                    if (depsObj instanceof List) {
                        @SuppressWarnings("unchecked")
                        List<Object> dependencies = (List<Object>) depsObj;
                        for (Object depObj : dependencies) {
                            if (!(depObj instanceof Map)) continue;
                            Map<String, Object> dep = (Map<String, Object>) depObj;
                            String depEventType = (String) dep.get(EVENT_TYPE);
                            Number dObj = (Number) dep.get(DELAY_MINUTES);
                            int d = dObj != null ? dObj.intValue() : 0;

                            if (exceptionEvent.getDetectionContext() != null) {
                                Object depTimeObj = exceptionEvent.getDetectionContext().get(depEventType + TIME);
                                if (depTimeObj != null) {
                                    try {
                                        LocalDateTime depTime = LocalDateTime.parse(depTimeObj.toString());
                                        LocalDateTime requiredTime = depTime.plusMinutes(d);
                                        if (maxRequiredTime == null || requiredTime.isAfter(maxRequiredTime)) {
                                            maxRequiredTime = requiredTime;
                                        }
                                    } catch (Exception parseEx) {
                                        log.warn("解析依赖事件时间失败: eventType={}, timeObj={}", depEventType, depTimeObj);
                                    }
                                }
                            }
                        }
                    }

                    if (maxRequiredTime != null && LocalDateTime.now().isBefore(maxRequiredTime)) {
                        alertEscalationService.scheduleEscalationEvaluation(exceptionEvent.getId(), levelName, maxRequiredTime);
                        log.info("延迟调度等级 [{}] 评估任务于 {}: exceptionEventId={}", levelName, maxRequiredTime, exceptionEvent.getId());
                    } else {
                        alertEscalationService.scheduleEscalationEvaluation(exceptionEvent.getId(), levelName);
                        log.info("已为等级 [{}] 创建升级评估任务(立即): exceptionEventId={}", levelName, exceptionEvent.getId());
                    }
                }
            }

        } catch (Exception e) {
            log.error("检查单个异常的待机升级时出现异常: exceptionEventId={}", exceptionEvent.getId(), e);
        }
    }

    /**
     * 检查依赖是否满足
     * 
     * 依赖结构示例：
     * {
     *   "status": "WAITING",
     *   "dependencies": [
     *     {
     *       "eventType": "FIRST_BOREHOLE_START",
     *       "delayMinutes": 120,
     *       "required": true
     *     }
     *   ],
     *   "logicalOperator": "AND"
     * }
     */
    @SuppressWarnings("unchecked")
    private boolean checkDependenciesSatisfied(Map<String, Object> levelStatus, ExceptionEvent event) {
        try {
            Object depsObj = levelStatus.get(DEPENDENCIES);
            if (!(depsObj instanceof List)) {
                log.warn("dependencies 不是 List 类型");
                return false;
            }

            @SuppressWarnings("unchecked")
            List<Object> dependencies = (List<Object>) depsObj;
            String logicalOperator = (String) levelStatus.getOrDefault(LOGICAL_OPERATOR, AND);

            if (dependencies.isEmpty()) {
                return true;  // 没有依赖，直接满足
            }

            boolean allSatisfied = true;
            boolean anySatisfied = false;

            for (Object depObj : dependencies) {
                if (!(depObj instanceof Map)) {
                    continue;
                }

                Map<String, Object> dependency = (Map<String, Object>) depObj;
                boolean isSatisfied = checkSingleDependency(dependency, event);

                if (isSatisfied) {
                    anySatisfied = true;
                } else {
                    allSatisfied = false;
                }
            }

            // AND 逻辑：所有依赖都满足
            if (AND.equalsIgnoreCase(logicalOperator)) {
                return allSatisfied;
            }
            // OR 逻辑：任意一个依赖满足
            else if (OR.equalsIgnoreCase(logicalOperator)) {
                return anySatisfied;
            }

            return false;

        } catch (Exception e) {
            log.error("检查依赖时出现异常", e);
            return false;
        }
    }

    /**
     * 检查单个依赖是否满足
     * 
     * 参数示例：
     * {
     *   "eventType": "FIRST_BOREHOLE_START",
     *   "delayMinutes": 120,
     *   "required": true
     * }
     */
    @SuppressWarnings("unchecked")
    private boolean checkSingleDependency(Map<String, Object> dependency, ExceptionEvent event) {
        try {
            String eventType = (String) dependency.get(EVENT_TYPE);
            Number delayMinutesObj = (Number) dependency.get(DELAY_MINUTES);
            int delayMinutes = delayMinutesObj != null ? delayMinutesObj.intValue() : 0;
            Boolean required = (Boolean) dependency.getOrDefault(REQUIRED, true);

            // 检查 detection_context 中是否有该事件的记录
            if (event.getDetectionContext() == null) {
                log.debug("检测上下文为空，依赖未满足: eventType={}", eventType);
                return false;
            }

            String eventTimeKey = eventType + TIME;
            Object eventTimeObj = event.getDetectionContext().get(eventTimeKey);

            if (eventTimeObj == null) {
                log.debug("事件未发生: eventType={}", eventType);
                return false;
            }

            // 检查时间延迟条件
            try {
                LocalDateTime eventTime = LocalDateTime.parse(eventTimeObj.toString());
                LocalDateTime requiredTime = eventTime.plusMinutes(delayMinutes);
                LocalDateTime now = LocalDateTime.now();

                boolean timeConditionMet = now.isAfter(requiredTime) || now.isEqual(requiredTime);
                
                log.debug("依赖时间检查: eventType={}, eventTime={}, requiredTime={}, now={}, satisfied={}", 
                        eventType, eventTime, requiredTime, now, timeConditionMet);

                return timeConditionMet;

            } catch (Exception e) {
                log.error("解析事件时间时出现异常: eventTimeObj={}", eventTimeObj, e);
                return false;
            }

        } catch (Exception e) {
            log.error("检查单个依赖时出现异常", e);
            return false;
        }
    }
}
