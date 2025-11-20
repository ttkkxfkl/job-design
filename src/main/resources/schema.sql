-- 创建数据库
CREATE DATABASE IF NOT EXISTS scheduled_task 
CHARACTER SET utf8mb4 
COLLATE utf8mb4_unicode_ci;

USE scheduled_task;

-- 定时任务表
CREATE TABLE IF NOT EXISTS scheduled_task (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    task_name VARCHAR(255) NOT NULL COMMENT '任务名称',
    task_type VARCHAR(50) NOT NULL DEFAULT 'LOG' COMMENT '任务类型：LOG-日志打印, EMAIL-邮件, SMS-短信, WEBHOOK-HTTP回调等',
    schedule_mode VARCHAR(20) NOT NULL DEFAULT 'ONCE' COMMENT '调度模式：ONCE-一次性定时, CRON-周期性调度',
    execute_time DATETIME COMMENT '执行时间（ONCE模式使用）',
    cron_expression VARCHAR(100) COMMENT 'Cron表达式（CRON模式使用）',
    priority INT DEFAULT 5 COMMENT '任务优先级：0-10，数值越大优先级越高，默认5',
    execution_timeout BIGINT DEFAULT 300 COMMENT '任务执行超时时间（秒），默认300秒（5分钟）',
    task_data JSON COMMENT '任务数据（JSON格式存储）',
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING' COMMENT '任务状态：PENDING-待执行, EXECUTING-执行中, SUCCESS-成功, FAILED-失败, CANCELLED-已取消, PAUSED-已暂停, TIMEOUT-超时',
    retry_count INT DEFAULT 0 COMMENT '已重试次数',
    max_retry_count INT DEFAULT 3 COMMENT '最大重试次数',
    last_execute_time DATETIME COMMENT '最后执行时间',
    error_message TEXT COMMENT '错误信息',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX idx_execute_time (execute_time),
    INDEX idx_status (status),
    INDEX idx_task_type (task_type),
    INDEX idx_schedule_mode (schedule_mode),
    INDEX idx_priority (priority),
    INDEX idx_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='定时任务表';

-- 任务执行历史表（可选，用于审计）
CREATE TABLE IF NOT EXISTS task_execution_log (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    task_id BIGINT NOT NULL COMMENT '任务ID',
    execute_time DATETIME NOT NULL COMMENT '执行时间',
    status VARCHAR(20) NOT NULL COMMENT '执行状态',
    error_message TEXT COMMENT '错误信息',
    execution_duration_ms BIGINT COMMENT '执行耗时（毫秒）',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    INDEX idx_task_id (task_id),
    INDEX idx_execute_time (execute_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='任务执行历史表';
