# Alert 系统完整数据流图与处理流程

本文档详细说明告警系统的数据流转过程、状态变迁、以及各个关键处理节点。

---

## 1. 核心数据流程图

### 1.1 异常检测到解除的完整流程

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                          异常检测与告警流程全景图                              │
└─────────────────────────────────────────────────────────────────────────────┘

业务系统
  │
  ├─→ 异常检测器（DetectionService）
  │   ├─→ 查询 exception_type (检测逻辑、检测配置)
  │   └─→ 创建 exception_event (status=ACTIVE)
  │       ↓
  │   📍 exception_event 初始状态
  │       {
  │         id: 100,
  │         status: "ACTIVE",
  │         current_alert_level: "NONE",
  │         detection_context: {外部事件时间}, 
  │         pending_escalations: {} (等级待机状态)
  │       }
  │
  ↓
AlertManager 评估最低等级
  │
  ├─→ 查询 alert_rule (LEVEL_1 的触发条件和动作)
  ├─→ 创建 scheduled_task
  │   {
  │     task_name: "ALERT_EVALUATION_100_1",
  │     execute_time: 计算的执行时间,
  │     task_data: {exceptionEventId: 100, levelName: "LEVEL_1"}
  │   }
  └─→ 记录 alert_event_log (eventType=ALERT_TRIGGERED)
  
         [scheduled_task:PENDING] → [系统定时任务执行器]
                                           ↓
                                    AlertExecutor.execute()
                                           ↓
         条件判断 ─────┬─→ ✅ 条件满足
         Quartz     │      ├─→ 记录日志 alert_event_log
                    │      ├─→ 有依赖等级？
                    │      │   ├─→ 是：pending_escalations[LEVEL_2]=WAITING
                    │      │   └─→ 否：立即创建下一等级任务
                    │      └─→ 标记 scheduled_task=SUCCESS
                    │
                    └─→ ❌ 条件不满足
                           ├─→ 计算延迟，更新 execute_time
                           └─→ 标记 scheduled_task=PENDING (重新排队)

         外部事件发生（例如：钻孔开始）
                │
                ↓
         AlertDependencyManager 事件监听器
                │
                ├─→ 更新 exception_event.detection_context
                │   {first_borehole_start_time: "2025-12-12T10:00:00"}
                │
                └─→ 检查 pending_escalations[LEVEL_2]
                    ├─→ 依赖满足（延迟时间 >= 480 分钟）？
                    │   ├─→ 是：
                    │   │   ├─→ pending_escalations[LEVEL_2].status = "READY"
                    │   │   ├─→ 创建 LEVEL_2 的 scheduled_task
                    │   │   └─→ 更新 exception_event.pending_escalations
                    │   │
                    │   └─→ 否：等待
                    │

异常解除（手动或自动）
        │
        ↓
 AlertResolutionService.resolveAlert()
        │
        ├─→ UPDATE exception_event
        │   status = "RESOLVING" (保护状态，防止中途崩溃重复处理)
        │
        ├─→ 查询所有待执行任务 scheduled_task
        │   WHERE task_data.exceptionEventId = 100 AND status = 'PENDING'
        │
        ├─→ 取消所有待执行任务 (status = 'CANCELLED')
        │   ├─→ 记录 alert_event_log (eventType=TASK_CANCELLED)
        │   └─→ 记录 alert_event_log (eventType=SYSTEM_RECOVERY)
        │
        └─→ 最终状态更新
            UPDATE exception_event
            status = "RESOLVED"
            pending_escalations = NULL
            resolved_at = NOW()
            resolution_source = "MANUAL_RESOLUTION" / "AUTO_RECOVERY"
            
            记录 alert_event_log (eventType=ALERT_RESOLVED)
```

---

### 1.2 依赖等级与等级升级流程

```
┌────────────────────────────────────────────────────────────┐
│             多等级告警与依赖升级流程                         │
└────────────────────────────────────────────────────────────┘

异常事件创建时：exception_event.pending_escalations = {}

