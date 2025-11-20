package com.example.scheduled.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 任务执行历史记录
 */
@TableName("task_execution_log")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TaskExecutionLog {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long taskId;

    private LocalDateTime executeTime;

    private ScheduledTask.TaskStatus status;

    private String errorMessage;

    private Long executionDurationMs;

    private LocalDateTime createdAt;

    // 可用 @TableField(fill = FieldFill.INSERT) 实现自动填充
}
