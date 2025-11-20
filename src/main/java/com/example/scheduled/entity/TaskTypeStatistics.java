package com.example.scheduled.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 任务类型分布统计
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TaskTypeStatistics {

    /**
     * 任务类型
     */
    private ScheduledTask.TaskType taskType;

    /**
     * 任务数量
     */
    private Long count;

    /**
     * 占比（百分比）
     */
    private Double percentage;
}
