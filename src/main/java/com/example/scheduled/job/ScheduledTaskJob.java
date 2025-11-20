package com.example.scheduled.job;

import com.example.scheduled.scheduler.TaskScheduler;
import lombok.extern.slf4j.Slf4j;
import org.quartz.JobDataMap;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.springframework.scheduling.quartz.QuartzJobBean;
import org.springframework.stereotype.Component;

/**
 * Quartz 任务执行 Job
 * 包装实际的任务调度逻辑
 */
@Slf4j
@Component
public class ScheduledTaskJob extends QuartzJobBean {

    private final TaskScheduler taskScheduler;

    public ScheduledTaskJob(TaskScheduler taskScheduler) {
        this.taskScheduler = taskScheduler;
    }

    @Override
    protected void executeInternal(JobExecutionContext context) throws JobExecutionException {
        JobDataMap dataMap = context.getJobDetail().getJobDataMap();
        Long taskId = dataMap.getLong("taskId");
        
        log.debug("Quartz 触发任务执行，任务ID：{}", taskId);
        
        try {
            taskScheduler.executeTask(taskId);
        } catch (Exception e) {
            log.error("Quartz 执行任务失败，任务ID：{}", taskId, e);
            throw new JobExecutionException(e);
        }
    }
}
