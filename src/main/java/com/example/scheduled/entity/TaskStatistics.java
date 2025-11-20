package com.example.scheduled.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 任务统计信息
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TaskStatistics {

    /**
     * 待执行任务数
     */
    private Long pendingCount;

    /**
     * 执行中任务数
     */
    private Long executingCount;

    /**
     * 成功任务数
     */
    private Long successCount;

    /**
     * 失败任务数
     */
    private Long failedCount;

    /**
     * 已取消任务数
     */
    private Long cancelledCount;

    /**
     * 已暂停任务数
     */
    private Long pausedCount;

    /**
     * 超时任务数
     */
    private Long timeoutCount;

    /**
     * 任务总数
     */
    private Long totalCount;

    /**
     * 成功率（百分比）
     */
    private Double successRate;

    /**
     * 平均执行时长（毫秒）
     */
    private Double avgExecutionDuration;

    /**
     * 最长执行时长（毫秒）
     */
    private Long maxExecutionDuration;

    /**
     * 最短执行时长（毫秒）
     */
    private Long minExecutionDuration;
}
