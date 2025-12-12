package com.example.scheduled.alert.action.impl;

import com.example.scheduled.alert.action.AlertActionExecutor;
import com.example.scheduled.alert.constant.AlertConstants;
import com.example.scheduled.alert.entity.AlertRule;
import com.example.scheduled.alert.entity.ExceptionEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.format.DateTimeFormatter;
import java.util.Map;

import static com.example.scheduled.alert.constant.AlertConstants.AlertLevels.*;

/**
 * 日志报警动作执行器 - 将报警信息输出到日志
 */
@Slf4j
@Component
public class LogAlertAction implements AlertActionExecutor {

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Override
    public void execute(Map<String, Object> actionConfig, ExceptionEvent event, AlertRule rule) throws Exception {
        String message = buildMessage(event, rule);
        
        // 根据等级输出不同的日志级别
        switch (rule.getLevel().toUpperCase()) {
            case BLUE, LEVEL_1 -> log.warn("【蓝色预警】{}", message);
            case YELLOW, LEVEL_2 -> log.error("【黄色预警】{}", message);
            case RED, LEVEL_3 -> log.error("【红色警告】{}", message);
            default -> log.info("【报警】{}", message);
        }
    }

    @Override
    public boolean supports(String actionType) {
        return "LOG".equalsIgnoreCase(actionType);
    }

    @Override
    public String getActionType() {
        return "LOG";
    }

    private String buildMessage(ExceptionEvent event, AlertRule rule) {
        return String.format(
            "异常ID[%d] | 类型[%d] | 等级[%s] | 检测时间[%s] | 当前等级[%s]",
            event.getId(),
            event.getExceptionTypeId(),
            rule.getLevel(),
            event.getDetectedAt().format(FORMATTER),
            event.getCurrentAlertLevel()
        );
    }
}