第1步：LEVEL_1 评估（无依赖）
    ┌─────────────────────────────────┐
    │ trigger_condition.condition_type │
    ├─────────────────────────────────┤
    │ ABSOLUTE (固定时刻 16:00)        │
    └──────────────┬──────────────────┘
                   │
    ┌──────────────▼──────────────┐
    │ 系统时间 >= 16:00?          │
    ├──────────────┬──────────────┤
    │ 是           │ 否           │
    └──┬───────────┴──────────────┘
       │
       ├─→ ✅ 触发 LEVEL_1 报警
       │   ├─→ alert_event_log (ALERT_TRIGGERED)
       │   ├─→ current_alert_level = "LEVEL_1"
       │   ├─→ last_escalated_at = NOW()
       │   │
       │   └─→ 检查 LEVEL_2 是否有依赖
       │       ├─→ 是：pending_escalations[LEVEL_2] = {
       │       │        status: "WAITING",
       │       │        dependencies: [{eventType: "FIRST_BOREHOLE_START", delayMinutes: 120}]
       │       │      }
       │       │      创建 scheduled_task (LEVEL_2 评估)，execute_time = NOW() + 480分钟
       │       │
       │       └─→ 否：立即创建 LEVEL_2 scheduled_task


第2步：外部事件发生监听
    ┌──────────────────────────────────────┐
    │ 业务系统发布 FIRST_BOREHOLE_START    │
    │ 时间：2025-12-12 10:00:00            │
    └──────────────┬───────────────────────┘
                   │
    ┌──────────────▼───────────────────────┐
    │ AlertDependencyManager 捕捉事件      │
    ├──────────────┬───────────────────────┤
    │ ✅ 记录事件时间到 detection_context │
    │ ✅ 查询所有待机 pending_escalations │
    │ ✅ 检查是否 delay >= 120分钟        │
    └──────────────┬───────────────────────┘
                   │
                   ├─→ 检查 LEVEL_2 状态
                   │   ├─→ status = "WAITING"：
                   │   │   ├─→ 检查依赖条件满足？
                   │   │   │   ├─→ 是（延迟时间满足）：
                   │   │   │   │   ├─→ pending_escalations[LEVEL_2].status = "READY"
                   │   │   │   │   ├─→ pending_escalations[LEVEL_2].readyAt = NOW()
                   │   │   │   │   ├─→ pending_escalations[LEVEL_2].scheduledTime = NOW() + 新延迟
                   │   │   │   │   ├─→ 创建 scheduled_task (LEVEL_2)
                   │   │   │   │   │   execute_time = NOW() + 新延迟
                   │   │   │   │   │   task_data: {exceptionEventId: 100, levelName: "LEVEL_2"}
                   │   │   │   │   └─→ pending_escalations[LEVEL_2].taskId = 新任务ID
                   │   │   │   │
                   │   │   │   └─→ 否（延迟不足）：继续等待
                   │   │   │
                   │   │   └─→ status != "WAITING"：忽略
                   │   │
                   │   └─→ 检查 LEVEL_3 状态（类似逻辑）
                   │
                   └─→ 保存更新后的 exception_event.pending_escalations


第3步：LEVEL_2 计划任务执行
    ┌──────────────────────────────────────┐
    │ scheduled_task (LEVEL_2) 执行时间到 │
    └──────────────┬───────────────────────┘
                   │
                   ├─→ AlertExecutor 执行
                   │   ├─→ 检查依赖条件（已在 READY 状态）
                   │   ├─→ ✅ 条件满足
                   │   │   ├─→ alert_event_log (ALERT_ESCALATED)
                   │   │   ├─→ current_alert_level = "LEVEL_2"
                   │   │   ├─→ 执行动作 (EMAIL/SMS/WEBHOOK)
                   │   │   └─→ 如有 LEVEL_3，继续等待逻辑
                   │   │
                   │   └─→ scheduled_task status = "SUCCESS"
                   │
