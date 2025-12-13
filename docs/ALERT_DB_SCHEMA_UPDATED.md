# ALERT 系统数据库结构与完整示例

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
| `priority` | INT | 8 | 优先级（0-10），报警任务通常为 8-10 |
| `execution_timeout` | BIGINT | 300 | 执行超时（秒），报警任务默认 300 秒 |
| `task_data` | JSON | 见下表示例 | **任务参数**，包含 exceptionEventId、alertRuleId/levelName 等 |
| `status` | VARCHAR(20) | `PENDING` | 任务状态：`PENDING`/`EXECUTING`/`SUCCESS`/`FAILED`/`CANCELLED`/`TIMEOUT` |
| `retry_count` | INT | 0 | 已重试次数 |
| `max_retry_count` | INT | 3 | 最大重试次数，报警任务通常 1-2 次 |
| `last_execute_time` | DATETIME | `2025-12-12 16:30:05` | 最后执行时间，第一次执行后更新 |
| `error_message` | TEXT | null | 执行失败的错误信息 |
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
  "task_data": {
    "exceptionEventId": 100,
    "alertRuleId": 2,
    "levelName": "LEVEL_2"
  },
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
| `level` | VARCHAR(20) | `LEVEL_2` | 报警等级：`LEVEL_1`(轻度)/`LEVEL_2`(中度)/`LEVEL_3`(严重) |
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
- **关键字段说明**：

| 字段名 | 类型 | 示例值 | 说明 |
|--------|------|--------|------|
| `id` | BIGINT | 1001 | 主键，自增 |
| `exception_event_id` | BIGINT | 100 | 关联的异常事件 ID（外键） |
| `alert_rule_id` | BIGINT | 2 | 关联的报警规则 ID（外键），某些事件（如 ALERT_RESOLVED）可为 null |
| `alert_level` | VARCHAR(32) | `LEVEL_1` | 报警等级：`LEVEL_1`/`LEVEL_2`/`LEVEL_3` |
| `event_type` | VARCHAR(64) | `ALERT_TRIGGERED` | 事件类型：`ALERT_TRIGGERED`(触发)/`ALERT_ESCALATED`(升级)/`ALERT_RESOLVED`(解除)/`TASK_CANCELLED`(任务取消) |
| `triggered_at` | DATETIME | `2025-12-12 08:30:00` | 事件发生时间 |
| `trigger_reason` | VARCHAR(255) | `条件满足，触发LEVEL_1报警` | 触发原因描述，用于审计追踪 |
| `action_status` | VARCHAR(20) | `SENT` | 动作执行状态：`PENDING`(待发送)/`SENT`(已发送)/`FAILED`(失败)/`COMPLETED`(完成) |
| `action_error_message` | TEXT | null | 动作执行失败时的错误信息 |
| `created_at` | DATETIME | `2025-12-12 08:30:00` | 日志记录时间 |

**完整记录示例 1（ALERT_TRIGGERED）**：
```json
{
  "id": 1001,
  "exception_event_id": 100,
  "alert_rule_id": 1,
  "alert_level": "LEVEL_1",
  "event_type": "ALERT_TRIGGERED",
  "triggered_at": "2025-12-12T08:30:00",
  "trigger_reason": "业务检测通过，时间条件满足，触发LEVEL_1报警",
  "action_status": "SENT",
  "action_error_message": null,
  "created_at": "2025-12-12T08:30:00"
}
```

**完整记录示例 2（ALERT_ESCALATED）**：
```json
{
  "id": 1002,
  "exception_event_id": 100,
  "alert_rule_id": 2,
  "alert_level": "LEVEL_2",
  "event_type": "ALERT_ESCALATED",
  "triggered_at": "2025-12-12T12:00:00",
  "trigger_reason": "FIRST_BOREHOLE_START事件已发生且延迟时间满足，升级到LEVEL_2",
  "action_status": "SENT",
  "action_error_message": null,
  "created_at": "2025-12-12T12:00:00"
}
```

**完整记录示例 3（ALERT_RESOLVED）**：
```json
{
  "id": 1003,
  "exception_event_id": 100,
  "alert_rule_id": null,
  "alert_level": "LEVEL_1",
  "event_type": "ALERT_RESOLVED",
  "triggered_at": "2025-12-12T10:15:00",
  "trigger_reason": "入井记录已补充，异常解除",
  "action_status": "COMPLETED",
  "action_error_message": null,
  "created_at": "2025-12-12T10:15:00"
}
```

**完整记录示例 4（TASK_CANCELLED）**：
```json
{
  "id": 1004,
  "exception_event_id": 100,
  "alert_rule_id": 2,
  "alert_level": "LEVEL_2",
  "event_type": "TASK_CANCELLED",
  "triggered_at": "2025-12-12T10:15:00",
  "trigger_reason": "异常解除，取消任务 12345",
  "action_status": "COMPLETED",
  "action_error_message": null,
  "created_at": "2025-12-12T10:15:00"
}
```

