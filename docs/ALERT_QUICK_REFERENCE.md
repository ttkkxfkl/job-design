# 报警规则系统 - 快速参考

## 概览

```
异常检测 → 计算时间 → 创建任务 → 定时评估 → 升级报警
```

## 核心概念

| 概念 | 说明 | 例子 |
|-----|------|------|
| **异常类型** | 定义异常和检测方式 | 入井记录不足 |
| **触发条件** | 定义何时触发报警 | 每天16:00 或 班次+8小时 |
| **报警规则** | 异常+条件+动作的组合 | 入井不足在16:00时日志输出 |
| **异常事件** | 检测到的一个具体异常实例 | 2024-12-11 14:30 发现的一个异常 |
| **报警日志** | 记录每次升级 | BLUE在16:00触发，YELLOW在16:00触发... |

## API 快速调用

### 创建异常类型
```bash
POST /api/alert/exception-type
{
  "name": "异常名称",
  "detectionLogicType": "RECORD_CHECK",
  "detectionConfig": { ... }
}
```

### 创建触发条件
```bash
POST /api/alert/trigger-condition

# 绝对时间
{ "conditionType": "ABSOLUTE", "absoluteTime": "16:00:00" }

# 相对时间
{ 
  "conditionType": "RELATIVE",
  "relativeEventType": "SHIFT_START",
  "relativeDurationMinutes": 480
}
```

### 创建报警规则
```bash
POST /api/alert/rule
{
  "exceptionTypeId": 1,
  "level": "BLUE",
  "triggerConditionId": 1,
  "actionType": "LOG",
  "priority": 5
}
```

### 报告异常事件
```bash
POST /api/alert/event
{
  "exceptionTypeId": 1,
  "detectionContext": {
    "shift_id": 123,
    "shift_start_time": "2024-12-11T08:00:00"
  }
}
```

### 查询异常详情
```bash
GET /api/alert/event/1
```

### 解决异常
```bash
PUT /api/alert/event/1/resolve
```

## 工作流

```
Step 1: 创建异常类型
  └─ detectionLogicType: RECORD_CHECK / TIME_CHECK / CUSTOM

Step 2: 创建触发条件（每个等级一个）
  ├─ ABSOLUTE: absoluteTime = "16:00"
  ├─ RELATIVE: relativeEventType = "SHIFT_START", duration = 480分钟
  └─ HYBRID: combinedConditionIds = "1,2", logicalOperator = "AND"

Step 3: 创建报警规则（每个等级一个）
  ├─ level: BLUE / YELLOW / RED
  ├─ actionType: LOG / EMAIL / SMS / WEBHOOK
  └─ actionConfig: {...}

Step 4: 报告异常事件
  └─ 系统自动创建评估任务，等待触发

Step 5: 系统自动升级
  ├─ BLUE 条件满足 → 执行 LOG 动作
  ├─ 创建 YELLOW 评估任务
  ├─ YELLOW 条件满足 → 执行 EMAIL 动作
  ├─ 创建 RED 评估任务
  ├─ RED 条件满足 → 执行 SMS 动作
  └─ 完成

Step 6: 解决异常
  └─ PUT /api/alert/event/{id}/resolve
```

## 数据库表

```sql
exception_type          -- 异常类型定义
trigger_condition       -- 触发条件
alert_rule             -- 报警规则
exception_event        -- 异常事件（当前状态）
alert_event_log        -- 报警日志（历史记录）
```

## 关键类

| 类 | 作用 |
|----|------|
| `ExceptionType` | 异常定义 |
| `TriggerCondition` | 触发条件 |
| `AlertRule` | 报警规则 |
| `ExceptionEvent` | 异常事件 |
| `AlertEventLog` | 报警日志 |
| `TriggerStrategy` | 触发策略接口 |
| `AlertActionExecutor` | 报警动作执行器接口 |
| `AlertEscalationService` | 升级管理 |
| `AlertExecutor` | 评估执行器（核心） |

## 触发条件类型

### ABSOLUTE（绝对时间）
```java
absoluteTime = "16:00"
// 每天16:00触发
// 可选：timeWindowStart/timeWindowEnd 限制时间窗口
```