```

---

### 1.3 状态转移与异常生命周期

```
┌────────────────────────────────────────────────────────────┐
│          Exception Event 状态机                            │
└────────────────────────────────────────────────────────────┘

                    ┌──────────────┐
                    │   创建时     │
                    │ status=NONE  │
                    └──────┬───────┘
                           │
                    ┌──────▼───────────────┐
                    │ ACTIVE                │
                    │ (活跃，待机评估中)     │
                    ├──────────────────────┤
                    │ • 异常被检测到        │
                    │ • 各等级待机处理中    │
                    │ • 依赖判断中          │
                    │ • 继续升级评估中      │
                    └──────┬────────────────┘
                           │
              ┌────────────┴──────────────┐
              │                           │
         (手动解除)              (异常消除自动发现)
              │                           │
         调用 API              系统自动检测
              │                           │
         ┌────▼──────────┐         ┌─────▼──────┐
         │ RESOLVING      │         │ RESOLVING  │
         │ (解除中)        │         │ (解除中)    │
         ├────────────────┤         ├────────────┤
         │ • 取消所有     │         │ • 取消所有 │
         │   待执行任务    │         │   待执行任务│
         │ • 记录 TASK_   │         │ • 记录日志 │
         │   CANCELLED   │         │            │
         │ • 防护状态     │         │ • 防护状态 │
         │   (防重复处理)  │         │ (防重复)   │
         └────┬──────────┘         └─────┬──────┘
              │                           │
              └───────────┬───────────────┘
                          │
                    ┌─────▼────────────┐
                    │ RESOLVED         │
                    │ (已解除)          │
                    ├──────────────────┤
                    │ • pending_       │
                    │   escalations=NULL│
                    │ • resolved_at    │
                    │   = NOW()        │
                    │ • resolution_    │
                    │   reason/source  │
                    │ • 记录日志       │
                    └──────────────────┘

🔒 RESOLVING 状态的意义：
   ├─→ 多线程安全：防止两个线程同时处理解除逻辑
   ├─→ 系统一致性：宕机恢复时检查该状态，避免重复取消任务
   └─→ 确保原子性：completed_escalations 和其他状态一起更新
```

---

### 1.4 Scheduled Task 任务状态流转

```
┌────────────────────────────────────────────────────────────┐
│          Scheduled Task 执行生命周期                        │
└────────────────────────────────────────────────────────────┘

     ┌────────────┐
     │  PENDING   │  创建时状态，等待定时器执行
     └──────┬─────┘
            │
            ├─→ Quartz 定时器触发
            │   execute_time <= NOW()
            │
            ├─→ 标记为执行中
            ├─→ 记录 task_execution_log (status=EXECUTING)
            │
     ┌──────▼────────────────────────┐
     │      EXECUTING                 │
     │   (执行中，正在处理)            │
     ├──────────────────────────────┤
     │  • AlertExecutor.execute()   │
     │  • 检查条件是否满足            │
     │  • 执行动作（发邮件/短信）     │
     │  • 更新异常事件状态            │
     └──────┬─────────────┬──────────┘
            │             │
       ✅ 成功         ❌ 失败
            │             │
     ┌──────▼──┐   ┌─────▼────────────┐
     │ SUCCESS │   │  FAILED          │
     │(成功)    │   │ (执行失败)       │
     └─────────┘   ├──────────────────┤
                   │ retry_count < 3? │
                   └──────┬───────────┘
                          │
                    ┌─────┴─────┐
                    │           │
                   是           否
                    │           │
              ┌─────▼──┐   ┌────▼────┐
              │ PENDING │   │  FAILED │
              │(重试)    │   │(失败)   │
              ├─────────┤   └─────────┘
              │ 等待下次 │
              │ 重试时间 │
              └─────────┘

其他结束状态：
    ├─→ CANCELLED：异常解除时被取消
    └─→ TIMEOUT：执行超时（execution_timeout 秒内未完成）

状态查询 SQL：
    SELECT status, COUNT(*) 
    FROM scheduled_task 
    WHERE task_type = 'ALERT' 
    GROUP BY status;
```

---

## 2. JSON 字段数据结构详解

### 2.1 detection_context（检测上下文）

用途：记录异常检测时的环境信息和外部事件发生的时间

```json
{
  "shift_id": "SHIFT_20251212_001",
  "shift_start_time": "2025-12-12T08:00:00",
  "shift_end_time": "2025-12-12T16:00:00",
  "team": "A队",
  "detected_by": "RECORD_CHECK",
  
  "FIRST_BOREHOLE_START_time": "2025-12-12T10:00:00",
  "BOREHOLE_COMPLETE_time": "2025-12-12T12:30:00",
  "OPERATION_END_time": "2025-12-12T14:45:00"
}
```

**演变过程示例**：
```
T1: 异常创建 (08:00)
    {
      "shift_id": "SHIFT_20251212_001",
      "detected_by": "RECORD_CHECK"
    }