---

## 3. 核心 SQL 示例与操作

### 3.1 创建异常类型
```sql
INSERT INTO exception_type(name, description, detection_logic_type, detection_config, enabled)
VALUES (
  '入井记录不足',
  '班次内入井记录数不足指定数量',
  'RECORD_CHECK',
  JSON_OBJECT(
    'tableName', 'work_log',
    'dateField', 'entry_time',
    'duration', '3h',
    'minCount', 1,
    'conditionField', 'entry_type',
    'conditionValue', '入井'
  ),
  1
);
-- 返回 id=1
```

### 3.2 创建触发条件（ABSOLUTE 类型）
```sql
INSERT INTO trigger_condition(condition_type, absolute_time)
VALUES ('ABSOLUTE', '16:00:00');
-- 返回 id=10
```

### 3.3 创建触发条件（RELATIVE 类型）
```sql
INSERT INTO trigger_condition(
  condition_type, 
  relative_event_type, 
  relative_duration_minutes,
  time_window_start,
  time_window_end
)
VALUES (
  'RELATIVE',
  'FIRST_BOREHOLE_START',
  480,
  '08:00:00',
  '22:00:00'
);
-- 返回 id=11
```

### 3.4 创建报警规则（LEVEL_1）
```sql
INSERT INTO alert_rule(
  exception_type_id, 
  level, 
  trigger_condition_id, 
  action_type, 
  action_config,
  priority,
  enabled
)
VALUES (
  1,
  'LEVEL_1',
  10,
  'LOG',
  JSON_OBJECT('logLevel', 'WARN', 'message', 'LEVEL_1报警'),
  5,
  1
);
-- 返回 id=1
```

### 3.5 创建报警规则（LEVEL_2 有邮件通知）
```sql
INSERT INTO alert_rule(
  exception_type_id, 
  level, 
  trigger_condition_id, 
  action_type, 
  action_config,
  priority,
  enabled
)
VALUES (
  1,
  'LEVEL_2',
  11,
  'EMAIL',
  JSON_OBJECT(
    'recipients', JSON_ARRAY('admin@company.com', 'team@company.com'),
    'subject', '入井记录不足预警 - LEVEL_2',
    'template', 'alert_level_2_template'
  ),
  6,
  1
);
-- 返回 id=2
```

### 3.6 创建异常事件
```sql
INSERT INTO exception_event(
  exception_type_id, 
  business_id,
  business_type,
  detected_at, 
  detection_context,
  current_alert_level,
  status
)
VALUES (
  1,
  'SHIFT_20251212_001',
  'SHIFT',
  '2025-12-12 08:00:00',
  JSON_OBJECT(
    'shift_id', 'SHIFT_20251212_001',
    'shift_start_time', '2025-12-12T08:00:00',
    'team', 'A队',
    'detected_by', 'RECORD_CHECK'
  ),
  'NONE',
  'ACTIVE'
);
-- 返回 id=100
```

### 3.7 创建计划评估任务（LEVEL_1）
```sql
INSERT INTO scheduled_task(
  task_name,
  task_type,
  schedule_mode,
  execute_time,
  priority,
  execution_timeout,
  task_data,
  status
)
VALUES (
  'ALERT_EVALUATION_100_1',
  'ALERT',
  'ONCE',
  '2025-12-12 16:00:00',
  8,
  300,
  JSON_OBJECT(
    'exceptionEventId', 100,
    'alertRuleId', 1,
    'levelName', 'LEVEL_1'
  ),
  'PENDING'
);
-- 返回 id=12345
```

### 3.8 记录报警触发日志
```sql
INSERT INTO alert_event_log(
  exception_event_id, 
  alert_rule_id, 
  alert_level, 
  event_type, 
  triggered_at, 
  trigger_reason,
  action_status
)
VALUES (
  100, 
  1, 
  'LEVEL_1', 
  'ALERT_TRIGGERED', 
  '2025-12-12 16:00:00', 
  '条件满足，触发LEVEL_1报警',
  'SENT'
);
-- 返回 id=1001
```

### 3.9 查询活跃异常事件
```sql
SELECT * FROM exception_event 
WHERE status = 'ACTIVE' 
  AND exception_type_id = 1
ORDER BY detected_at DESC;
```

### 3.10 查询某异常的所有报警日志
```sql
SELECT * FROM alert_event_log 
WHERE exception_event_id = 100
ORDER BY triggered_at DESC;
```

---

## 4. 数据关系与流转说明

