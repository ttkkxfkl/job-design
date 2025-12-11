# 报警规则系统 - 使用指南

## 概述

报警规则系统是一个独立的 `alert` 模块，集成在任务调度框架中。它通过计算触发条件，精确时间提交评估任务到调度系统，实现报警等级的逐步升级。

### 核心特性

- ✅ **等级逐步升级**：从BLUE → YELLOW → RED，每个等级独立评估
- ✅ **灵活的触发条件**：绝对时间、相对时间、混合条件
- ✅ **精确的时间计算**：无需轮询，直接计算下次评估时间
- ✅ **完整的审计日志**：记录每次升级的时间和原因
- ✅ **易于扩展**：支持自定义异常检测和报警动作

---

## 架构设计

```
Report Exception Event (异常事件报告)
    ↓
Calculate Next Evaluation Time (计算下次评估时间)
    ↓
Create ScheduledTask (创建定时任务)
    ↓
Task Scheduler (任务调度系统)
    ↓
AlertExecutor (报警执行器)
    ↓
Evaluate Trigger Condition (评估触发条件)
    ├─ Yes → Execute Alert Action (执行报警动作)
    │         └─ Schedule Next Level (为更高等级创建任务)
    └─ No  → Wait for Next Evaluation Time (继续等待)
```

---

## 数据模型

### 1. 异常类型 (exception_type)

定义不同的异常类型及其检测逻辑。

```java
ExceptionType {
  id: 1,
  name: "入井记录不足",
  description: "班次内没有入井操作记录",
  detection_logic_type: "RECORD_CHECK",
  detection_config: {
    "table": "work_record",
    "condition": "shift_id = ? AND action_type = 'ENTRY'"
  }
}
```

### 2. 触发条件 (trigger_condition)

定义报警的触发时机。

#### 2.1 绝对时间触发
```java
TriggerCondition {
  id: 1,
  condition_type: "ABSOLUTE",
  absolute_time: "16:00",      // 每天16:00
  time_window_start: "08:00",   // 可选：只在工作时间
  time_window_end: "18:00"
}
```

#### 2.2 相对时间触发
```java
TriggerCondition {
  id: 2,
  condition_type: "RELATIVE",
  relative_event_type: "SHIFT_START",  // 班次开始
  relative_duration_minutes: 480        // 480分钟 = 8小时
}
```

#### 2.3 混合条件
```java
TriggerCondition {
  id: 3,
  condition_type: "HYBRID",
  logical_operator: "AND",
  combined_condition_ids: "1,2"  // 同时满足条件1和条件2
}
```

### 3. 报警规则 (alert_rule)

定义异常的各等级报警规则。

```java
AlertRule {
  id: 1,
  exception_type_id: 1,
  level: "BLUE",                 // 蓝色预警（最低）
  trigger_condition_id: 1,
  action_type: "LOG",            // 动作类型
  action_config: {               // 动作配置
    "level": "WARN"
  },
  priority: 5
}

AlertRule {
  id: 2,
  exception_type_id: 1,
  level: "YELLOW",               // 黄色预警（中等）
  trigger_condition_id: 2,
  action_type: "EMAIL",
  action_config: {
    "recipients": ["supervisor@example.com"],
    "subject": "异常预警"
  },
  priority: 6
}

AlertRule {
  id: 3,
  exception_type_id: 1,
  level: "RED",                  // 红色警告（最高）
  trigger_condition_id: 3,
  action_type: "SMS",
  action_config: {
    "phone_numbers": ["13800138000"],
    "message_template": "%s级别异常"
  },
  priority: 8
}
```

### 4. 异常事件 (exception_event)

记录检测到的异常及其当前状态。

```java
ExceptionEvent {
  id: 1,
  exception_type_id: 1,
  detected_at: "2024-12-11T14:30:00",
  detection_context: {
    "shift_id": 123,
    "shift_start_time": "2024-12-11T08:00:00",
    "operator": "张三"
  },
  current_alert_level: "NONE",   // 当前等级
  status: "ACTIVE",
  created_at: "2024-12-11T14:30:00"
}
```

### 5. 报警事件日志 (alert_event_log)

审计日志，记录每次升级。

