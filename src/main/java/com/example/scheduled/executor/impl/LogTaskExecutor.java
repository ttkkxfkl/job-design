package com.example.scheduled.executor.impl;

import com.example.scheduled.annotation.TaskExecutorInfo;
import com.example.scheduled.entity.ScheduledTask;
import com.example.scheduled.executor.TaskExecutor;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 日志打印任务执行器
 */
@Slf4j
@Component
@TaskExecutorInfo(
    taskType = ScheduledTask.TaskType.LOG,
    displayName = "日志",
    description = "记录一条日志内容"
)
public class LogTaskExecutor implements TaskExecutor {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public boolean support(ScheduledTask.TaskType taskType) {
        return taskType == ScheduledTask.TaskType.LOG;
    }

    @Override
    public void execute(ScheduledTask task) throws Exception {
        log.info("===============================================");
        log.info("执行定时任务：{}", task.getTaskName());
        log.info("任务ID：{}", task.getId());
        log.info("执行时间：{}", task.getExecuteTime());

        if (task.getTaskData() != null && !task.getTaskData().isEmpty()) {
            String taskDataJson = objectMapper.writerWithDefaultPrettyPrinter()
                    .writeValueAsString(task.getTaskData());
            log.info("任务数据：\n{}", taskDataJson);
        }
        
        log.info("===============================================");
    }

    @Override
    public String getName() {
        return "日志打印执行器";
    }
}
