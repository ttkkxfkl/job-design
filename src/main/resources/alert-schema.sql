-- 报警规则系统数据库表

USE scheduled_task;

-- 1. 异常类型表：定义不同的异常类型及其检测逻辑
CREATE TABLE IF NOT EXISTS exception_type (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    name VARCHAR(255) NOT NULL UNIQUE COMMENT '异常类型名称，如：入井记录不足',
    description TEXT COMMENT '异常类型描述',
    detection_logic_type VARCHAR(50) NOT NULL COMMENT '检测逻辑类型：RECORD_CHECK-记录检查, TIME_CHECK-时间检查, CUSTOM-自定义',
    detection_config JSON COMMENT '检测配置（JSON格式），如表名、字段条件等',
    enabled BOOLEAN DEFAULT true COMMENT '是否启用',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX idx_enabled (enabled),
    INDEX idx_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='异常类型表';

-- 2. 触发条件表：定义报警的触发时机
CREATE TABLE IF NOT EXISTS trigger_condition (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    condition_type VARCHAR(50) NOT NULL COMMENT '条件类型：ABSOLUTE-固定时刻, RELATIVE-相对事件, HYBRID-混合',
    
    -- 绝对时间触发（固定时刻，如 16:00）
    absolute_time TIME COMMENT '固定触发时刻，如 16:00',
    
    -- 相对时间触发（从某个事件开始计时）
    relative_event_type VARCHAR(100) COMMENT '相对事件类型：SHIFT_START-班次开始, LAST_OPERATION-上一次操作, EXCEPTION_DETECTED-异常发现',
    relative_duration_minutes INT COMMENT '相对事件后的时间差（分钟），如 480 表示 8 小时',
    
    -- 时间窗口（只在某些时间段触发）
    time_window_start TIME COMMENT '时间窗口开始，如 09:00',
    time_window_end TIME COMMENT '时间窗口结束，如 17:00',
    
    -- 混合条件
    logical_operator VARCHAR(10) COMMENT '逻辑操作符：AND, OR',
    combined_condition_ids VARCHAR(500) COMMENT '组合的条件IDs（逗号分隔），如 "1,2,3"',
    
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX idx_condition_type (condition_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='触发条件表';

-- 3. 报警规则表：定义异常的各等级报警规则
CREATE TABLE IF NOT EXISTS alert_rule (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    exception_type_id BIGINT NOT NULL COMMENT '异常类型ID',
    level VARCHAR(20) NOT NULL COMMENT '报警等级：BLUE-蓝色, YELLOW-黄色, RED-红色',
    trigger_condition_id BIGINT NOT NULL COMMENT '触发条件ID',
    action_type VARCHAR(50) NOT NULL COMMENT '动作类型：LOG-日志, EMAIL-邮件, SMS-短信, WEBHOOK-网络钩子',
    action_config JSON COMMENT '动作配置（JSON格式），如邮件地址、SMS内容等',
    priority INT DEFAULT 5 COMMENT '优先级（0-10，数字越大优先级越高）',
    enabled BOOLEAN DEFAULT true COMMENT '是否启用',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    FOREIGN KEY (exception_type_id) REFERENCES exception_type(id),
    FOREIGN KEY (trigger_condition_id) REFERENCES trigger_condition(id),
    UNIQUE KEY uk_exception_level (exception_type_id, level),
    INDEX idx_enabled (enabled),
    INDEX idx_level (level)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='报警规则表';

-- 4. 异常事件表：记录检测到的异常及其当前状态
CREATE TABLE IF NOT EXISTS exception_event (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    exception_type_id BIGINT NOT NULL COMMENT '异常类型ID',
    detected_at DATETIME NOT NULL COMMENT '异常发现时刻',
    detection_context JSON COMMENT '检测上下文信息（班次、操作人、班组等）',
    current_alert_level VARCHAR(20) DEFAULT 'NONE' COMMENT '当前报警等级：NONE-无, BLUE-蓝色, YELLOW-黄色, RED-红色',
    last_escalated_at DATETIME COMMENT '最后一次升级的时刻',
    resolved_at DATETIME COMMENT '异常解决时刻',
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' COMMENT '异常状态：ACTIVE-活跃, RESOLVED-已解决, SUPPRESSED-已抑制',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    FOREIGN KEY (exception_type_id) REFERENCES exception_type(id),
    INDEX idx_status (status),
    INDEX idx_current_alert_level (current_alert_level),
    INDEX idx_detected_at (detected_at),
    INDEX idx_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='异常事件表';

-- 5. 报警事件日志表：记录每次报警的触发情况
CREATE TABLE IF NOT EXISTS alert_event_log (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    exception_event_id BIGINT NOT NULL COMMENT '异常事件ID',
    alert_rule_id BIGINT NOT NULL COMMENT '报警规则ID',
    triggered_at DATETIME NOT NULL COMMENT '报警触发时刻',
    alert_level VARCHAR(20) NOT NULL COMMENT '报警等级',
    trigger_reason TEXT COMMENT '触发原因（用于审计）',
    action_status VARCHAR(20) DEFAULT 'PENDING' COMMENT '动作执行状态：PENDING-待执行, SENT-已发送, FAILED-失败',
    action_error_message TEXT COMMENT '动作执行失败的错误信息',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    FOREIGN KEY (exception_event_id) REFERENCES exception_event(id),
    FOREIGN KEY (alert_rule_id) REFERENCES alert_rule(id),
    INDEX idx_exception_event_id (exception_event_id),
    INDEX idx_alert_rule_id (alert_rule_id),
    INDEX idx_triggered_at (triggered_at),
    INDEX idx_alert_level (alert_level)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='报警事件日志表';

-- 创建索引优化查询
CREATE INDEX idx_alert_rule_exception_type_enabled 
ON alert_rule(exception_type_id, enabled);

CREATE INDEX idx_exception_event_type_status 
ON exception_event(exception_type_id, status);
