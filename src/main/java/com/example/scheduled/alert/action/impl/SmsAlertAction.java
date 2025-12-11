package com.example.scheduled.alert.action.impl;

import com.example.scheduled.alert.action.AlertActionExecutor;
import com.example.scheduled.alert.entity.AlertRule;
import com.example.scheduled.alert.entity.ExceptionEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 短信报警动作执行器
 */
@Slf4j
@Component
public class SmsAlertAction implements AlertActionExecutor {

    @Override
    public void execute(Map<String, Object> actionConfig, ExceptionEvent event, AlertRule rule) throws Exception {
        if (actionConfig == null) {
            log.warn("短信配置为空");
            return;
        }

        @SuppressWarnings("unchecked")
        List<String> phoneNumbers = (List<String>) actionConfig.get("phone_numbers");
        String messageTemplate = (String) actionConfig.get("message_template");

        if (phoneNumbers == null || phoneNumbers.isEmpty()) {
            log.warn("短信收件人列表为空");
            return;
        }

        String smsContent = buildSmsContent(event, rule, messageTemplate);

        log.info("【短信报警】发送到: {} | 内容: {}", 
            String.join(",", phoneNumbers), smsContent);

        // TODO: 调用短信服务发送短信
        // smsService.send(phoneNumbers, smsContent);
    }

    @Override
    public boolean supports(String actionType) {
        return "SMS".equalsIgnoreCase(actionType);
    }

    @Override
    public String getActionType() {
        return "SMS";
    }

    private String buildSmsContent(ExceptionEvent event, AlertRule rule, String template) {
        if (template == null) {
            template = "异常报警：%s级别异常，异常ID: %d";
        }
        return String.format(template, rule.getLevel(), event.getId());
    }
}
