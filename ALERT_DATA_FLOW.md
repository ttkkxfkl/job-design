# Alert 模块完整数据流图

## 1. 整体系统流程图

```mermaid
graph TD
    A["🔍 外部系统检测异常<br/>BusinessLogic System"]
    B["📝 创建异常事件<br/>ExceptionEvent"]
    C["🚀 初始化蓝色评估<br/>scheduleInitialEvaluation"]
    D["⏰ 任务调度系统执行<br/>TaskScheduler"]
    E["⚙️ AlertExecutor.execute"]
    F["🔎 业务检测层<br/>ExceptionDetectionStrategy"]
    G["⏱️ 时间触发评估<br/>TriggerStrategy"]
    H["💥 条件满足?"]
    I["📧 执行报警动作<br/>AlertActionExecutor"]
    J["📋 记录报警日志<br/>AlertEventLog"]
    K["🔄 检查依赖事件<br/>AlertDependencyManager"]
    L["🎯 有依赖事件?"]
    M["⏳ 事件已发生?"]
    N["💾 记录待机状态<br/>pending_escalations"]
    O["🎉 创建下一级评估任务<br/>scheduleNextLevelEvaluation"]
    P["📤 Spring发布事件<br/>publishEvent"]
    Q["👂 监听并处理事件<br/>onAlertSystemEvent"]
    R["✅ 检查待机升级条件"]
    S["🔄 升级到下一级"]
    T["❌ 条件未满足<br/>继续等待"]

    A --> B
    B --> C
    C --> D
    D --> E
    E --> F
    F --> G
    G --> H
    H -->|未满足| T
    H -->|满足| I
    I --> J
    J --> K
    K --> L
    L -->|无依赖| O
    L -->|有依赖| M
    M -->|已发生| O
    M -->|未发生| N
    N --> P
    P --> Q
    Q --> R
    R -->|满足| S
    R -->|未满足| T
    S --> O
    O --> D

    style A fill:#ff9999
    style B fill:#ffcc99
    style E fill:#99ccff
    style F fill:#99ff99
    style G fill:#ff99ff
    style I fill:#ffff99
    style K fill:#99ffcc
    style N fill:#ff99cc
    style Q fill:#99ffff
```

---

## 2. 详细的蓝色→黄色→红色升级流程

```mermaid
graph LR
    subgraph "BLUE Level"
        B1["BLUE 评估任务"]
        B2["业务检测"]
        B3["时间条件"]
        B4["执行报警"]
    end

    subgraph "YELLOW Dependency Check"
        Y1["检查黄色依赖<br/>FIRST_BOREHOLE_START?"]
        Y2a["依赖已发生<br/>→ 立即创建YELLOW任务"]
        Y2b["依赖未发生<br/>→ 记录待机状态"]
    end

    subgraph "Waiting for External Event"
        W1["exception_event.pending_escalations<br/>YELLOW: WAITING"]
        W2["钻孔系统发布事件"]
        W3["AlertDependencyManager监听"]
        W4["检查待机升级条件"]
        W5["满足 → 创建YELLOW任务"]
    end

    subgraph "YELLOW Level"
        YL1["YELLOW 评估任务"]
        YL2["业务检测"]
        YL3["时间条件"]
        YL4["执行报警"]
    end

    subgraph "RED Level"
        R1["RED 评估任务"]
        R2["业务检测"]
        R3["时间条件"]
        R4["执行报警"]
    end

    B1 --> B2 --> B3 --> B4
    B4 --> Y1
    Y1 --> Y2a
    Y1 --> Y2b
    Y2a --> YL1
    Y2b --> W1
    W1 --> W2
    W2 --> W3
    W3 --> W4
    W4 --> W5
    W5 --> YL1
    YL1 --> YL2 --> YL3 --> YL4
    YL4 --> R1
    R1 --> R2 --> R3 --> R4

    style B4 fill:#ffcccc
    style YL4 fill:#ffff99
    style R4 fill:#ff9999
    style W1 fill:#ffffcc
    style W5 fill:#ccffcc
```

