# ALERT 系统数据库结构与示例

本文档汇总当前项目中的核心数据库表结构、字段说明，以及典型数据示例。涉及两类库：
- 业务库：存储告警事件、告警规则、异常类型、审计日志，以及调度任务
- Quartz 库：由 Quartz 管理的 Job/Trigger 表（参考）

> 注：`scheduled_task` 与 `task_execution_log` 在 [src/main/resources/schema.sql](../src/main/resources/schema.sql) 中提供了 DDL；告警相关表根据实体与服务使用约定，采用 MySQL JSON 存储灵活配置，下面给出推荐 DDL 草案与示例。

---

## 1. 业务库：告警相关表

### 1.1 `exception_event` 异常事件表
- 用途：记录系统检测到的异常实例及其生命周期状态
- 推荐 DDL（MySQL 8+）：
```sql
CREATE TABLE IF NOT EXISTS exception_event (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  exception_type_id BIGINT NOT NULL COMMENT '异常类型ID',
  business_id VARCHAR(100) NULL COMMENT '业务数据ID（标识报警来源于哪条业务数据）',
  business_type VARCHAR(50) NULL COMMENT '业务类型（如：SHIFT-班次, BOREHOLE-钻孔等）',
  status VARCHAR(20) NOT NULL COMMENT 'ACTIVE/RESOLVING/RESOLVED',
  current_alert_level VARCHAR(32) NOT NULL DEFAULT 'NONE' COMMENT '当前报警等级',
  detected_at DATETIME NOT NULL COMMENT '首次检测时间',
  resolved_at DATETIME NULL COMMENT '解除时间',
  resolution_reason VARCHAR(255) NULL COMMENT '解除原因',
  resolution_source VARCHAR(64) NULL COMMENT '解除来源：MANUAL/AUTO',
  last_escalated_at DATETIME NULL COMMENT '最近升级时间',
  recovery_flag TINYINT(1) NOT NULL DEFAULT 0 COMMENT '【已废弃】原用于标记已恢复',
  detection_context JSON NULL COMMENT '检测上下文（事件时间等）',
  pending_escalations JSON NULL COMMENT '待机升级状态映射',
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  INDEX idx_status (status),
  INDEX idx_type (exception_type_id),
  INDEX idx_detected (detected_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

**表结构可视化：**

| 字段名 | 类型 | 是否必填 | 默认值 | 说明 |
|--------|------|----------|--------|------|
| id | BIGINT | 是(PK) | AUTO | 主键 |
| exception_type_id | BIGINT | 是 | - | 异常类型ID，外键关联 exception_type |
| business_id | VARCHAR(100) | 否 | NULL | 业务数据ID，标识报警来源的业务数据唯一标识 |
| business_type | VARCHAR(50) | 否 | NULL | 业务类型，如：SHIFT(班次)/BOREHOLE(钻孔)/OPERATION(操作) |
| status | VARCHAR(20) | 是 | - | 事件状态：ACTIVE/RESOLVING/RESOLVED |
| current_alert_level | VARCHAR(32) | 是 | 'NONE' | 当前报警等级：NONE/LEVEL_1/LEVEL_2 |
| detected_at | DATETIME | 是 | - | 首次检测到异常的时间 |
| resolved_at | DATETIME | 否 | NULL | 异常解除时间 |
| resolution_reason | VARCHAR(255) | 否 | NULL | 解除原因描述 |
| resolution_source | VARCHAR(64) | 否 | NULL | 解除来源：MANUAL_RESOLUTION/AUTO_RECOVERY |
| last_escalated_at | DATETIME | 否 | NULL | 最近一次升级时间 |
| recovery_flag | TINYINT(1) | 是 | 0 | 【已废弃】原用于标记启动恢复，现改用 pending_escalations 状态判断 |
| detection_context | JSON | 否 | NULL | 检测上下文，存储外部事件时间等 |
| pending_escalations | JSON | 否 | NULL | 待机升级状态映射，记录等待中的升级 |
| created_at | DATETIME | 是 | CURRENT_TIMESTAMP | 创建时间 |
| updated_at | DATETIME | 是 | CURRENT_TIMESTAMP | 更新时间 |

**索引说明：**
- `idx_status`: 按状态快速查询活跃异常
- `idx_type`: 按异常类型过滤
- `idx_detected`: 按检测时间排序
- 字段示例：
```json
{
  "id": 100,
  "exception_type_id": 1,
  "business_id": "SHIFT_20251212_001",
  "business_type": "SHIFT",
  "status": "ACTIVE",
  "current_alert_level": "LEVEL_1",
  "detected_at": "2025-12-12T08:00:00",
  "last_escalated_at": "2025-12-12T08:30:00",
  "detection_context": {
    "FIRST_BOREHOLE_START_time": "2025-12-12T10:00:00"
  },
  "pending_escalations": {
    "LEVEL_2": {
      "status": "READY",
      "readyAt": "2025-12-12T10:00:00",
      "scheduledTime": "2025-12-12T12:00:00",
      "taskId": "12345",
      "dependencies": [
        {"eventType": "FIRST_BOREHOLE_START", "delayMinutes": 120, "required": true}
      ],
      "logicalOperator": "AND"
    }
  }
}
```

### 1.2 `alert_rule` 报警规则表
- 用途：定义各级报警的触发条件、依赖事件与动作类型
- 推荐 DDL：
```sql
CREATE TABLE IF NOT EXISTS alert_rule (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  exception_type_id BIGINT NOT NULL COMMENT '关联异常类型',
  level VARCHAR(32) NOT NULL COMMENT 'LEVEL_1/LEVEL_2/... 或 RED/ORANGE/YELLOW/BLUE',
  enabled TINYINT(1) NOT NULL DEFAULT 1,
  org_scope VARCHAR(128) NULL COMMENT '适用机构，如省/市/县',
  priority INT NOT NULL DEFAULT 5,
  action_type VARCHAR(64) NULL COMMENT '动作类型：LOG/EMAIL/SMS/...',
  trigger_condition JSON NOT NULL COMMENT '触发时间条件（组合）',
  dependent_events JSON NULL COMMENT '依赖事件配置',
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  INDEX idx_type_level (exception_type_id, level),
  INDEX idx_enabled (enabled)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

**表结构可视化：**

| 字段名 | 类型 | 是否必填 | 默认值 | 说明 |
|--------|------|----------|--------|------|
| id | BIGINT | 是(PK) | AUTO | 主键 |
| exception_type_id | BIGINT | 是 | - | 关联的异常类型ID |
| level | VARCHAR(32) | 是 | - | 报警等级：LEVEL_1/LEVEL_2 或 BLUE/YELLOW/RED |
| enabled | TINYINT(1) | 是 | 1 | 是否启用：1=启用，0=禁用 |
| org_scope | VARCHAR(128) | 否 | NULL | 适用机构范围，如省/市/县 |
| priority | INT | 是 | 5 | 优先级，数字越大优先级越高 |
| action_type | VARCHAR(64) | 否 | NULL | 动作类型：LOG/EMAIL/SMS/WEBHOOK |
| trigger_condition | JSON | 是 | - | **触发条件**：计算何时执行评估任务的时间规则 |
| dependent_events | JSON | 否 | NULL | **依赖事件**：判断是否可以升级的前置条件 |
| created_at | DATETIME | 是 | CURRENT_TIMESTAMP | 创建时间 |
| updated_at | DATETIME | 是 | CURRENT_TIMESTAMP | 更新时间 |

**索引说明：**
- `idx_type_level`: 按异常类型和等级组合查询
- `idx_enabled`: 快速过滤启用的规则

**重要字段说明：**

### trigger_condition（触发条件） vs dependent_events（依赖事件）

这两个字段容易混淆，但作用完全不同：

| 对比维度 | trigger_condition | dependent_events |
|---------|------------------|------------------|
| **作用** | 计算**何时评估**这个规则 | 判断**能否升级**到这个等级 |
| **影响** | 决定 ScheduledTask 的执行时间 | 决定报警是否真正触发 |
| **必填性** | 必填 | 可选 |
| **处理时机** | 创建规则/事件发生时 | 评估任务执行时 |

**trigger_condition 示例**（计算评估时间）：
```json
{
  "relation": "AND",
  "items": [
    {
      "operator": ">", 
      "source": "业务事件", 
      "field": "探水计划首次开始时间", 
      "offsetValue": 16, 
      "offsetUnit": "小时"
    }
  ]
}
```
→ 含义：在"探水计划首次开始"**之后16小时**创建评估任务

**dependent_events 示例**（判断能否触发）：
```json
{
  "logicalOperator": "AND",
  "events": [
    {
      "eventType": "FIRST_BOREHOLE_START", 
      "delayMinutes": 120, 
      "required": true
    }
  ]
}
```
→ 含义：必须**等到** "第一个钻孔开始" 事件发生**且过了120分钟**，才能触发此等级报警

**配合使用示例**：
```json
{
  "trigger_condition": {
    "type": "RELATIVE",
    "eventType": "FIRST_BOREHOLE_START",
    "offsetMinutes": 120
  },
  "dependent_events": {
    "events": [
      {"eventType": "FIRST_BOREHOLE_START", "delayMinutes": 120}
    ]
  }
}
```
→ 工作流程：
1. 等待 FIRST_BOREHOLE_START 事件发生（dependent_events 检查）
2. 事件发生后，根据 trigger_condition 计算时间：事件时间 + 120分钟
3. 在计算出的时间点执行评估任务
4. 执行时再次检查 dependent_events 是否满足（双重保险）

### 1.3 `exception_type` 异常类型表
- 用途：定义异常的检测逻辑类型与参数配置
- 推荐 DDL：
```sql
CREATE TABLE IF NOT EXISTS exception_type (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  name VARCHAR(128) NOT NULL,
  detection_logic_type VARCHAR(64) NOT NULL COMMENT '检测逻辑枚举，如 RECORD_CHECK',
  detection_config JSON NOT NULL COMMENT '检测参数：表名、时间窗、阈值等',
  enabled TINYINT(1) NOT NULL DEFAULT 1,
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  INDEX idx_name (name),
  INDEX idx_enabled (enabled)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

**表结构可视化：**

| 字段名 | 类型 | 是否必填 | 默认值 | 说明 |
|--------|------|----------|--------|------|
| id | BIGINT | 是(PK) | AUTO | 主键 |
| name | VARCHAR(128) | 是 | - | 异常类型名称，如"入井记录不足" |
| detection_logic_type | VARCHAR(64) | 是 | - | 检测逻辑类型：RECORD_CHECK/THRESHOLD_CHECK/CUSTOM |
| detection_config | JSON | 是 | - | 检测参数配置：表名、字段、时间窗、阈值等 |
| enabled | TINYINT(1) | 是 | 1 | 是否启用：1=启用，0=禁用 |
| created_at | DATETIME | 是 | CURRENT_TIMESTAMP | 创建时间 |
| updated_at | DATETIME | 是 | CURRENT_TIMESTAMP | 更新时间 |

**索引说明：**
- `idx_name`: 按异常类型名称查询
- `idx_enabled`: 快速过滤启用的类型
- `detection_config` 示例：
```json
{
  "tableName": "work_log",
  "dateField": "entry_time",
  "duration": "3h",
  "minCount": 0
}
```

### 1.4 `alert_event_log` 报警事件审计表
- 用途：记录每次触发、升级、解除的审计日志
- 推荐 DDL：
```sql
CREATE TABLE IF NOT EXISTS alert_event_log (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  exception_event_id BIGINT NOT NULL,
  alert_rule_id BIGINT NULL,
  alert_level VARCHAR(32) NULL,
  event_type VARCHAR(64) NOT NULL COMMENT 'ALERT_TRIGGERED/ALERT_RESOLVED/...',
  triggered_at DATETIME NOT NULL,
  trigger_reason VARCHAR(255) NULL,
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  INDEX idx_event (exception_event_id),
  INDEX idx_rule (alert_rule_id),
  INDEX idx_time (triggered_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

**表结构可视化：**

| 字段名 | 类型 | 是否必填 | 默认值 | 说明 |
|--------|------|----------|--------|------|
| id | BIGINT | 是(PK) | AUTO | 主键 |
| exception_event_id | BIGINT | 是 | - | 关联的异常事件ID |
| alert_rule_id | BIGINT | 否 | NULL | 关联的报警规则ID（解除时可为空）|
| alert_level | VARCHAR(32) | 否 | NULL | 报警等级，如 LEVEL_1/LEVEL_2 |
| event_type | VARCHAR(64) | 是 | - | 事件类型：ALERT_TRIGGERED/ALERT_RESOLVED/TASK_CANCELLED |
| triggered_at | DATETIME | 是 | - | 事件发生时间 |
| trigger_reason | VARCHAR(255) | 否 | NULL | 触发或解除原因描述 |
| created_at | DATETIME | 是 | CURRENT_TIMESTAMP | 日志记录时间 |

**索引说明：**
- `idx_event`: 按异常事件查询所有相关日志
- `idx_rule`: 按规则查询触发历史
- `idx_time`: 按时间范围统计和分析
- 示例记录：
```json
{
  "exception_event_id": 100,
  "alert_rule_id": 1,
  "alert_level": "LEVEL_1",
  "event_type": "ALERT_TRIGGERED",
  "triggered_at": "2025-12-12T08:30:00",
  "trigger_reason": "业务检测通过，时间条件满足"
}
```

---

## 2. 业务库：调度任务表（已存在 DDL）

### 2.1 `scheduled_task`
- DDL 见 [src/main/resources/schema.sql](../src/main/resources/schema.sql)
- 任务数据 `task_data` 示例：
```json
{
  "exceptionEventId": 100,
  "alertRuleId": 2,
  "evaluationType": "ALERT_EVALUATION"
}
```

### 2.2 `task_execution_log`
- 审计执行日志，DDL 见 [src/main/resources/schema.sql](../src/main/resources/schema.sql)

---

## 3. Quartz 表（参考）
- 参考脚本：[src/main/resources/quartz-schema.sql](../src/main/resources/quartz-schema.sql)
- 关键表：`QRTZ_JOB_DETAILS`, `QRTZ_TRIGGERS`, `QRTZ_CRON_TRIGGERS`, `QRTZ_FIRED_TRIGGERS` 等
- 由 Spring Quartz 自动管理，无需业务代码手工操作

---

## 4. 示例数据与典型操作

### 4.1 插入异常类型
```sql
INSERT INTO exception_type(name, detection_logic_type, detection_config, enabled)
VALUES (
  '入井记录不足',
  'RECORD_CHECK',
  JSON_OBJECT('tableName','work_log','dateField','entry_time','duration','3h','minCount',0),
  1
);
```

**插入后数据示例：**

| id | name | detection_logic_type | detection_config | enabled |
|----|------|---------------------|------------------|---------|
| 1 | 入井记录不足 | RECORD_CHECK | {"tableName":"work_log","dateField":"entry_time","duration":"3h","minCount":0} | 1 |

### 4.2 插入报警规则（LEVEL_1 无依赖）
```sql
INSERT INTO alert_rule(exception_type_id, level, enabled, priority, action_type, trigger_condition)
VALUES (
  1, 'LEVEL_1', 1, 5, 'LOG',
  JSON_OBJECT(
    'relation','AND',
    'items', JSON_ARRAY(
      JSON_OBJECT('operator','>','source','业务事件','field','探水计划首次开始时间','offsetValue',30,'offsetUnit','分钟')
    )
  )
);
```

**插入后数据示例：**

| id | exception_type_id | level | enabled | priority | action_type | trigger_condition | dependent_events |
|----|-------------------|-------|---------|----------|-------------|-------------------|------------------|
| 1 | 1 | LEVEL_1 | 1 | 5 | LOG | {"relation":"AND","items":[{"operator":">","source":"业务事件","field":"探水计划首次开始时间","offsetValue":30,"offsetUnit":"分钟"}]} | NULL |

### 4.3 插入报警规则（LEVEL_2 有依赖）
```sql
INSERT INTO alert_rule(exception_type_id, level, enabled, priority, action_type, trigger_condition, dependent_events)
VALUES (
  1, 'LEVEL_2', 1, 6, 'EMAIL',
  JSON_OBJECT('relation','AND','items', JSON_ARRAY()),
  JSON_OBJECT(
    'logicalOperator','AND',
    'events', JSON_ARRAY(JSON_OBJECT('eventType','FIRST_BOREHOLE_START','delayMinutes',120,'required',true))
  )
);
```

**插入后数据示例：**

| id | exception_type_id | level | enabled | priority | action_type | trigger_condition | dependent_events |
|----|-------------------|-------|---------|----------|-------------|-------------------|------------------|
| 2 | 1 | LEVEL_2 | 1 | 6 | EMAIL | {"relation":"AND","items":[]} | {"logicalOperator":"AND","events":[{"eventType":"FIRST_BOREHOLE_START","delayMinutes":120,"required":true}]} |

### 4.4 报告异常事件
```sql
INSERT INTO exception_event(exception_type_id, status, current_alert_level, detected_at, detection_context)
VALUES (
  1, 'ACTIVE', 'NONE', NOW(), JSON_OBJECT()
);
```

**插入后数据示例：**

| id | exception_type_id | status | current_alert_level | highest_alert_level | detected_at | resolved_at | detection_context | pending_escalations |
|----|-------------------|--------|---------------------|---------------------|-------------|-------------|-------------------|---------------------|
| 100 | 1 | ACTIVE | NONE | NONE | 2025-12-12 08:00:00 | NULL | {} | {} |

### 4.5 记录报警触发日志
```sql
INSERT INTO alert_event_log(exception_event_id, alert_rule_id, alert_level, event_type, triggered_at, trigger_reason)
VALUES (100, 1, 'LEVEL_1', 'ALERT_TRIGGERED', '2025-12-12 08:30:00', '业务检测通过，时间条件满足');
```

**插入后数据示例：**

| id | exception_event_id | alert_rule_id | alert_level | event_type | triggered_at | trigger_reason | created_at |
|----|-------------------|---------------|-------------|------------|-------------|----------------|------------|
| 1 | 100 | 1 | LEVEL_1 | ALERT_TRIGGERED | 2025-12-12 08:30:00 | 业务检测通过，时间条件满足 | 2025-12-12 08:30:00 |

---

## 5. 设计要点与约定
- JSON 字段统一：采用 MySQL 8 JSON 类型，前后端统一键名（`detection_context`, `pending_escalations`, `trigger_condition`, `dependent_events`）。
- 时间单位与偏移：前端可选择“分钟/小时/天”，后端存储时建议转换为分钟或保留原单位但在服务层统一解释。
- 索引建议：对高频查询字段（状态、类型、时间）建立索引；JSON 字段可按需增设生成列实现二级索引。
- 审计与恢复：事件状态迁移（ACTIVE→RESOLVING→RESOLVED）以及任务取消应有日志；启动恢复基于 `pending_escalations` 状态（WAITING/READY）判断，不再依赖 `recovery_flag`。
### pending_escalations 字段详细结构
```json
{
  "LEVEL_N": {
    "status": "WAITING | READY | COMPLETED",
    "createdAt": "待机创建时间",
    "readyAt": "依赖满足时间（status=READY时设置）",
    "scheduledTime": "计划执行时间（考虑延迟）",
    "taskId": "调度系统中的任务ID（用于取消任务）",
    "dependencies": [
      {
        "eventType": "事件类型",
        "delayMinutes": 120,
        "required": true
      }
    ],
    "logicalOperator": "AND | OR"
  }
}
```

**字段说明**:
- `taskId`: 由 AlertEscalationService 创建任务后写入，用于系统恢复时取消旧任务
- `scheduledTime`: AlertDependencyManager 计算的实际执行时间，包含事件时间+延迟
- `readyAt`: 标记依赖满足的时刻，用于审计和追踪
---

如需我把这些 DDL 直接落到 `schema.sql` 并提供 Flyway/Liquibase 脚本，我可以继续集成，并补充生成列索引示例（例如对 `alert_rule.level`、`exception_event.status` 的组合索引与 JSON Path 生成列）。
