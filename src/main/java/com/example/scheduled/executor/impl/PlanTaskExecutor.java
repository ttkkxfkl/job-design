package com.example.scheduled.executor.impl;

import com.example.scheduled.annotation.TaskExecutorInfo;
import com.example.scheduled.entity.ScheduledTask;
import com.example.scheduled.executor.TaskExecutor;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 计划任务检查执行器
 */
@Slf4j
@Component
@TaskExecutorInfo(
    taskType = ScheduledTask.TaskType.PLAN,
    displayName = "计划",
    description = "执行预定义计划任务"
)
public class PlanTaskExecutor implements TaskExecutor {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public boolean support(ScheduledTask.TaskType taskType) {
        return taskType == ScheduledTask.TaskType.PLAN;
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
        return "计划任务检查执行器";
    }
}
