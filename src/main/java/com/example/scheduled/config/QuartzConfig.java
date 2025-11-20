package com.example.scheduled.config;

import org.quartz.Scheduler;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.quartz.SchedulerFactoryBean;

/**
 * Quartz 调度器配置类
 * 仅在 scheduler-type=quartz 时生效
 */
@Configuration
@ConditionalOnProperty(name = "scheduled.task.scheduler-type", havingValue = "quartz")
public class QuartzConfig {

    /**
     * 配置 Quartz Scheduler
     * Spring Boot 会自动配置 SchedulerFactoryBean，这里主要是为了确保依赖注入
     */
    @Bean
    public Scheduler scheduler(SchedulerFactoryBean schedulerFactoryBean) throws Exception {
        Scheduler scheduler = schedulerFactoryBean.getScheduler();
        scheduler.start();
        return scheduler;
    }
}