### 4.1 表关系结构
```
exception_type (异常类型定义)
  ├─ alert_rule (多:1 关系) - 定义各等级规则
  │  ├─ trigger_condition (多:1 关系) - 触发时机
  │  └─ alert_rule_id → alert_event_log (审计)
  │
  └─ exception_event (多:1 关系) - 异常事件实例
     ├─ detection_context (JSON：外部事件时间)
     ├─ pending_escalations (JSON：待机升级状态)
     ├─ exception_event_id → alert_event_log (完整历史)
     └─ exception_event_id → scheduled_task (关联任务)
```

### 4.2 数据流转过程
```
1️⃣ 业务系统检测异常
   ↓
2️⃣ 创建 exception_event (status=ACTIVE, current_alert_level=NONE)
   ↓
3️⃣ 查询 alert_rule，为最低等级创建 scheduled_task
   ↓
4️⃣ 计划时间到达，AlertExecutor 执行任务
   ↓
5️⃣ 检查条件满足
   ├─ ✅ 是 → 记录 alert_event_log (ALERT_TRIGGERED)
   │       ├─ 有依赖 → pending_escalations=WAITING
   │       └─ 无依赖 → 创建下一等级 scheduled_task
   │
   └─ ❌ 否 → 重新计算执行时间，创建延迟任务
        
6️⃣ 外部事件发生
   ↓
7️⃣ AlertDependencyManager 监听事件，更新 detection_context
   ↓
8️⃣ 检查 pending_escalations 依赖是否满足
   ├─ ✅ 满足 → 创建该等级的 scheduled_task，改 status=READY
   └─ ❌ 不满足 → 继续等待
        
9️⃣ 异常解除（手动/自动）
   ↓
🔟 调用 resolveAlert()
    ├─ 更新 status=RESOLVING（防护状态）
    ├─ 查询并取消所有 scheduled_task
    ├─ 记录 alert_event_log (ALERT_RESOLVED)
    └─ 更新 status=RESOLVED, pending_escalations=null
```

---

## 5. 常见查询场景

### 查询异常的完整状态变更历史
```sql
SELECT *
FROM alert_event_log
WHERE exception_event_id = 100
ORDER BY triggered_at ASC;
```

### 查询当前所有待机的升级任务
```sql
SELECT ee.id, ee.business_id, ee.pending_escalations, st.id as task_id, st.execute_time
FROM exception_event ee
LEFT JOIN scheduled_task st ON 
  JSON_UNQUOTE(JSON_EXTRACT(ee.pending_escalations, '$[*].taskId')) = CAST(st.id AS CHAR)
WHERE ee.status = 'ACTIVE'
  AND ee.pending_escalations IS NOT NULL;
```

### 查询未在规定时间内处理的异常
```sql
SELECT id, business_id, detected_at, TIMESTAMPDIFF(MINUTE, detected_at, NOW()) as minutes_since_detection
FROM exception_event
WHERE status = 'ACTIVE'
  AND TIMESTAMPDIFF(MINUTE, detected_at, NOW()) > 240
ORDER BY detected_at ASC;
```

### 统计各异常类型的触发次数
```sql
SELECT et.name, COUNT(ael.id) as trigger_count
FROM exception_type et
LEFT JOIN exception_event ee ON et.id = ee.exception_type_id
LEFT JOIN alert_event_log ael ON ee.id = ael.exception_event_id
WHERE ael.event_type = 'ALERT_TRIGGERED'
GROUP BY et.id, et.name
ORDER BY trigger_count DESC;
```

---

## 6. 索引推荐与优化

为提升查询性能，建议创建以下索引：

```sql
-- exception_event 优化索引
CREATE INDEX idx_exception_event_status_type ON exception_event(status, exception_type_id);
CREATE INDEX idx_exception_event_business ON exception_event(business_id, business_type);
CREATE INDEX idx_exception_event_detected ON exception_event(detected_at DESC);

-- alert_event_log 优化索引
CREATE INDEX idx_alert_event_log_exception_type ON alert_event_log(exception_event_id, event_type);
CREATE INDEX idx_alert_event_log_triggered ON alert_event_log(triggered_at DESC);

-- scheduled_task 优化索引
CREATE INDEX idx_scheduled_task_status_time ON scheduled_task(status, execute_time);
CREATE INDEX idx_scheduled_task_type_status ON scheduled_task(task_type, status);

-- trigger_condition 优化索引
CREATE INDEX idx_trigger_condition_type ON trigger_condition(condition_type);

-- alert_rule 优化索引
CREATE INDEX idx_alert_rule_exception_level ON alert_rule(exception_type_id, level, enabled);
```

---

本文档已覆盖所有核心表的完整字段说明和示例数据。如有任何疑问，请参考项目中的 DDL 定义文件。
