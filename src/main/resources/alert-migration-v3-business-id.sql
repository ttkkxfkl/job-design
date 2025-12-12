-- 报警系统 - 业务ID字段迁移脚本 (v3)
-- 为 exception_event 表添加 business_id 和 business_type 字段
-- 用于标识报警来源的业务数据

USE scheduled_task;

-- 为 exception_event 表添加业务ID相关字段
ALTER TABLE exception_event 
ADD COLUMN IF NOT EXISTS business_id VARCHAR(100) COMMENT '业务数据ID（标识报警来源于哪条业务数据）' AFTER exception_type_id,
ADD COLUMN IF NOT EXISTS business_type VARCHAR(50) COMMENT '业务类型（如：SHIFT-班次, BOREHOLE-钻孔, OPERATION-操作等）' AFTER business_id;

-- 添加索引以优化查询性能
CREATE INDEX IF NOT EXISTS idx_business_id ON exception_event(business_id);
CREATE INDEX IF NOT EXISTS idx_business_type ON exception_event(business_type);
CREATE INDEX IF NOT EXISTS idx_business_id_type ON exception_event(business_id, business_type);

-- 验证字段是否成功添加
SELECT 
    COLUMN_NAME, 
    DATA_TYPE, 
    COLUMN_TYPE, 
    COLUMN_COMMENT 
FROM INFORMATION_SCHEMA.COLUMNS 
WHERE TABLE_SCHEMA = 'scheduled_task' 
  AND TABLE_NAME = 'exception_event' 
  AND COLUMN_NAME IN ('business_id', 'business_type');

-- 说明：
-- 1. business_id: 用于存储业务数据的唯一标识，如班次ID、钻孔ID等
-- 2. business_type: 用于标识业务数据类型，方便按类型查询和统计
-- 3. 建议的业务类型枚举值：
--    - SHIFT: 班次
--    - BOREHOLE: 钻孔
--    - OPERATION: 操作记录
--    - EQUIPMENT: 设备
--    - WORKER: 工人
--    - PROJECT: 项目
--    （可根据实际业务扩展）

-- 使用示例：
-- INSERT INTO exception_event (
--     exception_type_id, 
--     business_id, 
--     business_type,
--     detected_at,
--     detection_context,
--     status,
--     current_alert_level
-- ) VALUES (
--     1,
--     'SHIFT_20251212_001',  -- 业务ID：班次唯一标识
--     'SHIFT',                -- 业务类型：班次
--     NOW(),
--     JSON_OBJECT('shiftName', '早班', 'teamId', 'TEAM_A'),
--     'ACTIVE',
--     'NONE'
-- );

-- 查询示例：
-- 1. 查询某个业务数据的所有报警
-- SELECT * FROM exception_event WHERE business_id = 'SHIFT_20251212_001';

-- 2. 查询某个业务类型的活跃报警
-- SELECT * FROM exception_event WHERE business_type = 'SHIFT' AND status = 'ACTIVE';

-- 3. 统计各业务类型的报警数量
-- SELECT business_type, COUNT(*) as alert_count 
-- FROM exception_event 
-- WHERE status = 'ACTIVE'
-- GROUP BY business_type;
