-- 报警规则系统 - 数据初始化示例脚本
-- 场景：入井记录不足报警规则配置

USE scheduled_task;

-- ============================================
-- 第一步：创建异常类型
-- ============================================

INSERT INTO exception_type (
    name, 
    description, 
    detection_logic_type, 
    detection_config, 
    enabled, 
    created_at, 
    updated_at
) VALUES (
    '入井记录不足',
    '班次内没有入井操作记录',
    'RECORD_CHECK',
    JSON_OBJECT(
        'table', 'work_record',
        'condition', 'shift_id = ? AND action_type = "ENTRY"'
    ),
    true,
    NOW(),
    NOW()
);

SET @exception_type_id = LAST_INSERT_ID();
SELECT @exception_type_id AS created_exception_type_id;

-- ============================================
-- 第二步：创建触发条件（3个等级）
-- ============================================

-- BLUE 等级：绝对时间触发（每天 16:00）
INSERT INTO trigger_condition (
    condition_type,
    absolute_time,
    time_window_start,
    time_window_end,
    created_at,
    updated_at
) VALUES (
    'ABSOLUTE',
    '16:00:00',
    '08:00:00',  -- 工作时间内
    '18:00:00',
    NOW(),
    NOW()
);

SET @blue_condition_id = LAST_INSERT_ID();

-- YELLOW 等级：相对事件触发（班次开始 + 8小时）
INSERT INTO trigger_condition (
    condition_type,
    relative_event_type,
    relative_duration_minutes,
    created_at,
    updated_at
) VALUES (
    'RELATIVE',
    'SHIFT_START',
    480,  -- 8小时
    NOW(),
    NOW()
);

SET @yellow_condition_id = LAST_INSERT_ID();

-- RED 等级：相对事件触发（班次开始 + 12小时）
INSERT INTO trigger_condition (
    condition_type,
    relative_event_type,
    relative_duration_minutes,
    created_at,
    updated_at
) VALUES (
    'RELATIVE',
    'SHIFT_START',
    720,  -- 12小时
    NOW(),
    NOW()
);

SET @red_condition_id = LAST_INSERT_ID();

-- ============================================
-- 第三步：创建报警规则（3个等级）
-- ============================================

-- BLUE 等级报警规则：日志输出
INSERT INTO alert_rule (
    exception_type_id,
    level,
    trigger_condition_id,
    action_type,
    action_config,
    priority,
    enabled,
    created_at,
    updated_at
) VALUES (
    @exception_type_id,
    'BLUE',
    @blue_condition_id,
    'LOG',
    JSON_OBJECT('level', 'WARN'),
    5,
    true,
    NOW(),
    NOW()
);

SET @blue_rule_id = LAST_INSERT_ID();

-- YELLOW 等级报警规则：邮件通知
INSERT INTO alert_rule (
    exception_type_id,
    level,
    trigger_condition_id,
    action_type,
    action_config,
    priority,
    enabled,
    created_at,
    updated_at
) VALUES (
    @exception_type_id,
    'YELLOW',
    @yellow_condition_id,
    'EMAIL',
    JSON_OBJECT(
        'recipients', JSON_ARRAY('supervisor@example.com', 'manager@example.com'),
        'subject', '异常预警 - 入井记录不足'
    ),
    6,
    true,
    NOW(),
    NOW()
);

SET @yellow_rule_id = LAST_INSERT_ID();

-- RED 等级报警规则：短信通知
INSERT INTO alert_rule (
    exception_type_id,
    level,
    trigger_condition_id,
    action_type,
    action_config,
    priority,
    enabled,
    created_at,
    updated_at
) VALUES (
    @exception_type_id,
    'RED',
    @red_condition_id,
    'SMS',
    JSON_OBJECT(
        'phone_numbers', JSON_ARRAY('13800138000', '13900139000'),
        'message_template', '【紧急】异常报警：%s级别异常，异常ID: %d'
    ),
    8,
    true,
    NOW(),
    NOW()
);