---

## 3. 事件发布订阅流程（Spring Event）

```mermaid
graph LR
    subgraph "钻孔系统<br/>BoreholService"
        B1["startFirstBorehole"]
        B2["创建 BoreholStartEvent"]
        B3["eventPublisher.publishEvent"]
    end

    subgraph "Spring Event Bus<br/>ApplicationEventPublisher"
        E["广播事件"]
    end

    subgraph "告警系统<br/>AlertDependencyManager"
        A1["@EventListener<br/>onAlertSystemEvent"]
        A2["更新 detectionContext<br/>FIRST_BOREHOLE_START_time"]
        A3["检查 pendingEscalations"]
        A4["触发待机升级"]
    end

    B1 --> B2
    B2 --> B3
    B3 --> E
    E --> A1
    A1 --> A2
    A2 --> A3
    A3 --> A4

    style B3 fill:#99ccff
    style E fill:#ffff99
    style A1 fill:#99ff99
    style A4 fill:#ff99cc
```

---

## 4. 数据库表及更新流程

```mermaid
graph TD
    subgraph "Input Tables"
        ET["exception_type<br/>检测逻辑: detectionLogicType<br/>检测配置: detectionConfig"]
        TC["trigger_condition<br/>触发类型: ABSOLUTE/RELATIVE/HYBRID<br/>时间表达: relativeEventType, absoluteTime等"]
        AR["alert_rule<br/>等级: BLUE/YELLOW/RED<br/>动作: actionType, actionConfig<br/>⭐ 新增: dependentEvents JSON"]
    end

    subgraph "Runtime Tables"
        EE["exception_event<br/>异常基本信息<br/>状态: status<br/>当前级别: currentAlertLevel<br/>⭐ 新增: detection_context (事件时间)<br/>⭐ 新增: pending_escalations (待机状态)"]
        AEL["alert_event_log<br/>报警日志<br/>记录每次触发"]
    end

    subgraph "Update Flow"
        U1["1️⃣ 初始化异常事件"]
        U2["2️⃣ 创建BLUE评估任务"]
        U3["3️⃣ BLUE触发 → 更新 currentAlertLevel=BLUE"]
        U4["4️⃣ 检查YELLOW依赖 → 记录 pending_escalations"]
        U5["5️⃣ 外部事件发生 → 更新 detection_context"]
        U6["6️⃣ 检查待机 → 满足则创建YELLOW任务"]
        U7["7️⃣ YELLOW触发 → 更新 currentAlertLevel=YELLOW"]
        U8["8️⃣ 循环直到RED或结束"]
    end

    ET --> U1
    TC --> U2
    AR --> U3
    AR --> U4
    EE --> U5
    EE --> U6
    AR --> U7
    U1 --> U2
    U2 --> U3
    U3 --> U4
    U4 --> U5
    U5 --> U6
    U6 --> U7
    U7 --> U8

    style EE fill:#ffffcc
    style AR fill:#ccffff
    style U4 fill:#ffcccc
    style U5 fill:#99ff99
    style U6 fill:#ccffcc
```

---

## 5. AlertRule 的 dependentEvents JSON 结构示例

```mermaid
graph TD
    AR["alert_rule<br/>id=2<br/>level=YELLOW"]
    
    JSON["dependentEvents = {<br/>  'events': [<br/>    {<br/>      'eventType': 'FIRST_BOREHOLE_START',<br/>      'delayMinutes': 120,<br/>      'required': true<br/>    },<br/>    {<br/>      'eventType': 'SHIFT_END',<br/>      'delayMinutes': 0,<br/>      'required': false<br/>    }<br/>  ],<br/>  'logicalOperator': 'AND',<br/>  'timeWindowStart': '06:00',<br/>  'timeWindowEnd': '18:00'<br/>}"]

    AR --> JSON

    style JSON fill:#e1f5ff
```

---

## 6. ExceptionEvent 的 pending_escalations JSON 结构示例

