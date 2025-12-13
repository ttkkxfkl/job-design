package com.example.scheduled.alert.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
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
 * <p>
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
     * <p>
     * 逻辑：
     * 1. 如果事件指定了 exceptionEventId，则只更新该异常事件
     * 2. 如果事件只有 businessId，则更新所有归属于该业务的活跃异常事件
     */
    private void recordEventToContext(AlertSystemEvent event) {
        try {
            List<ExceptionEvent> eventsToUpdate = new ArrayList<>();

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
     * <p>
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

                Object depsObj = levelStatus.get(DEPENDENCIES);
                Object hybridIdObj = levelStatus.get("hybridConditionId");

                // 情况1：正常情况，有 dependencies 字段
                if (depsObj instanceof List && !((List<?>) depsObj).isEmpty()) {
                    // 第一步：检查所有依赖事件是否都已发生（不考虑时间延迟）
                    boolean eventsSatisfied = checkAllEventsOccurred(levelStatus, exceptionEvent);
                    if (!eventsSatisfied) {
                        log.debug("报警升级依赖事件未满足: exceptionEventId={}, level={}",
                                exceptionEvent.getId(), levelName);
                        continue;
                    }

                    log.info("报警升级依赖事件已满足: exceptionEventId={}, level={}, triggeringEvent={}",
                            exceptionEvent.getId(), levelName, triggeringEvent.getEventType());

                    // 第二步：计算最晚需要等待的时间（考虑所有依赖的延迟时间）
                    LocalDateTime maxRequiredTime = calculateMaxRequiredTime(levelStatus, exceptionEvent);

                    // 第三步：更新 pending_escalations 状态为 READY
                    levelStatus.put(STATUS, READY);
                    levelStatus.put(READY_AT, LocalDateTime.now().toString());
                    if (maxRequiredTime != null) {
                        levelStatus.put("scheduledTime", maxRequiredTime.toString());
                    }
                    exceptionEventRepository.updateById(exceptionEvent);

                    // 第四步：根据时间决定立即执行还是延迟执行
                    LocalDateTime scheduleTime = (maxRequiredTime != null && LocalDateTime.now().isBefore(maxRequiredTime))
                            ? maxRequiredTime
                            : LocalDateTime.now();

                    alertEscalationService.scheduleEscalationEvaluation(exceptionEvent.getId(), levelName, scheduleTime);
                    
                    if (scheduleTime.isAfter(LocalDateTime.now())) {
                        log.info("延迟调度等级 [{}] 评估任务于 {}: exceptionEventId={}", levelName, scheduleTime, exceptionEvent.getId());
                    } else {
                        log.info("立即调度等级 [{}] 评估任务: exceptionEventId={}", levelName, exceptionEvent.getId());
                    }
                }
                // 情况2：混合条件降级
                else if (hybridIdObj != null) {
                    log.warn("发现混合条件降级状态，暂不处理: exceptionEventId={}, level={}, hybridConditionId={}",
                            exceptionEvent.getId(), levelName, hybridIdObj);
                }
                else {
                    log.warn("待机等级缺少依赖信息: exceptionEventId={}, level={}",
                            exceptionEvent.getId(), levelName);
                }
            }

        } catch (Exception e) {
            log.error("检查单个异常的待机升级时出现异常: exceptionEventId={}", exceptionEvent.getId(), e);
        }
    }

    /**
     * 检查所有依赖事件是否都已发生（不考虑时间延迟）
     * <p>
     * 依赖结构示例：
     * {
     * "status": "WAITING",
     * "dependencies": [
     * {
     * "eventType": "FIRST_BOREHOLE_START",
     * "delayMinutes": 120,
     * "required": true
     * }
     * ],
     * "logicalOperator": "AND"
     * }
     *
     * @return true 表示所有需要的事件都已发生，false 表示还有事件未发生
     */
    @SuppressWarnings("unchecked")
    private boolean checkAllEventsOccurred(Map<String, Object> levelStatus, ExceptionEvent event) {
        try {
            Object depsObj = levelStatus.get(DEPENDENCIES);
            if (!(depsObj instanceof List)) {
                log.warn("dependencies 不是 List 类型");
                return false;
            }

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
                boolean eventOccurred = checkEventOccurred(dependency, event);

                if (eventOccurred) {
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
     * 检查单个依赖事件是否已发生（不考虑时间延迟）
     * <p>
     * 参数示例：
     * {
     * "eventType": "FIRST_BOREHOLE_START",
     * "delayMinutes": 120,
     * "required": true
     *
     * @return true 表示事件已发生，false 表示事件未发生
     * }
     */
    private boolean checkEventOccurred(Map<String, Object> dependency, ExceptionEvent event) {
        try {
            String eventType = (String) dependency.get(EVENT_TYPE);

            // 检查 detection_context 中是否有该事件的记录
            if (event.getDetectionContext() == null) {
                log.debug("检测上下文为空，事件未发生: eventType={}", eventType);
                return false;
            }

            String eventTimeKey = eventType + TIME;
            Object eventTimeObj = event.getDetectionContext().get(eventTimeKey);

            if (eventTimeObj == null) {
                log.debug("事件未发生: eventType={}", eventType);
                return false;
            }

            // 事件已发生
            log.debug("事件已发生: eventType={}, eventTime={}", eventType, eventTimeObj);
            return true;

        } catch (Exception e) {
            log.error("检查事件发生时出现异常", e);
            return false;
        }
    }

    /**
     * 计算最晚需要等待的时间（考虑所有依赖的延迟时间）
     *
     * @param levelStatus 等级状态信息
     * @param event       异常事件
     * @return 最晚需要等待到的时间，如果无需等待则返回 null
     */
    private LocalDateTime calculateMaxRequiredTime(Map<String, Object> levelStatus, ExceptionEvent event) {
        LocalDateTime maxRequiredTime = null;

        try {
            Object depsObj = levelStatus.get(DEPENDENCIES);
            if (!(depsObj instanceof List)) {
                return null;
            }
            @SuppressWarnings("unchecked")
            List<Object> dependencies = (List<Object>) depsObj;

            for (Object depObj : dependencies) {
                if (!(depObj instanceof Map)) {
                    continue;
                }

                @SuppressWarnings("unchecked")
                Map<String, Object> dep = (Map<String, Object>) depObj;

                String depEventType = (String) dep.get(EVENT_TYPE);
                Number delayMinutesObj = (Number) dep.get(DELAY_MINUTES);
                int delayMinutes = delayMinutesObj != null ? delayMinutesObj.intValue() : 0;

                if (event.getDetectionContext() == null) {
                    continue;
                }

                Object depTimeObj = event.getDetectionContext().get(depEventType + TIME);
                if (depTimeObj == null) {
                    continue;
                }

                try {
                    LocalDateTime depTime = LocalDateTime.parse(depTimeObj.toString());
                    LocalDateTime requiredTime = depTime.plusMinutes(delayMinutes);

                    // 取所有依赖中最晚的时间
                    if (maxRequiredTime == null || requiredTime.isAfter(maxRequiredTime)) {
                        maxRequiredTime = requiredTime;
                    }

                    log.debug("计算依赖时间: eventType={}, eventTime={}, delayMinutes={}, requiredTime={}",
                            depEventType, depTime, delayMinutes, requiredTime);
                } catch (Exception parseEx) {
                    log.warn("解析依赖事件时间失败: eventType={}, timeObj={}", depEventType, depTimeObj, parseEx);
                }
            }
        } catch (Exception e) {
            log.error("计算最晚等待时间时出现异常", e);
        }

        return maxRequiredTime;
    }
}