T2: 钻孔开始事件发生 (10:00)
    事件监听器更新 →
    {
      "shift_id": "SHIFT_20251212_001",
      "detected_by": "RECORD_CHECK",
      "FIRST_BOREHOLE_START_time": "2025-12-12T10:00:00"  // 新增
    }

T3: 钻孔完成事件发生 (12:30)
    事件监听器更新 →
    {
      "shift_id": "SHIFT_20251212_001",
      "detected_by": "RECORD_CHECK",
      "FIRST_BOREHOLE_START_time": "2025-12-12T10:00:00",
      "BOREHOLE_COMPLETE_time": "2025-12-12T12:30:00"  // 新增
    }
```

---

### 2.2 pending_escalations（待机升级状态）

用途：追踪各等级升级的依赖状态、何时变 READY、何时执行

```json
{
  "LEVEL_2": {
    "status": "WAITING",
    "createdAt": "2025-12-12T08:02:00",
    "readyAt": null,
    "scheduledTime": null,
    "taskId": null,
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
        "eventType": "OPERATION_END",
        "delayMinutes": 60,
        "required": true
      },
      {
        "eventType": "BOREHOLE_COMPLETE",
        "delayMinutes": 0,
        "required": false
      }
    ],
    "logicalOperator": "OR"
  }
}
```

**状态演变示例**：

```
T0: 8:00 - 异常创建，创建 pending_escalations
    {
      "LEVEL_2": {status: "WAITING", createdAt: "08:02"},
      "LEVEL_3": {status: "WAITING", createdAt: "08:02"}
    }

T1: 8:30 - LEVEL_1 触发，创建 LEVEL_2 计划任务（execute_time = 8:30 + 480分钟 = 16:30）
    {
      "LEVEL_2": {
        status: "WAITING", 
        createdAt: "08:02",
        scheduledTime: "2025-12-12T16:30:00",
        taskId: 12345
      },
      ...
    }

T2: 10:00 - FIRST_BOREHOLE_START 事件发生
    事件监听器检查：
    ├─ 事件时间：10:00
    ├─ 依赖延迟：120分钟
    ├─ 现在时间：10:00
    ├─ 120分钟后 = 12:00
    └─ 现在还不到 12:00，不满足延迟条件 → 继续等待

T3: 12:00 - 依赖延迟时间满足（FIRST_BOREHOLE_START + 120分钟）
    事件监听器更新：
    {
      "LEVEL_2": {
        status: "READY",          // ✅ 状态改为 READY
        readyAt: "2025-12-12T12:00:00",  // ✅ 记录准备好的时间
        scheduledTime: "2025-12-12T12:00:00",  // ✅ 可能更新为当前时间
        taskId: 12345
      }
    }
    同时创建新的 scheduled_task (execute_time = NOW() 即刻执行)

T4: 12:00 - scheduled_task (LEVEL_2) 立即执行
    ├─ 检查条件：READY 状态
    ├─ 条件满足
    ├─ 触发 ALERT_ESCALATED 日志
    ├─ current_alert_level = "LEVEL_2"
    └─ 更新 pending_escalations[LEVEL_3] 状态（如果有依赖）

T5: 异常解除（14:00）
    ├─ UPDATE exception_event.pending_escalations = NULL
    └─ 所有待机升级状态全部清空