```mermaid
graph TD
    EE["exception_event<br/>id=123<br/>status=ACTIVE<br/>currentAlertLevel=BLUE"]

    JSON["pending_escalations = {<br/>  'YELLOW': {<br/>    'status': 'WAITING',<br/>    'dependencies': {<br/>      'FIRST_BOREHOLE_START': {<br/>        'required': true,<br/>        'occurred': false,<br/>        'delayMinutes': 120<br/>      }<br/>    },<br/>    'logicalOperator': 'AND',<br/>    'createdAt': '2025-12-12T10:00:00'<br/>  },<br/>  'RED': {<br/>    'status': 'NOT_READY'<br/>  }<br/>}"]

    EE --> JSON
    
    style JSON fill:#fff9c4
```

---

## 7. 时间线示例：完整的升级过程

```mermaid
timeline
    title 异常报警完整升级时间线
    
    10:00 : 钻探系统检测异常 : 创建 ExceptionEvent
    10:00 : 初始化蓝色评估 : 创建 TaskType.ALERT 任务
    10:02 : 蓝色评估执行 : 业务检测通过 → 时间条件满足 → 触发蓝色报警
    10:02 : 检查黄色依赖 : 需要等待 FIRST_BOREHOLE_START 事件
    10:02 : 记录待机状态 : exception_event.pending_escalations[YELLOW] = WAITING
    10:30 : 钻孔系统：第一个钻孔开始 : 发布 BoreholStartEvent 事件
    10:30 : 告警系统监听事件 : 更新 detection_context[FIRST_BOREHOLE_START_time]
    10:30 : 检查待机升级 : pending_escalations[YELLOW] 依赖满足
    10:30 : 创建黄色评估任务 : TaskScheduler 调度
    10:32 : 黄色评估执行 : 业务检测通过 → 时间条件满足 → 触发黄色报警
    10:32 : 检查红色依赖 : 无依赖或依赖已满足
    10:32 : 创建红色评估任务 : TaskScheduler 调度
    11:00 : 红色评估执行 : 业务检测通过 → 触发红色报警（最高级）
```

---

## 8. 核心组件交互图

```mermaid
graph TD
    subgraph "外部系统"
        BS["钻孔系统<br/>BoreholService"]
        OS["其他业务系统"]
    end

    subgraph "Spring Framework"
        AEP["ApplicationEventPublisher<br/>事件发布器"]
    end

    subgraph "告警核心服务"
        AES["AlertEscalationService<br/>升级编排"]
        ADM["AlertDependencyManager<br/>依赖管理<br/>@EventListener"]
        AE["AlertExecutor<br/>TaskExecutor实现<br/>业务检测+时间触发"]
        TF["TriggerStrategyFactory<br/>触发策略工厂"]
        DET["ExceptionDetectionStrategy<br/>业务检测策略"]
        AAE["AlertActionExecutor<br/>动作执行器"]
    end

    subgraph "数据访问层"
        EER["ExceptionEventRepository"]
        ARR["AlertRuleRepository"]
        TCR["TriggerConditionRepository"]
        ETR["ExceptionTypeRepository"]
    end

    subgraph "数据库"
        EET["exception_event"]
        ART["alert_rule"]
        TCT["trigger_condition"]
        ETT["exception_type"]
        AEL["alert_event_log"]
    end

    subgraph "任务调度系统"
        TS["TaskScheduler<br/>Simple/Quartz"]
    end

    BS -->|发布事件| AEP
    OS -->|发布事件| AEP
    AEP -->|广播| ADM
    ADM -->|创建任务| AES
    AES -->|查询规则| ARR
    AES -->|更新状态| EER
    AES -->|记录日志| AEL
    AES -->|提交任务| TS
    TS -->|执行| AE
    AE -->|查询配置| ETR
    AE -->|业务检测| DET
    AE -->|获取策略| TF
    AE -->|时间评估| TF
    AE -->|执行动作| AAE
    AE -->|检查依赖| ADM
    TF -->|查询条件| TCR
    AAE -->|记录日志| AEL
    EER -->|读写| EET
    ARR -->|读写| ART
    TCR -->|读写| TCT
    ETR -->|读写| ETT
    AEL -->|写入| AEL

    style AE fill:#99ccff
    style ADM fill:#99ff99
    style AES fill:#ffcc99
    style TS fill:#ff99ff
```

