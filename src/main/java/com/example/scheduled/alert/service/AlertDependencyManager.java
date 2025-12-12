package com.example.scheduled.alert.service;

import com.example.scheduled.alert.entity.ExceptionEvent;
import com.example.scheduled.alert.event.AlertSystemEvent;
import com.example.scheduled.alert.repository.ExceptionEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Map;

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
     */
    private void recordEventToContext(AlertSystemEvent event) {
        try {
            // 如果是来自异常事件本身的更新，直接更新该异常
            if (event.getExceptionEventId() != null) {
                ExceptionEvent exceptionEvent = exceptionEventRepository.selectById(event.getExceptionEventId());
                if (exceptionEvent != null) {
                    // 初始化或更新 detection_context
                    if (exceptionEvent.getDetectionContext() == null) {
                        exceptionEvent.setDetectionContext(new java.util.HashMap<>());
                    }

                    // 记录事件发生时间
                    exceptionEvent.getDetectionContext().put(
                            event.getEventType() + "_time",
                            LocalDateTime.now().toString()
                    );

                    exceptionEventRepository.updateById(exceptionEvent);
                    log.info("已更新事件上下文: exceptionEventId={}, eventType={}", 
                            event.getExceptionEventId(), event.getEventType());
                }
            }

        } catch (Exception e) {
            log.error("记录事件到 detection_context 时出现异常", e);
        }
    }

    /**
     * 检查并触发待机的报警升级
     * 遍历所有 ACTIVE 状态的异常，检查其 pending_escalations 中的依赖是否满足
     */
    private void checkAndTriggerPendingEscalations(AlertSystemEvent event) {
        try {
            // 查询所有 ACTIVE 状态的异常事件
            java.util.List<ExceptionEvent> activeEvents = exceptionEventRepository.selectList(
                    new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<ExceptionEvent>()
                            .eq(ExceptionEvent::getStatus, "ACTIVE")
                            .isNotNull(ExceptionEvent::getPendingEscalations)
            );

            for (ExceptionEvent exceptionEvent : activeEvents) {
                checkPendingEscalationsForEvent(exceptionEvent, event);
            }

        } catch (Exception e) {
            log.error("检查待机升级时出现异常", e);
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
                String status = (String) levelStatus.get("status");

                // 只处理 WAITING 状态的升级
                if (!"WAITING".equals(status)) {
                    continue;
                }

                // 检查该等级的依赖是否满足
                if (checkDependenciesSatisfied(levelStatus, exceptionEvent)) {
                    log.info("报警升级依赖满足: exceptionEventId={}, level={}, triggeringEvent={}", 
                            exceptionEvent.getId(), levelName, triggeringEvent.getEventType());

                    // 更新 pending_escalations 状态为 READY
                    levelStatus.put("status", "READY");
                    levelStatus.put("readyAt", LocalDateTime.now().toString());
                    exceptionEventRepository.updateById(exceptionEvent);

                    // 为该等级创建评估任务：若存在延迟要求，按事件时间+延迟调度，否则立即调度
                    LocalDateTime maxRequiredTime = null;
                    Object depsObj = levelStatus.get("dependencies");
                    if (depsObj instanceof java.util.List) {
                        java.util.List<Object> dependencies = (java.util.List<Object>) depsObj;
                        for (Object depObj : dependencies) {
                            if (!(depObj instanceof Map)) continue;
                            Map<String, Object> dep = (Map<String, Object>) depObj;
                            String depEventType = (String) dep.get("eventType");
                            Number dObj = (Number) dep.get("delayMinutes");
                            int d = dObj != null ? dObj.intValue() : 0;

                            if (exceptionEvent.getDetectionContext() != null) {
                                Object depTimeObj = exceptionEvent.getDetectionContext().get(depEventType + "_time");
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
            Object depsObj = levelStatus.get("dependencies");
            if (!(depsObj instanceof java.util.List)) {
                log.warn("dependencies 不是 List 类型");
                return false;
            }

            java.util.List<Object> dependencies = (java.util.List<Object>) depsObj;
            String logicalOperator = (String) levelStatus.getOrDefault("logicalOperator", "AND");

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
            if ("AND".equalsIgnoreCase(logicalOperator)) {
                return allSatisfied;
            }
            // OR 逻辑：任意一个依赖满足
            else if ("OR".equalsIgnoreCase(logicalOperator)) {
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
            String eventType = (String) dependency.get("eventType");
            Number delayMinutesObj = (Number) dependency.get("delayMinutes");
            int delayMinutes = delayMinutesObj != null ? delayMinutesObj.intValue() : 0;
            Boolean required = (Boolean) dependency.getOrDefault("required", true);

            // 检查 detection_context 中是否有该事件的记录
            if (event.getDetectionContext() == null) {
                log.debug("检测上下文为空，依赖未满足: eventType={}", eventType);
                return false;
            }

            String eventTimeKey = eventType + "_time";
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
