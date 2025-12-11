package com.example.scheduled.alert.action;

import com.example.scheduled.alert.entity.AlertRule;
import com.example.scheduled.alert.entity.ExceptionEvent;

import java.util.Map;

/**
 * 报警动作执行器接口
 */
public interface AlertActionExecutor {

    /**
     * 执行报警动作
     * @param actionConfig 动作配置（JSON）
     * @param event 异常事件
     * @param rule 报警规则
     */
    void execute(Map<String, Object> actionConfig, ExceptionEvent event, AlertRule rule) throws Exception;

    /**
     * 判断是否支持该动作类型
     */
    boolean supports(String actionType);

    /**
     * 获取动作类型名称
     */
    String getActionType();
}
