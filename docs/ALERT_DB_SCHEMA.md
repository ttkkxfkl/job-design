# ALERT 系统数据库结构与示例

本文档汇总当前项目的核心数据库表结构、字段说明，以及详细的示例数据。包含以下数据库表：
- **任务调度表**：`scheduled_task`（定时任务），`task_execution_log`（执行历史）
- **告警系统表**：`exception_type`（异常类型）、`trigger_condition`（触发条件）、`alert_rule`（报警规则）、`exception_event`（异常事件）、`alert_event_log`（报警日志）

所有 DDL 已在项目中实现，本文档提供完整的字段说明和示例数据。

---

## 1. 任务调度表

### 1.1 `scheduled_task` 定时任务表

- **用途**：由任务调度系统管理，存储所有待执行/已执行的定时任务，包括报警评估任务、延迟任务等
- **DDL**：见 [src/main/resources/schema.sql](../src/main/resources/schema.sql#L9-L51)
- **关键字段说明**：

| 字段名 | 类型 | 示例值 | 说明 |
|--------|------|--------|------|
| `id` | BIGINT | 12345 | 主键，自增 |
| `task_name` | VARCHAR(255) | `ALERT_EVALUATION_100_2` | 任务名称，格式：`ALERT_EVALUATION_{exceptionEventId}_{ruleId}` |
| `task_type` | VARCHAR(50) | `ALERT` | 任务类型，本系统使用 `ALERT` |
| `schedule_mode` | VARCHAR(20) | `ONCE` | 调度模式，`ONCE` 表示一次性定时任务 |
| `execute_time` | DATETIME | `2025-12-12 16:30:00` | 计划执行时间，由 AlertEscalationService 计算填充 |
| `priority` | INT | 5 | 优先级（0-10），报警任务通常为 8-10 |
| `execution_timeout` | BIGINT | 300 | 执行超时（秒），报警任务默认 300 秒 |
| `task_data` | JSON | 见下表示例 | **任务参数**，包含 exceptionEventId、alertRuleId/levelName 等 |
| `status` | VARCHAR(20) | `PENDING` | 任务状态：`PENDING`/`EXECUTING`/`SUCCESS`/`FAILED`/`CANCELLED`/`TIMEOUT` |
| `retry_count` | INT | 0 | 已重试次数 |
| `max_retry_count` | INT | 3 | 最大重试次数，报警任务通常 1-2 次 |
| `last_execute_time` | DATETIME | `2025-12-12 16:30:05` | 最后执行时间，第一次执行后更新 |
| `error_message` | TEXT | `Connection timeout` | 执行失败的错误信息 |
| `created_at` | DATETIME | `2025-12-12 10:02:00` | 任务创建时间 |
| `updated_at` | DATETIME | `2025-12-12 16:30:10` | 任务更新时间 |

**task_data JSON 示例**：
```json
{
  "exceptionEventId": 100,
  "alertRuleId": 2,
  "levelName": "LEVEL_2"
}
```

**完整记录示例**：
```json
{
  "id": 12345,
  "task_name": "ALERT_EVALUATION_100_2",
  "task_type": "ALERT",
  "schedule_mode": "ONCE",
  "execute_time": "2025-12-12 16:30:00",
  "priority": 8,
  "execution_timeout": 300,
  "task_data": {"exceptionEventId": 100, "alertRuleId": 2, "levelName": "LEVEL_2"},
  "status": "PENDING",
  "retry_count": 0,
  "max_retry_count": 3,
  "last_execute_time": null,
  "error_message": null,
  "created_at": "2025-12-12 10:02:00",
  "updated_at": "2025-12-12 10:02:00"
}
```

---

### 1.2 `task_execution_log` 任务执行历史表

- **用途**：记录每次任务执行的历史，用于审计和追踪
- **DDL**：见 [src/main/resources/schema.sql](../src/main/resources/schema.sql#L53-L72)
- **关键字段说明**：

| 字段名 | 类型 | 示例值 | 说明 |
|--------|------|--------|------|
| `id` | BIGINT | 98765 | 主键，自增 |
| `task_id` | BIGINT | 12345 | 关联的 scheduled_task ID |
| `execute_time` | DATETIME | `2025-12-12 16:30:05` | 实际执行时间 |
| `status` | VARCHAR(20) | `SUCCESS` | 执行状态：`SUCCESS`/`FAILED`/`TIMEOUT` |
| `error_message` | TEXT | null | 执行失败时的错误信息 |
| `execution_duration_ms` | BIGINT | 250 | 执行耗时（毫秒） |
| `created_at` | DATETIME | `2025-12-12 16:30:05` | 日志记录时间 |

**完整记录示例**：
```json
{
  "id": 98765,
  "task_id": 12345,
  "execute_time": "2025-12-12 16:30:05",
  "status": "SUCCESS",
  "error_message": null,
  "execution_duration_ms": 250,
  "created_at": "2025-12-12 16:30:05"
}
```

---

## 2. 告警系统表

### 2.1 `exception_type` 异常类型表

- **用途**：定义系统中支持的异常类型及其检测逻辑
- **DDL**：见 [src/main/resources/alert-schema.sql](../src/main/resources/alert-schema.sql#L8-L30)
- **关键字段说明**：

| 字段名 | 类型 | 示例值 | 说明 |
|--------|------|--------|------|
| `id` | BIGINT | 1 | 主键，自增 |
| `name` | VARCHAR(255) | `入井记录不足` | 异常类型名称，唯一 |
| `description` | TEXT | `班次内入井记录数不足指定数量` | 异常描述 |
| `detection_logic_type` | VARCHAR(50) | `RECORD_CHECK` | 检测逻辑类型：`RECORD_CHECK`(记录检查)/`TIME_CHECK`(时间检查)/`CUSTOM`(自定义) |
| `detection_config` | JSON | 见下表示例 | **检测配置**，包含表名、字段条件、时间窗、阈值等 |
| `enabled` | BOOLEAN | true | 是否启用该异常类型 |
| `created_at` | DATETIME | `2025-12-01 09:00:00` | 创建时间 |
| `updated_at` | DATETIME | `2025-12-12 10:00:00` | 更新时间 |

**detection_config JSON 示例（RECORD_CHECK）**：
```json
{
  "tableName": "work_log",
  "dateField": "entry_time",
  "duration": "3h",
  "minCount": 1,
  "conditionField": "entry_type",
  "conditionValue": "入井"
}
```

**detection_config JSON 示例（TIME_CHECK）**：
```json
{
  "startTime": "08:00",
  "endTime": "18:00",
  "checkInterval": "3h",
  "timeoutMinutes": 180
}
```

**完整记录示例**：
```json
{
  "id": 1,
  "name": "入井记录不足",
  "description": "班次内入井记录数不足指定数量，可能存在入井人员未登记",
  "detection_logic_type": "RECORD_CHECK",
  "detection_config": {
    "tableName": "work_log",
    "dateField": "entry_time",
    "duration": "3h",
    "minCount": 1,
    "conditionField": "entry_type",
    "conditionValue": "入井"
  },
  "enabled": true,
  "created_at": "2025-12-01 09:00:00",
  "updated_at": "2025-12-12 10:00:00"
}
```

---

### 2.2 `trigger_condition` 触发条件表

- **用途**：定义报警的触发时机，支持三种类型：绝对时间、相对事件、混合条件
- **DDL**：见 [src/main/resources/alert-schema.sql](../src/main/resources/alert-schema.sql#L32-L80)
- **关键字段说明**：

| 字段名 | 类型 | 示例值 | 说明 |
|--------|------|--------|------|
| `id` | BIGINT | 10 | 主键，自增 |
| `condition_type` | VARCHAR(50) | `ABSOLUTE` | 条件类型：`ABSOLUTE`(固定时刻)/`RELATIVE`(相对事件)/`HYBRID`(混合) |
| `absolute_time` | TIME | `16:00:00` | **ABSOLUTE 类型**：固定触发时刻，仅在 condition_type=ABSOLUTE 时使用 |
| `relative_event_type` | VARCHAR(100) | `FIRST_BOREHOLE_START` | **RELATIVE 类型**：相对的事件类型，如钻孔开始 |
| `relative_duration_minutes` | INT | 480 | **RELATIVE 类型**：距离事件的分钟数（如 480 = 8 小时） |
| `time_window_start` | TIME | `09:00:00` | **可选**：时间窗口开始，仅在该时间段内触发 |
| `time_window_end` | TIME | `17:00:00` | **可选**：时间窗口结束，仅在该时间段内触发 |
| `logical_operator` | VARCHAR(10) | `AND` | **HYBRID 类型**：逻辑操作符 `AND` 或 `OR` |
| `combined_condition_ids` | VARCHAR(500) | `10,11,12` | **HYBRID 类型**：组合条件 IDs（逗号分隔） |
| `created_at` | DATETIME | `2025-12-01 09:00:00` | 创建时间 |
| `updated_at` | DATETIME | `2025-12-12 10:00:00` | 更新时间 |

**完整记录示例（ABSOLUTE 类型）**：
```json
{
  "id": 10,
  "condition_type": "ABSOLUTE",
  "absolute_time": "16:00:00",
  "relative_event_type": null,
  "relative_duration_minutes": null,
  "time_window_start": null,
  "time_window_end": null,
  "logical_operator": null,
  "combined_condition_ids": null,
  "created_at": "2025-12-01 09:00:00",
  "updated_at": "2025-12-01 09:00:00"
}
```

**完整记录示例（RELATIVE 类型）**：
```json
{
  "id": 11,
  "condition_type": "RELATIVE",
  "absolute_time": null,
  "relative_event_type": "FIRST_BOREHOLE_START",
  "relative_duration_minutes": 480,
  "time_window_start": "08:00:00",
  "time_window_end": "22:00:00",
  "logical_operator": null,
  "combined_condition_ids": null,
  "created_at": "2025-12-01 09:00:00",
  "updated_at": "2025-12-01 09:00:00"
}
```

**完整记录示例（HYBRID 类型）**：
```json
{
  "id": 12,
  "condition_type": "HYBRID",
  "absolute_time": null,
  "relative_event_type": null,
  "relative_duration_minutes": null,
  "time_window_start": null,
  "time_window_end": null,
  "logical_operator": "AND",
  "combined_condition_ids": "10,11",
  "created_at": "2025-12-01 09:00:00",
  "updated_at": "2025-12-01 09:00:00"
}
```

---

### 2.3 `alert_rule` 报警规则表

- **用途**：为异常类型的各个等级定义报警规则，关联触发条件和动作
- **DDL**：见 [src/main/resources/alert-schema.sql](../src/main/resources/alert-schema.sql#L82-L116)
- **关键字段说明**：

| 字段名 | 类型 | 示例值 | 说明 |
|--------|------|--------|------|
| `id` | BIGINT | 2 | 主键，自增 |
| `exception_type_id` | BIGINT | 1 | 关联的异常类型 ID（外键） |
| `level` | VARCHAR(20) | `LEVEL_2` | 报警等级：`LEVEL_1`(轻度)/`LEVEL_2`(中度)/`LEVEL_3`(严重)；或使用 `BLUE`/`YELLOW`/`RED` |
| `trigger_condition_id` | BIGINT | 11 | 关联的触发条件 ID（外键），决定何时执行评估 |
| `action_type` | VARCHAR(50) | `EMAIL` | 动作类型：`LOG`(日志)/`EMAIL`(邮件)/`SMS`(短信)/`WEBHOOK`(网络钩子) |
| `action_config` | JSON | 见下表示例 | **动作配置**，包含邮件地址、短信内容等 |
| `priority` | INT | 6 | 优先级（0-10），数字越大优先级越高 |
| `enabled` | BOOLEAN | true | 是否启用该规则 |
| `created_at` | DATETIME | `2025-12-01 09:00:00` | 创建时间 |
| `updated_at` | DATETIME | `2025-12-12 10:00:00` | 更新时间 |

**action_config JSON 示例（EMAIL）**：
```json
{
  "recipients": ["admin@company.com", "team@company.com"],
  "subject": "入井记录不足预警 - LEVEL_2",
  "template": "alert_level_2_template"
}
```

**action_config JSON 示例（SMS）**：
```json
{
  "phoneNumbers": ["13800138000", "13900139000"],
  "content": "【告警】班次内入井记录不足，请及时处理"
}
```

**action_config JSON 示例（WEBHOOK）**：
```json
{
  "url": "https://api.company.com/alert/webhook",
  "method": "POST",
  "headers": {"Authorization": "Bearer token123"},
  "timeout": 5000
}
```

**完整记录示例（LEVEL_2 - EMAIL）**：
```json
{
  "id": 2,
  "exception_type_id": 1,
  "level": "LEVEL_2",
  "trigger_condition_id": 11,
  "action_type": "EMAIL",
  "action_config": {
    "recipients": ["admin@company.com", "team@company.com"],
    "subject": "入井记录不足预警 - LEVEL_2",
    "template": "alert_level_2_template"
  },
  "priority": 6,
  "enabled": true,
  "created_at": "2025-12-01 09:00:00",
  "updated_at": "2025-12-12 10:00:00"
}
```

---

### 2.4 `exception_event` 异常事件表
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

- 用途：记录系统检测到的异常实例及其生命周期状态
- 推荐 DDL（MySQL 8+）：
```sql
CREATE TABLE IF NOT EXISTS exception_event (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  exception_type_id BIGINT NOT NULL COMMENT '异常类型ID',
  business_id VARCHAR(100) NULL COMMENT '业务数据ID（标识报警来源于哪条业务数据）',
  business_type VARCHAR(50) NULL COMMENT '业务类型（如：SHIFT-班次, BOREHOLE-钻孔等）',
  detected_at DATETIME NOT NULL COMMENT '首次检测时间',
  detection_context JSON NULL COMMENT '检测上下文（事件时间等）',
  pending_escalations JSON NULL COMMENT '待机升级状态映射',
  current_alert_level VARCHAR(32) NOT NULL DEFAULT 'NONE' COMMENT '当前报警等级',
  last_escalated_at DATETIME NULL COMMENT '最近升级时间',
  resolved_at DATETIME NULL COMMENT '解除时间',
  status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE/RESOLVING/RESOLVED',
  resolution_reason VARCHAR(255) NULL COMMENT '解除原因',
  resolution_source VARCHAR(64) NULL COMMENT '解除来源',
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  INDEX idx_status (status),
  INDEX idx_type (exception_type_id),
  INDEX idx_detected (detected_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

- **关键字段说明**：

| 字段名 | 类型 | 示例值 | 说明 |
|--------|------|--------|------|
| `id` | BIGINT | 100 | 主键，自增 |
| `exception_type_id` | BIGINT | 1 | 关联的异常类型 ID（外键） |
| `business_id` | VARCHAR(100) | `SHIFT_20251212_001` | 业务数据 ID，标识报警来源于哪条业务数据（如班次 ID、钻孔 ID） |
| `business_type` | VARCHAR(50) | `SHIFT` | 业务类型：`SHIFT`(班次)/`BOREHOLE`(钻孔)/`OPERATION`(操作) 等 |
| `detected_at` | DATETIME | `2025-12-12 08:00:00` | 首次检测到异常的时间 |
| `detection_context` | JSON | 见下表示例 | **检测上下文**，记录检测时的环境信息、外部事件时间等 |
| `pending_escalations` | JSON | 见下表示例 | **待机升级状态映射**，记录各等级的待机和就绪状态 |
| `current_alert_level` | VARCHAR(32) | `LEVEL_1` | 当前报警等级：`NONE`/`LEVEL_1`/`LEVEL_2`/`LEVEL_3` |
| `last_escalated_at` | DATETIME | `2025-12-12 08:30:00` | 最后一次升级的时刻 |
| `resolved_at` | DATETIME | `2025-12-12 10:15:00` | 异常解除的时刻，仅当 status=RESOLVED 时有值 |
| `status` | VARCHAR(20) | `ACTIVE` | 异常事件状态：`ACTIVE`(活跃)/`RESOLVING`(解除中)/`RESOLVED`(已解除) |
| `resolution_reason` | VARCHAR(255) | `入井记录已补充` | 解除原因 |
| `resolution_source` | VARCHAR(64) | `MANUAL_RESOLUTION` | 解除来源：`MANUAL_RESOLUTION`(手动)/`AUTO_RECOVERY`(自动) |
| `created_at` | DATETIME | `2025-12-12 08:00:00` | 创建时间 |
| `updated_at` | DATETIME | `2025-12-12 10:15:00` | 更新时间 |

**detection_context JSON 示例**：
```json
{
  "shift_id": "SHIFT_20251212_001",
  "shift_start_time": "2025-12-12T08:00:00",
  "shift_end_time": "2025-12-12T16:00:00",
  "team": "A队",
  "detected_by": "RECORD_CHECK",
  "FIRST_BOREHOLE_START_time": "2025-12-12T10:00:00",
  "OPERATION_COMPLETE_time": "2025-12-12T12:30:00"
}
```

**pending_escalations JSON 示例**：
```json
{
  "LEVEL_2": {
    "status": "READY",
    "createdAt": "2025-12-12T08:02:00",
    "readyAt": "2025-12-12T10:00:00",
    "scheduledTime": "2025-12-12T12:00:00",
    "taskId": "12345",
    "dependencies": [
      {
        "eventType": "FIRST_BOREHOLE_START",
        "delayMinutes": 120,
        "required": true
      }
    ],
    "logicalOperator": "AND"
  },
  "LEVEL_3": {
    "status": "WAITING",
    "createdAt": "2025-12-12T08:02:00",
    "readyAt": null,
    "scheduledTime": null,
    "taskId": null,
    "dependencies": [
      {
        "eventType": "OPERATION_COMPLETE",
        "delayMinutes": 60,
        "required": true
      }
    ],
    "logicalOperator": "AND"
  }
}
```

**完整记录示例（ACTIVE 状态，LEVEL_1 已触发）**：
```json
{
  "id": 100,
  "exception_type_id": 1,
  "business_id": "SHIFT_20251212_001",
  "business_type": "SHIFT",
  "detected_at": "2025-12-12T08:00:00",
  "detection_context": {
    "shift_id": "SHIFT_20251212_001",
    "shift_start_time": "2025-12-12T08:00:00",
    "team": "A队",
    "detected_by": "RECORD_CHECK",
    "FIRST_BOREHOLE_START_time": "2025-12-12T10:00:00"
  },
  "pending_escalations": {
    "LEVEL_2": {
      "status": "READY",
      "createdAt": "2025-12-12T08:02:00",
      "readyAt": "2025-12-12T10:00:00",
      "scheduledTime": "2025-12-12T12:00:00",
      "taskId": "12345",
      "dependencies": [
        {"eventType": "FIRST_BOREHOLE_START", "delayMinutes": 120, "required": true}
      ],
      "logicalOperator": "AND"
    }
  },
  "current_alert_level": "LEVEL_1",
  "last_escalated_at": "2025-12-12T08:30:00",
  "resolved_at": null,
  "status": "ACTIVE",
  "resolution_reason": null,
  "resolution_source": null,
  "created_at": "2025-12-12T08:00:00",
  "updated_at": "2025-12-12T10:00:00"
}
```

**完整记录示例（RESOLVED 状态）**：
```json
{
  "id": 100,
  "exception_type_id": 1,
  "business_id": "SHIFT_20251212_001",
  "business_type": "SHIFT",
  "detected_at": "2025-12-12T08:00:00",
  "detection_context": {
    "shift_id": "SHIFT_20251212_001",
    "shift_start_time": "2025-12-12T08:00:00",
    "team": "A队"
  },
  "pending_escalations": null,
  "current_alert_level": "LEVEL_1",
  "last_escalated_at": "2025-12-12T08:30:00",
  "resolved_at": "2025-12-12T10:15:00",
  "status": "RESOLVED",
  "resolution_reason": "入井记录已补充",
  "resolution_source": "MANUAL_RESOLUTION",
  "created_at": "2025-12-12T08:00:00",
  "updated_at": "2025-12-12T10:15:00"
}
```

---

### 2.5 `alert_event_log` 报警事件审计表
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
