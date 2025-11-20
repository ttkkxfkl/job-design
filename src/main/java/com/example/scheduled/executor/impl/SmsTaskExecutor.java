package com.example.scheduled.executor.impl;

import com.example.scheduled.annotation.TaskExecutorInfo;
import com.example.scheduled.entity.ScheduledTask;
import com.example.scheduled.executor.TaskExecutor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 短信任务执行器（示例 - 预留扩展）
 * 后续可以集成短信服务实现真实发送
 */
@Slf4j
@Component
@TaskExecutorInfo(
    taskType = ScheduledTask.TaskType.SMS,
    displayName = "短信",
    description = "发送短信通知"
)
public class SmsTaskExecutor implements TaskExecutor {

    @Override
    public boolean support(ScheduledTask.TaskType taskType) {
        return taskType == ScheduledTask.TaskType.SMS;
    }

    @Override
    public void execute(ScheduledTask task) throws Exception {
        log.info("【短信任务执行器】准备发送短信");
        
        // TODO: 实现短信发送逻辑
        // String phone = (String) task.getTaskData().get("phone");
        // String content = (String) task.getTaskData().get("content");
        // smsService.sendSms(phone, content);
        
        log.info("【短信任务执行器】任务：{} 短信发送完成（当前为模拟）", task.getTaskName());
    }

    @Override
    public String getName() {
        return "短信执行器";
    }
}