---

## 9. 关键数据结构汇总

### alert_rule 表
```
id | exceptionTypeId | triggerConditionId | level | actionType | actionConfig | dependent_events (JSON)
```

**dependent_events JSON**:
```json
{
  "events": [
    {
      "eventType": "FIRST_BOREHOLE_START",
      "delayMinutes": 120,
      "required": true
    }
  ],
  "logicalOperator": "AND"
}
```

### exception_event 表
```
id | exceptionTypeId | detectedAt | detection_context (JSON) | currentAlertLevel | status | pending_escalations (JSON)
```

**detection_context JSON** (实时更新):
```json
{
  "shift_start_time": "2025-12-12T06:00:00",
  "FIRST_BOREHOLE_START_time": "2025-12-12T10:30:00",
  "boreholeNumber": 1,
  "location": "XX矿井"
}
```

**pending_escalations JSON** (待机状态):
```json
{
  "YELLOW": {
    "status": "WAITING",
    "dependencies": [
      {
        "eventType": "FIRST_BOREHOLE_START",
        "delayMinutes": 120,
        "required": true
      }
    ],
    "logicalOperator": "AND",
    "createdAt": "2025-12-12T10:02:00"
  },
  "RED": {
    "status": "READY",
    "readyAt": "2025-12-12T10:30:00",
    "scheduledTime": "2025-12-12T12:30:00",
    "taskId": "67890",
    "dependencies": [],
    "logicalOperator": "AND"
  }
}
```

**字段说明**:
- `status`: WAITING(等待依赖) | READY(已调度) | COMPLETED(已执行)
- `readyAt`: 依赖满足时间
- `scheduledTime`: 计划执行时间（考虑延迟）
- `taskId`: 调度系统中的任务ID，用于取消任务

---

## 核心流程总结

### 🔵 蓝色阶段
1. 异常检测系统检测到异常 → 创建 ExceptionEvent
2. 调用 `scheduleInitialEvaluation` → 为 BLUE 创建评估任务
3. 任务执行：业务检测 → 时间条件评估 → 触发报警动作
4. 记录 AlertEventLog

### 🟡 黄色阶段
1. 检查 alert_rule[YELLOW].dependentEvents
2. 如果有依赖事件且**未发生** → 记录 pending_escalations[YELLOW] = WAITING
3. 如果依赖**已发生** → 直接创建 YELLOW 评估任务
4. 外部系统发布事件 → Spring 事件总线 → AlertDependencyManager 监听
5. 更新 detection_context 记录事件时间
6. 检查 pending_escalations → 满足条件 → 创建 YELLOW 评估任务

---

## 10. 报警解除流程图

```mermaid
graph LR
    subgraph "触发解除"
        T1["🎯 解除触发源<br/>1. 用户手动点击<br/>2. 业务系统自动检测<br/>3. 管理员系统操作"]
        T2["调用 AlertResolutionService<br/>resolveAlert"]
    end

    subgraph "解除过程"
        P1["1️⃣ 检查当前状态<br/>如果已RESOLVED，直接返回"]
        P2["2️⃣ 转换为 RESOLVING<br/>status = RESOLVING<br/>防止中途系统崩溃"]
        P3["3️⃣ 查询待机任务<br/>从 PENDING_TASK_MAP"]
        P4["4️⃣ 取消所有任务<br/>taskManagementService.cancelTask"]
        P5["5️⃣ 记录任务取消日志<br/>event_type = TASK_CANCELLED"]
        P6["6️⃣ 清除任务映射<br/>clearPendingTasks"]
    end

    subgraph "最终状态"
        F1["7️⃣ 转换为 RESOLVED<br/>status = RESOLVED<br/>resolved_at = NOW<br/>resolution_reason<br/>resolution_source"]
        F2["8️⃣ 记录解除事件日志<br/>event_type = ALERT_RESOLVED"]
        F3["9️⃣ 发布解除事件<br/>AlertResolutionEvent"]
    end

    T1 --> T2
    T2 --> P1
    P1 --> P2
    P2 --> P3
    P3 --> P4
    P4 --> P5
    P5 --> P6
    P6 --> F1
    F1 --> F2
    F2 --> F3

    style T2 fill:#ffcccc
    style P2 fill:#fff4e6
    style F1 fill:#ccffcc
    style F3 fill:#99ccff
```