SET @red_rule_id = LAST_INSERT_ID();

-- ============================================
-- 验证数据已创建
-- ============================================

SELECT '===== 异常类型 =====' AS;
SELECT * FROM exception_type WHERE id = @exception_type_id;

SELECT '===== 触发条件 =====' AS;
SELECT id, condition_type, absolute_time, relative_event_type, relative_duration_minutes 
FROM trigger_condition 
WHERE id IN (@blue_condition_id, @yellow_condition_id, @red_condition_id);

SELECT '===== 报警规则 =====' AS;
SELECT id, level, trigger_condition_id, action_type, priority 
FROM alert_rule 
WHERE exception_type_id = @exception_type_id;

-- ============================================
-- 可选：创建示例异常事件（用于测试）
-- ============================================

INSERT INTO exception_event (
    exception_type_id,
    detected_at,
    detection_context,
    current_alert_level,
    status,
    created_at,
    updated_at
) VALUES (
    @exception_type_id,
    NOW(),
    JSON_OBJECT(
        'shift_id', 123,
        'shift_start_time', DATE_SUB(NOW(), INTERVAL 2 HOUR),
        'operator', '张三',
        'department', '采煤队'
    ),
    'NONE',
    'ACTIVE',
    NOW(),
    NOW()
);

SET @event_id = LAST_INSERT_ID();
SELECT '===== 创建的异常事件 =====' AS;
SELECT id, exception_type_id, detected_at, current_alert_level, status 
FROM exception_event 
WHERE id = @event_id;

-- ============================================
-- 说明
-- ============================================

/*
创建的报警规则配置说明：

异常类型：入井记录不足（ID: @exception_type_id）

三级报警升级：

1. BLUE 等级（轻度预警）
   触发条件：每天 16:00 触发
   动作：输出警告级别日志
   优先级：5

2. YELLOW 等级（中度预警）
   触发条件：班次开始后 8 小时触发
   动作：发送邮件给主管和经理
   优先级：6

3. RED 等级（严重警告）
   触发条件：班次开始后 12 小时触发
   动作：发送短信给指定人员
   优先级：8

时间轴示例（班次08:00-17:00）：

14:30  异常事件被检测到
       └─ 创建异常事件，状态为 ACTIVE

16:00  评估 BLUE 条件
       ├─ 条件满足（已过 16:00）
       ├─ 执行日志输出
       ├─ 创建 alert_event_log 记录
       └─ 为 YELLOW 创建评估任务

班次开始(08:00) + 8h = 16:00  评估 YELLOW 条件
       ├─ 条件满足（已过 8 小时）
       ├─ 发送邮件
       ├─ 创建 alert_event_log 记录
       └─ 为 RED 创建评估任务

班次开始(08:00) + 12h = 20:00  评估 RED 条件
       ├─ 条件满足（已过 12 小时）
       ├─ 发送短信
       ├─ 创建 alert_event_log 记录
       └─ 完成升级（无更高等级）

异常解决：
   执行 PUT /api/alert/event/{eventId}/resolve
   └─ 状态变更为 RESOLVED，解决时间记录

使用查询：

-- 查看异常事件的完整升级历史
SELECT * FROM alert_event_log 
WHERE exception_event_id = @event_id 
ORDER BY triggered_at ASC;

-- 查看当前活跃的异常
SELECT * FROM exception_event 
WHERE status = 'ACTIVE' 
ORDER BY detected_at DESC;

-- 查看某个异常类型的所有规则
SELECT a.*, t.condition_type, t.absolute_time, t.relative_event_type 
FROM alert_rule a
LEFT JOIN trigger_condition t ON a.trigger_condition_id = t.id
WHERE a.exception_type_id = @exception_type_id
ORDER BY FIELD(a.level, 'BLUE', 'YELLOW', 'RED');
*/