### RELATIVE（相对时间）
```java
relativeEventType = "SHIFT_START"  // 或 "LAST_OPERATION", "EXCEPTION_DETECTED"
relativeDurationMinutes = 480      // N分钟后
// 班次开始8小时后触发
```

### HYBRID（混合）
```java
combinedConditionIds = "1,2,3"
logicalOperator = "AND"  // 或 "OR"
// 多个条件组合判断
```

## 报警动作

| 类型 | 配置 | 说明 |
|-----|------|------|
| LOG | `{"level": "WARN"}` | 日志输出 |
| EMAIL | `{"recipients": [...], "subject": "..."}` | 邮件 |
| SMS | `{"phone_numbers": [...], "message_template": "..."}` | 短信 |
| WEBHOOK | `{"url": "...", "method": "POST"}` | 网络钩子 |

## 文件位置

```
alert/
├── entity/           -- 5个实体类
├── repository/       -- 5个Repository
├── trigger/          -- 触发策略
├── detection/        -- 异常检测
├── action/           -- 报警动作
├── executor/         -- AlertExecutor
├── service/          -- 业务服务
└── controller/       -- REST API

resources/
├── alert-schema.sql           -- 建表脚本
└── alert-init-example.sql     -- 初始化示例

docs/
├── ALERT_README.md            -- 系统概览
├── ALERT_SYSTEM_GUIDE.md      -- 使用指南
├── ALERT_INTEGRATION.md       -- 集成说明
└── ALERT_SUMMARY.md           -- 本文件
```

## 初始化步骤

```bash
# 1. 创建表
mysql < src/main/resources/alert-schema.sql

# 2. 插入示例数据（可选）
mysql < src/main/resources/alert-init-example.sql

# 3. 启动应用
mvn spring-boot:run

# 4. 测试API
curl -X GET http://localhost:8080/api/alert/exception-types
```

## 常见问题

**Q: 异常在评估前被解决了怎么办？**  
A: 评估时检查status，如果是RESOLVED则跳过。

**Q: 如何查看升级历史？**  
A: `GET /api/alert/event/{id}` 返回所有alert_event_log记录。

**Q: 如何添加新动作类型？**  
A: 实现AlertActionExecutor接口并加@Component注解。

**Q: 支持多少个并发异常？**  
A: 无限制，每个异常最多只有等级数量个并发任务。

## 时间表达参考

| 表达式 | 含义 |
|-------|------|
| `absoluteTime = "16:00"` | 每天16:00 |
| `relativeEventType = "SHIFT_START", duration = 0` | 班次开始立即 |
| `relativeEventType = "SHIFT_START", duration = 480` | 班次开始后8小时 |
| `relativeEventType = "EXCEPTION_DETECTED", duration = 30` | 异常发现后30分钟 |

## SQL 快速查询

```sql
-- 查看活跃异常
SELECT * FROM exception_event WHERE status = 'ACTIVE';

-- 查看某异常的升级历史
SELECT * FROM alert_event_log 
WHERE exception_event_id = 1 
ORDER BY triggered_at ASC;

-- 查看某类型的所有规则
SELECT * FROM alert_rule 
WHERE exception_type_id = 1 
ORDER BY FIELD(level, 'BLUE', 'YELLOW', 'RED');

-- 查看未来7天的评估任务
SELECT * FROM scheduled_task 
WHERE task_type = 'ALERT' 
AND execute_time BETWEEN NOW() AND DATE_ADD(NOW(), INTERVAL 7 DAY)
ORDER BY execute_time;
```

## 设计原则

✅ **等级逐步升级** - 不是同时创建所有等级任务  
✅ **精确时间计算** - 不是定期轮询  
✅ **与框架融合** - 复用TaskScheduler和TaskExecutor  
✅ **完整审计** - 每次升级都有日志记录  
✅ **高度可扩展** - 易添加新类型和新动作  

---

**更多详情**见 ALERT_SYSTEM_GUIDE.md

**集成问题**见 ALERT_INTEGRATION.md

**项目概览**见 ALERT_README.md
