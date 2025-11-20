package com.example.scheduled.executor.impl;

import com.example.scheduled.annotation.TaskExecutorInfo;
import com.example.scheduled.entity.ScheduledTask;
import com.example.scheduled.executor.TaskExecutor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 邮件任务执行器（示例 - 预留扩展）
 * 后续可以集成邮件服务实现真实发送
 */
@Slf4j
@Component
@TaskExecutorInfo(
    taskType = ScheduledTask.TaskType.EMAIL,
    displayName = "邮件",
    description = "发送邮件通知"
)
public class EmailTaskExecutor implements TaskExecutor {

    @Override
    public boolean support(ScheduledTask.TaskType taskType) {
        return taskType == ScheduledTask.TaskType.EMAIL;
    }

    @Override
    public void execute(ScheduledTask task) throws Exception {
        log.info("【邮件任务执行器】准备发送邮件");
        
        // TODO: 实现邮件发送逻辑
        // String recipient = (String) task.getTaskData().get("recipient");
        // String subject = (String) task.getTaskData().get("subject");
        // String content = (String) task.getTaskData().get("content");
        // mailService.sendMail(recipient, subject, content);
        
        log.info("【邮件任务执行器】任务：{} 邮件发送完成（当前为模拟）", task.getTaskName());
    }

    @Override
    public String getName() {
        return "邮件执行器";
    }
}
