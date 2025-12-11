package com.example.scheduled.alert.action.impl;

import com.example.scheduled.alert.action.AlertActionExecutor;
import com.example.scheduled.alert.entity.AlertRule;
import com.example.scheduled.alert.entity.ExceptionEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 邮件报警动作执行器
 */
@Slf4j
@Component
public class EmailAlertAction implements AlertActionExecutor {

    @Override
    public void execute(Map<String, Object> actionConfig, ExceptionEvent event, AlertRule rule) throws Exception {
        if (actionConfig == null) {
            log.warn("邮件配置为空");
            return;
        }

        @SuppressWarnings("unchecked")
        List<String> recipients = (List<String>) actionConfig.get("recipients");
        String subject = (String) actionConfig.get("subject");
        String template = (String) actionConfig.get("template");

        if (recipients == null || recipients.isEmpty()) {
            log.warn("邮件收件人列表为空");
            return;
        }

        String emailContent = buildEmailContent(event, rule, template);

        log.info("【邮件报警】发送到: {} | 主题: {} | 内容: {}", 
            String.join(",", recipients), subject, emailContent);

        // TODO: 调用邮件服务发送邮件
        // mailService.send(recipients, subject, emailContent);
    }

    @Override
    public boolean supports(String actionType) {
        return "EMAIL".equalsIgnoreCase(actionType);
    }

    @Override
    public String getActionType() {
        return "EMAIL";
    }

    private String buildEmailContent(ExceptionEvent event, AlertRule rule, String template) {
        if (template == null) {
            template = "发现 %s 级别异常（ID: %d），请及时处理";
        }
        return String.format(template, rule.getLevel(), event.getId());
    }
}