```java
AlertEventLog {
  id: 1,
  exception_event_id: 1,
  alert_rule_id: 1,
  triggered_at: "2024-12-11T16:00:00",
  alert_level: "BLUE",
  trigger_reason: "触发条件已满足",
  action_status: "SENT"
}
```

---

## 工作流示例

### 场景：入井记录不足报警

**初始条件：**
- 异常类型：入井记录不足
- BLUE报警：每天16:00触发
- YELLOW报警：班次开始8小时后触发
- RED报警：班次开始12小时后触发
- 班次时间：08:00-17:00

**时间轴：**

```
14:30  异常被检测到（班次进行中，没有入井记录）
       ├─ 创建异常事件
       ├─ 计算 BLUE 触发时间 → 2024-12-11 16:00:00
       └─ 创建评估任务A（执行时间=16:00）

16:00  评估任务A 触发（评估 BLUE 条件）
       ├─ shouldTrigger() = true ✅
       ├─ 记录到 alert_event_log（BLUE 已触发）
       ├─ 执行日志输出（LOG动作）
       ├─ 更新 exception_event.current_alert_level = "BLUE"
       └─ 计算 YELLOW 触发时间 → 2024-12-11 16:00:00（班次开始+8h）
          └─ 创建评估任务B

16:00  评估任务B 触发（评估 YELLOW 条件）
       ├─ shouldTrigger() = true ✅
       ├─ 记录到 alert_event_log（YELLOW 已触发）
       ├─ 执行邮件通知（EMAIL动作）
       ├─ 更新 exception_event.current_alert_level = "YELLOW"
       └─ 计算 RED 触发时间 → 2024-12-11 20:00:00（班次开始+12h）
          └─ 创建评估任务C

20:00  评估任务C 触发（评估 RED 条件）
       ├─ shouldTrigger() = true ✅
       ├─ 记录到 alert_event_log（RED 已触发）
       ├─ 执行短信通知（SMS动作）
       ├─ 更新 exception_event.current_alert_level = "RED"
       └─ 没有更高等级，完成升级
```

---

## API 使用示例

### 1. 创建异常类型

```bash
curl -X POST http://localhost:8080/api/alert/exception-type \
  -H "Content-Type: application/json" \
  -d '{
    "name": "入井记录不足",
    "description": "班次内没有入井操作记录",
    "detectionLogicType": "RECORD_CHECK",
    "detectionConfig": {
      "table": "work_record",
      "condition": "shift_id = ? AND action_type = \"ENTRY\""
    }
  }'
```

**响应：**
```json
{
  "code": 0,
  "message": "success",
  "data": {
    "id": 1,
    "name": "入井记录不足",
    ...
  }
}
```

### 2. 创建触发条件

#### 绝对时间（16:00）
```bash
curl -X POST http://localhost:8080/api/alert/trigger-condition \
  -H "Content-Type: application/json" \
  -d '{
    "conditionType": "ABSOLUTE",
    "absoluteTime": "16:00:00"
  }'
```

#### 相对时间（班次开始+8小时）
```bash
curl -X POST http://localhost:8080/api/alert/trigger-condition \
  -H "Content-Type: application/json" \
  -d '{
    "conditionType": "RELATIVE",
    "relativeEventType": "SHIFT_START",
    "relativeDurationMinutes": 480
  }'
```

#### 混合条件
```bash
curl -X POST http://localhost:8080/api/alert/trigger-condition \
  -H "Content-Type: application/json" \
  -d '{
    "conditionType": "HYBRID",
    "logicalOperator": "AND",
    "combinedConditionIds": "1,2"
  }'
```

### 3. 创建报警规则

```bash
curl -X POST http://localhost:8080/api/alert/rule \
  -H "Content-Type: application/json" \
  -d '{
    "exceptionTypeId": 1,
    "level": "BLUE",
    "triggerConditionId": 1,
    "actionType": "LOG",
    "actionConfig": {
      "level": "WARN"
    },
    "priority": 5,
    "enabled": true
  }'
```

### 4. 报告异常事件

