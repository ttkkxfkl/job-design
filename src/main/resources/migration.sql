-- 数据库迁移脚本：添加优先级和超时字段
-- 适用于已存在的 scheduled_task 表

USE scheduled_task;

-- 添加 priority 字段（如果不存在）
ALTER TABLE scheduled_task 
ADD COLUMN IF NOT EXISTS priority INT DEFAULT 5 COMMENT '任务优先级：0-10，数值越大优先级越高，默认5'
AFTER cron_expression;

-- 添加 execution_timeout 字段（如果不存在）
ALTER TABLE scheduled_task 
ADD COLUMN IF NOT EXISTS execution_timeout BIGINT DEFAULT 300 COMMENT '任务执行超时时间（秒），默认300秒（5分钟）'
AFTER priority;

-- 添加优先级索引
ALTER TABLE scheduled_task 
ADD INDEX IF NOT EXISTS idx_priority (priority);

-- 修改 status 字段的注释，添加 PAUSED 和 TIMEOUT 状态
ALTER TABLE scheduled_task 
MODIFY COLUMN status VARCHAR(20) NOT NULL DEFAULT 'PENDING' 
COMMENT '任务状态：PENDING-待执行, EXECUTING-执行中, SUCCESS-成功, FAILED-失败, CANCELLED-已取消, PAUSED-已暂停, TIMEOUT-超时';

-- 为现有任务设置默认值（如果字段为NULL）
UPDATE scheduled_task SET priority = 5 WHERE priority IS NULL;
UPDATE scheduled_task SET execution_timeout = 300 WHERE execution_timeout IS NULL;

SELECT '数据库迁移完成：已添加 priority 和 execution_timeout 字段' AS message;
