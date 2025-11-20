package com.example.scheduled.executor.impl;

import com.example.scheduled.annotation.TaskExecutorInfo;
import com.example.scheduled.entity.ScheduledTask;
import com.example.scheduled.executor.TaskExecutor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Webhook任务执行器（示例 - 预留扩展）
 * 后续可以使用RestTemplate或WebClient实现HTTP回调
 */
@Slf4j
@Component
@TaskExecutorInfo(
    taskType = ScheduledTask.TaskType.WEBHOOK,
    displayName = "回调",
    description = "调用外部 HTTP 接口"
)
public class WebhookTaskExecutor implements TaskExecutor {

    @Override
    public boolean support(ScheduledTask.TaskType taskType) {
        return taskType == ScheduledTask.TaskType.WEBHOOK;
    }

    @Override
    public void execute(ScheduledTask task) throws Exception {
        log.info("【Webhook任务执行器】准备发送HTTP回调");
        
        // TODO: 实现HTTP回调逻辑
        // String url = (String) task.getTaskData().get("url");
        // String method = (String) task.getTaskData().get("method");
        // Map<String, Object> payload = (Map<String, Object>) task.getTaskData().get("payload");
        // restTemplate.exchange(url, HttpMethod.valueOf(method), new HttpEntity<>(payload), String.class);
        
        log.info("【Webhook任务执行器】任务：{} HTTP回调完成（当前为模拟）", task.getTaskName());
    }

    @Override
    public String getName() {
        return "Webhook执行器";
    }
}
