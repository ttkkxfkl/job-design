package com.example.scheduled.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 每日任务统计
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DailyTaskStatistics {

    /**
     * 统计日期
     */
    private String date;

    /**
     * 执行任务数
     */
    private Long executedCount;

    /**
     * 成功任务数
     */
    private Long successCount;

    /**
     * 失败任务数
     */
    private Long failedCount;

    /**
     * 超时任务数
     */
    private Long timeoutCount;

    /**
     * 成功率（百分比）
     */
    private Double successRate;
}