```

---

### 2.3 action_config（动作配置）

用途：为不同类型的动作存储具体参数

**Email 类型**：
```json
{
  "recipients": ["admin@company.com", "team@company.com", "shift_manager@company.com"],
  "subject": "【告警】入井记录不足 - LEVEL_2",
  "template": "alert_level_2_email_template",
  "cc": ["supervisor@company.com"],
  "priority": "high"
}
```

**SMS 类型**：
```json
{
  "phoneNumbers": ["13800138000", "13900139000"],
  "content": "【告警】班次内入井记录不足，请及时处理"
}
```

**Webhook 类型**：
```json
{
  "url": "https://api.company.com/alert/notify",
  "method": "POST",
  "headers": {
    "Authorization": "Bearer token_xxx",
    "Content-Type": "application/json"
  },
  "timeout": 5000,
  "retries": 3
}
```

**LOG 类型**：
```json
{
  "logLevel": "WARN",
  "message": "LEVEL_1 异常告警已触发",
  "includeContext": true
}
```

---

### 2.4 detection_config（检测配置）

用途：异常类型的检测规则配置

**RECORD_CHECK 类型**：
```json
{
  "tableName": "work_log",
  "dateField": "entry_time",
  "duration": "3h",
  "minCount": 1,
  "conditionField": "entry_type",
  "conditionValue": "入井",
  "businessIdField": "shift_id"
}
```

**TIME_CHECK 类型**：
```json
{
  "tableName": "shift_operation",
  "startTimeField": "operation_start_time",
  "endTimeField": "operation_end_time",
  "maxDuration": "8h",
  "alertWhen": "EXCEED"
}
```

**CUSTOM 类型**：
```json
{
  "scriptType": "SQL",
  "query": "SELECT COUNT(*) as cnt FROM work_log WHERE entry_time > DATE_SUB(NOW(), INTERVAL 3 HOUR) AND entry_type = '入井'",
  "threshold": 1,
  "operator": "<"
}
```

---

### 2.5 task_data（任务数据）

用途：scheduled_task 的参数载体，包含要评估的异常事件和规则

```json
{
  "exceptionEventId": 100,
  "alertRuleId": 2,
  "levelName": "LEVEL_2"
}
```

字段说明：
- `exceptionEventId`：要评估的异常事件 ID，用于查询 exception_event 和 pending_escalations
- `alertRuleId`：要执行的告警规则 ID，用于查询 alert_rule 获取动作信息
- `levelName`：等级名称，冗余字段，便于日志追踪

---

## 3. 关键业务场景数据流示例

### 3.1 场景：班次内入井人数不足

**T0: 08:00 - 班次开始，异常检测**
```
检测器运行：
SELECT COUNT(*) FROM work_log 
WHERE entry_time >= NOW() - INTERVAL 3 HOUR AND entry_type = '入井'

结果：0 < 1 (最小值)，异常触发！

执行：
INSERT INTO exception_event(...)
VALUES (
  exception_type_id=1,
  business_id='SHIFT_20251212_001',
  status='ACTIVE',
  current_alert_level='NONE',
  detection_context={shift_id, team, ...},
  pending_escalations={}
)
→ id=100
```

**T1: 08:30 - 触发 LEVEL_1（固定时刻评估）**
```
AlertManager 查询 alert_rule WHERE exception_type_id=1 AND level='LEVEL_1'
获得：trigger_condition_id=10 (ABSOLUTE 16:00)
状态：status='ACTIVE'

当前时间是否 >= 16:00？否，延迟评估
创建 scheduled_task:
{
  task_name: 'ALERT_EVALUATION_100_1',
  execute_time: '16:00:00',
  task_data: {exceptionEventId: 100, alertRuleId: 1, levelName: 'LEVEL_1'}
}
→ scheduled_task.id = 12345
```

**T2: 10:00 - 钻孔开始，事件发生**
```
业务系统发布事件：FIRST_BOREHOLE_START
AlertDependencyManager 监听到

更新 exception_event:
{
  detection_context: {
    shift_id: "SHIFT_20251212_001",
    FIRST_BOREHOLE_START_time: "10:00:00"
  }
}

检查 pending_escalations[LEVEL_2] 依赖：
├─ 需要事件：FIRST_BOREHOLE_START
├─ 需要延迟：120 分钟
├─ 现在时间：10:00
├─ 120 分钟后 = 12:00
└─ 现在 < 12:00，还需等待（继续 WAITING）
```

**T3: 12:00 - 延迟时间满足，LEVEL_2 变 READY**
```
后台定时任务检查待机升级（或事件监听器）

pending_escalations[LEVEL_2].dependencies[0] 检查：
├─ FIRST_BOREHOLE_START_time 已存在：10:00
├─ 现在时间：12:00
├─ 12:00 - 10:00 = 120 分钟 ≥ 要求的 120 分钟 ✅
└─ 条件满足

执行：
UPDATE exception_event
SET pending_escalations = {
  LEVEL_2: {
    status: 'READY',  // ✅ 改为 READY
    readyAt: '12:00:00',
    scheduledTime: '12:00:00',
    taskId: 12346
  }
}

创建新 scheduled_task (LEVEL_2):
{
  task_name: 'ALERT_EVALUATION_100_2',
  execute_time: '12:00:00',  // 立即执行
  task_data: {exceptionEventId: 100, alertRuleId: 2, levelName: 'LEVEL_2'}
}
→ scheduled_task.id = 12346
```

**T4: 12:00 - LEVEL_2 任务执行**
```
AlertExecutor 执行 task_id=12346