```bash
curl -X POST http://localhost:8080/api/alert/event \
  -H "Content-Type: application/json" \
  -d '{
    "exceptionTypeId": 1,
    "detectionContext": {
      "shift_id": 123,
      "shift_start_time": "2024-12-11T08:00:00",
      "operator": "张三"
    }
  }'
```

**响应：**
```json
{
  "code": 0,
  "message": "success",
  "data": {
    "id": 1,
    "exceptionTypeId": 1,
    "detectedAt": "2024-12-11T14:30:00",
    "currentAlertLevel": "NONE",
    "status": "ACTIVE",
    ...
  }
}
```

### 5. 查询异常事件详情

```bash
curl -X GET http://localhost:8080/api/alert/event/1
```

**响应：**
```json
{
  "code": 0,
  "message": "success",
  "data": {
    "event": {
      "id": 1,
      "exceptionTypeId": 1,
      "currentAlertLevel": "RED",
      "status": "ACTIVE",
      ...
    },
    "logs": [
      {
        "id": 1,
        "alertLevel": "BLUE",
        "triggeredAt": "2024-12-11T16:00:00",
        "triggerReason": "触发条件已满足"
      },
      {
        "id": 2,
        "alertLevel": "YELLOW",
        "triggeredAt": "2024-12-11T16:00:00",
        "triggerReason": "触发条件已满足"
      },
      {
        "id": 3,
        "alertLevel": "RED",
        "triggeredAt": "2024-12-11T20:00:00",
        "triggerReason": "触发条件已满足"
      }
    ]
  }
}
```

### 6. 解决异常事件

```bash
curl -X PUT http://localhost:8080/api/alert/event/1/resolve
```

---

## 扩展指南

### 1. 添加新的异常检测策略

创建一个实现 `ExceptionDetectionStrategy` 的新类：

```java
@Component("customDetector")
public class CustomExceptionDetector implements ExceptionDetectionStrategy {
    
    @Override
    public boolean detect(Map<String, Object> config, Map<String, Object> context) {
        // 实现你的检测逻辑
        return false;
    }
    
    @Override
    public String getStrategyName() {
        return "CUSTOM";
    }
}
```

### 2. 添加新的报警动作

创建一个实现 `AlertActionExecutor` 的新类：

```java
@Component
public class WebhookAlertAction implements AlertActionExecutor {
    
    @Override
    public void execute(Map<String, Object> actionConfig, ExceptionEvent event, AlertRule rule) throws Exception {
        // 实现你的动作逻辑（如发送HTTP请求）
    }
    
    @Override
    public boolean supports(String actionType) {
        return "WEBHOOK".equalsIgnoreCase(actionType);
    }
    
    @Override
    public String getActionType() {
        return "WEBHOOK";
    }
}
```

### 3. 添加新的触发条件类型

创建一个实现 `TriggerStrategy` 的新类，并在 `TriggerStrategyFactory` 中注册。

---

## 常见问题

### Q1: 如果异常在评估前就被解决了怎么办？

A: 在评估时会检查异常事件的 `status` 字段，如果已是 `RESOLVED` 则直接跳过。

### Q2: 同一个异常会创建多少个 ScheduledTask？

A: 取决于报警规则数量。如果有3个等级的规则，最多会有3个评估任务（一次一个）。

### Q3: 如何查看所有的升级历史？

A: 通过 `/api/alert/event/{eventId}` 可以获取该事件的所有 `alert_event_log` 记录。

### Q4: 报警动作失败怎么办？

A: 系统会记录错误信息到 `alert_event_log.action_error_message`，暂不自动重试（可根据需要扩展）。

---

## 数据库初始化

执行以下SQL脚本初始化表结构：

```bash
mysql -u root -p scheduled_task < src/main/resources/alert-schema.sql
```

---

## 总结

报警规则系统通过以下方式实现灵活的报警管理：

1. **清晰的分层**：异常检测 → 触发条件评估 → 报警动作执行
2. **精确的时间计算**：避免轮询，直接计算下次评估时间
3. **逐步的等级升级**：每个等级独立评估，依次升级
4. **完整的审计日志**：每次升级都有记录可追溯
5. **高度可扩展**：易于添加新的检测策略和报警动作

希望这个系统能够满足你的业务需求！
