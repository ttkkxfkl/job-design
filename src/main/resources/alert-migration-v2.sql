-- 报警系统 - 版本更新脚本
-- 为 exception_event 表添加报警解除和恢复机制相关字段
-- 为 alert_event_log 表扩展记录更多事件类型

USE scheduled_task;

-- 1. 为 exception_event 表添加新字段
ALTER TABLE exception_event 
ADD COLUMN IF NOT EXISTS status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' COMMENT '异常状态：ACTIVE-活跃, RESOLVING-解除中, RESOLVED-已解除' AFTER current_alert_level,
ADD COLUMN IF NOT EXISTS resolved_at DATETIME COMMENT '异常解除时刻' AFTER last_escalated_at,
ADD COLUMN IF NOT EXISTS resolution_reason VARCHAR(500) COMMENT '解除原因描述' AFTER resolved_at,
ADD COLUMN IF NOT EXISTS resolution_source VARCHAR(50) COMMENT '解除来源：MANUAL_RESOLUTION-手动解除, AUTO_RECOVERY-自动恢复, SYSTEM_CANCEL-系统取消' AFTER resolution_reason,
ADD COLUMN IF NOT EXISTS recovery_flag BOOLEAN DEFAULT false COMMENT '启动恢复标志：true表示已在启动时恢复过' AFTER resolution_source,
ADD COLUMN IF NOT EXISTS pending_escalations JSON COMMENT '待机升级状态JSON：记录各等级的等待状态' AFTER detection_context,
ADD INDEX IF NOT EXISTS idx_status (status),
ADD INDEX IF NOT EXISTS idx_resolved_at (resolved_at),
ADD INDEX IF NOT EXISTS idx_recovery_flag (recovery_flag);

-- 2. 为 alert_event_log 表扩展event_type字段，支持记录更多事件类型
ALTER TABLE alert_event_log 
ADD COLUMN IF NOT EXISTS event_type VARCHAR(50) DEFAULT 'ALERT_TRIGGERED' COMMENT '事件类型：ALERT_TRIGGERED-报警触发, ALERT_ESCALATED-报警升级, ALERT_RESOLVED-报警解除, TASK_CANCELLED-任务取消, SYSTEM_RECOVERY-系统恢复' AFTER alert_level,
ADD INDEX IF NOT EXISTS idx_event_type (event_type);

-- 3. 创建 alert_rule 表的 dependent_events 字段（如果还没有的话）
-- 注：这个字段应该在之前的设计中已经存在，这里只是确保
ALTER TABLE alert_rule
ADD COLUMN IF NOT EXISTS dependent_events JSON COMMENT '依赖事件配置JSON：格式为{events: [{eventType: "...", delayMinutes: 0, required: true}], logicalOperator: "AND"}' AFTER action_config;

-- 4. 创建索引优化查询性能
CREATE INDEX IF NOT EXISTS idx_exception_event_status_created 
ON exception_event(status, created_at);

CREATE INDEX IF NOT EXISTS idx_exception_event_active_recovery
ON exception_event(status, recovery_flag) 
WHERE status = 'ACTIVE' AND recovery_flag = false;

-- 5. 为了支持系统启动时的恢复，需要查询所有未恢复的活跃异常
-- 该查询会被 AlertRecoveryService 使用
-- SELECT * FROM exception_event 
-- WHERE status = 'ACTIVE' AND recovery_flag = false 
-- ORDER BY created_at ASC;