检查条件：pending_escalations[LEVEL_2].status 是否 READY？✅
执行动作：EMAIL 发送

记录日志：
INSERT INTO alert_event_log
VALUES (
  exception_event_id: 100,
  alert_rule_id: 2,
  alert_level: 'LEVEL_2',
  event_type: 'ALERT_ESCALATED',
  action_status: 'SENT'
)

更新异常事件：
UPDATE exception_event
SET current_alert_level = 'LEVEL_2',
    last_escalated_at = NOW(),
    pending_escalations = {
      LEVEL_3: {status: 'WAITING', ...}
    }
```

**T5: 14:00 - 入井人员到达，异常自动解除**
```
业务系统发布事件：ENTRY_LOGGED
数据库查询：
SELECT COUNT(*) FROM work_log 
WHERE entry_time >= NOW() - INTERVAL 3 HOUR AND entry_type = '入井'

结果：1 >= 1 (最小值)，异常消除！✅

执行 resolveAlert(exceptionEventId=100):

Step1: 防护状态
UPDATE exception_event
SET status = 'RESOLVING'

Step2: 取消所有待执行任务
SELECT * FROM scheduled_task 
WHERE status = 'PENDING' AND task_data.exceptionEventId = 100

取消任务 12346 (LEVEL_3 待执行任务):
UPDATE scheduled_task SET status = 'CANCELLED'
INSERT INTO alert_event_log (eventType='TASK_CANCELLED', ...)

Step3: 最终解除
UPDATE exception_event
SET status = 'RESOLVED',
    resolved_at = NOW(),
    resolution_reason = '入井人员已到达',
    resolution_source = 'AUTO_RECOVERY',
    pending_escalations = NULL

记录日志：
INSERT INTO alert_event_log
VALUES (
  exception_event_id: 100,
  event_type: 'ALERT_RESOLVED',
  resolution_reason: '入井人员已到达'
)
```

---

## 4. 数据库索引最佳实践

```sql
-- 查询活跃异常事件
CREATE INDEX idx_exception_event_status_type 
ON exception_event(status, exception_type_id);

-- 查询某业务的异常
CREATE INDEX idx_exception_event_business 
ON exception_event(business_id, business_type);

-- 按时间排序
CREATE INDEX idx_exception_event_detected 
ON exception_event(detected_at DESC);

-- 查询异常的报警日志
CREATE INDEX idx_alert_event_log_exception_type 
ON alert_event_log(exception_event_id, event_type);

-- 按触发时间查询
CREATE INDEX idx_alert_event_log_triggered 
ON alert_event_log(triggered_at DESC);

-- 查询待执行或执行中的任务
CREATE INDEX idx_scheduled_task_status_time 
ON scheduled_task(status, execute_time);

-- 查询报警类型的任务
CREATE INDEX idx_scheduled_task_type_status 
ON scheduled_task(task_type, status);
```

---

## 5. 常见问题 (FAQ)

### Q: pending_escalations 什么时候为 NULL？
**A**: 当异常解除时（status=RESOLVED），pending_escalations 会被设置为 NULL。如果检查 NULL 字段发现有值，说明异常仍在活跃状态。

### Q: 如何从系统恢复 (崩溃后恢复) 中识别已处理过的异常？
**A**: 检查 exception_event.status 字段：
- `ACTIVE`: 异常活跃，需要继续处理
- `RESOLVING`: 异常正在解除中，需要检查是否已取消所有任务
- `RESOLVED`: 异常已完全解除，可以忽略

### Q: 为什么 LEVEL_2 在 LEVEL_1 触发后不立即执行？
**A**: 因为 LEVEL_2 可能有依赖条件（如某个外部事件）。在事件发生前，LEVEL_2 保持 WAITING 状态。一旦依赖条件满足（延迟时间充足），LEVEL_2 自动变 READY 并执行。

### Q: action_config 的字段是否有固定格式？
**A**: 否。action_config 是灵活的 JSON 对象，可根据 action_type 和业务需求自定义字段。

---

本文档已完整说明整个告警系统的数据流转、状态变迁和字段含义。如有疑问，请参考源代码实现。