---

## 11. 系统启动恢复机制

```mermaid
graph TD
    START["🚀 应用启动<br/>ApplicationReadyEvent"]
    
    subgraph "恢复逻辑"
        R1["1️⃣ 查询 ACTIVE 异常<br/>status = ACTIVE<br/>过滤有 WAITING/READY 状态"]
        R2["查到异常列表"]
        R3["2️⃣ 逐个恢复异常"]
        R4["3️⃣ 对每个异常检查<br/>pending_escalations"]
        R5["4️⃣ 重新调度待机任务<br/>scheduleEscalationEvaluation"]
        R6["5️⃣ 基于状态判断<br/>pending_escalations<br/>无需标记字段"]
        R7["6️⃣ 发布恢复事件<br/>AlertRecoveredEvent"]
    end

    subgraph "处理RESOLVING异常"
        RS1["7️⃣ 查询 RESOLVING 异常<br/>status = RESOLVING"]
        RS2["系统中途解除时崩溃"]
        RS3["8️⃣ 完成解除过程<br/>status = RESOLVED"]
    end

    subgraph "恢复完成"
        END["✅ 告警系统恢复完成<br/>所有待机任务已重新调度<br/>系统可以正常运行"]
    end

    START --> R1
    R1 --> R2
    R2 --> R3
    R3 --> R4
    R4 --> R5
    R5 --> R6
    R6 --> R7
    R7 --> RS1
    RS1 --> RS2
    RS2 --> RS3
    RS3 --> END

    style START fill:#ffcccc
    style R5 fill:#ccffcc
    style RS3 fill:#fff4e6
    style END fill:#99ccff
```

---

## 12. 异常事件完整生命周期

```mermaid
stateDiagram-v2
    [*] --> DETECTED: 1. 异常检测
    
    DETECTED --> EVALUATING_BLUE: 2. 创建BLUE评估任务
    
    EVALUATING_BLUE --> BLUE_TRIGGERED: 3. BLUE触发报警
    EVALUATING_BLUE --> EVALUATING_BLUE: 等待条件满足
    
    BLUE_TRIGGERED --> CHECK_DEPENDENCIES: 4. 检查下一级依赖
    
    CHECK_DEPENDENCIES --> WAITING_EVENT: 5. 依赖未发生，待机
    CHECK_DEPENDENCIES --> EVALUATING_YELLOW: 5b. 依赖已发生，创建任务
    
    WAITING_EVENT --> EXTERNAL_EVENT: 6. 监听外部事件
    EXTERNAL_EVENT --> EVALUATING_YELLOW: 7. 事件发生，创建任务
    
    EVALUATING_YELLOW --> YELLOW_TRIGGERED: 8. YELLOW触发报警
    EVALUATING_YELLOW --> YELLOW_TRIGGERED: 同样流程...
    
    YELLOW_TRIGGERED --> CHECK_HIGHER_LEVEL: 9. 检查更高级别
    CHECK_HIGHER_LEVEL --> EVALUATING_LEVEL3: 10. 继续升级
    
    EVALUATING_LEVEL3 --> LEVEL3_TRIGGERED: 11. 最高级触发
    
    BLUE_TRIGGERED --> RESOLUTION_INITIATED: 12a. 用户解除
    YELLOW_TRIGGERED --> RESOLUTION_INITIATED: 12b. 自动恢复
    LEVEL3_TRIGGERED --> RESOLUTION_INITIATED: 12c. 管理员取消
    WAITING_EVENT --> RESOLUTION_INITIATED: 12d. 从待机状态解除
    
    RESOLUTION_INITIATED --> RESOLVING: 13. 转换为RESOLVING<br/>防止中途崩溃
    RESOLVING --> TASK_CANCELLING: 14. 取消所有待机任务
    TASK_CANCELLING --> RESOLVED: 15. 最终状态RESOLVED
    
    RESOLVED --> [*]: 16. 异常解除完成<br/>生命周期结束
    
    note right of DETECTED
        创建异常事件
        初始化上下文
    end note
    
    note right of WAITING_EVENT
        记录待机状态
        等待外部事件
        支持系统重启恢复
    end note
    
    note right of RESOLVING
        中间状态，防止崩溃
        确保原子性操作
    end note
    
    note right of RESOLVED
        保留所有审计日志
        永久记录生命周期
    end note
```

