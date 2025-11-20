package com.example.scheduled.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 定时任务配置属性
 */
@Data
@Component
@ConfigurationProperties(prefix = "scheduled.task")
public class ScheduledTaskProperties {

    /**
     * 调度器类型：simple（内置线程池）/ quartz（Quartz调度器）
     */
    private String schedulerType = "simple";

    /**
     * 核心线程数（仅 simple 模式使用）
     */
    private int corePoolSize = 10;

    /**
     * 最大重试次数
     */
    private int maxRetryCount = 3;

    /**
     * 重试间隔（秒）
     */
    private long retryIntervalSeconds = 60;

    /**
     * 锁类型：local（本地）/ redis（Redis）
     */
    private String lockType = "local";
}