---

## 13. 数据库字段变更汇总

### exception_event 表扩展字段

| 字段名 | 数据类型 | 说明 | 用途 |
|-------|---------|------|------|
| `status` | VARCHAR(20) | ACTIVE/RESOLVING/RESOLVED | 异常当前状态 |
| `resolved_at` | DATETIME | 解除时间 | 审计日志 |
| `resolution_reason` | VARCHAR(500) | 解除原因 | 审计和追溯 |
| `resolution_source` | VARCHAR(50) | MANUAL/AUTO/SYSTEM | 解除来源 |
| `recovery_flag` | BOOLEAN | true/false | 启动恢复标志 |
| `pending_escalations` | JSON | 待机状态 | 系统重启恢复 |
| `detection_context` | JSON | 事件时间记录 | 依赖计算 |

### alert_event_log 表扩展字段

| 字段名 | 数据类型 | 说明 | 示例值 |
|-------|---------|------|--------|
| `event_type` | VARCHAR(50) | 事件类型 | ALERT_TRIGGERED / ALERT_RESOLVED / TASK_CANCELLED |

---

## 14. 接口汇总

### 报警解除相关接口

```
POST /api/alert/resolution/resolve
  参数：exceptionEventId, resolutionSource, resolutionReason
  说明：通用报警解除接口

POST /api/alert/resolution/manual-resolve
  参数：exceptionEventId, reason
  说明：手动解除报警（用户点击）

POST /api/alert/resolution/auto-recovery
  参数：exceptionEventId, recoveryReason
  说明：自动恢复报警（业务系统触发）

POST /api/alert/resolution/system-cancel
  参数：exceptionEventId, cancellationReason
  说明：系统取消报警（管理员操作）
```

---

## 完整设计确认

### ✅ 已确认的设计决策

1. **动态等级配置** - ✅ 前端配置，后端通过枚举支持 LEVEL_1 到 LEVEL_N
2. **通用解除接口** - ✅ 支持手动、自动、系统三种解除方式
3. **原子性保证** - ✅ 使用 RESOLVING 中间状态防止系统中途崩溃
4. **任务取消机制** - ✅ 调用 taskManagementService.cancelTask() 取消所有待机任务
5. **审计日志** - ✅ alert_event_log 永久保留所有状态变更事件
6. **系统启动恢复** - ✅ ApplicationReadyEvent 时扫描 ACTIVE 异常，重新调度待机任务
7. **事件驱动** - ✅ Spring ApplicationEvent 发布报警解除/恢复事件

### 核心特性

| 特性 | 实现方式 | 关键类 |
|------|--------|--------|
| **报警解除** | 转换为RESOLVING→取消任务→转换为RESOLVED | AlertResolutionService |
| **系统恢复** | 应用启动时扫描ACTIVE异常，重新调度 | AlertRecoveryService |
| **任务跟踪** | Map<Long, List<String>> 记录 exceptionEventId→taskIds | AlertEscalationService |
| **日志追踪** | 记录所有事件：ALERT_TRIGGERED/RESOLVED/TASK_CANCELLED | AlertEventLog |
| **故障恢复** | recovery_flag 防止重复恢复，RESOLVING 防止中途崩溃 | ExceptionEvent |

```

