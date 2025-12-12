# 报警规则系统

一个灵活、可扩展的报警规则引擎，集成在任务调度框架中，支持报警等级的逐步升级。

## 快速概览

### 核心概念

```
异常类型 (Exception Type)
    ↓
异常事件 (Exception Event) ← 异常被检测到
    ↓
报警规则 (Alert Rule) ← 使用 trigger_condition JSON 字段
    ├─ 计算下次评估时间
    ├─ 检查依赖事件 (dependent_events)
    └─ 创建 ScheduledTask
    ↓
任务调度系统 (Task Scheduler)
    ↓
报警执行器 (Alert Executor)
    ├─ 评估触发条件
    ├─ 执行报警动作
    ├─ 为下一等级创建任务
    └─ 或解除异常 (AlertResolutionService)
```

### 工作流示例

假设定义了一个异常：**入井记录不足**，有三个等级的报警规则：

```
14:30  ┌─ 异常事件创建
       ├─ 查询最低等级规则 (LEVEL_1)
       ├─ 计算触发时间（根据 trigger_condition）
       └─ 创建评估任务A，持久化 taskId

16:00  ┌─ 评估任务A 执行
       ├─ 幂等性检查（防止重复执行）
       ├─ 业务检测通过 ✓
       ├─ 触发条件满足 ✓
       ├─ 执行 LOG 动作
       ├─ 检查 LEVEL_2 依赖事件
       └─ 创建 LEVEL_2 评估任务B

18:00  ┌─ 依赖事件发生（FIRST_BOREHOLE_START）
       ├─ AlertDependencyManager 处理
       ├─ 更新 pending_escalations 状态
       └─ 重新调度 LEVEL_2 任务

20:00  ┌─ 评估任务B 执行
       ├─ 幂等性检查通过
       ├─ 执行 EMAIL 动作
       └─ 继续下一等级或异常解除
```

## 核心特性

✅ **等级逐步升级** - 从 LEVEL_1 → LEVEL_2 → LEVEL_3，每次只升级一个等级

✅ **精确时间计算** - 根据 trigger_condition JSON 精确计算下次评估时间，无需轮询

✅ **依赖事件管理** - 支持等级依赖业务事件（dependent_events），延迟触发

✅ **异常解除机制** - 检测到业务条件恢复时自动解除，取消所有待机任务

✅ **系统恢复能力** - 服务重启后自动恢复未完成的告警流程，防止 Quartz 持久化冲突

✅ **完整审计日志** - 记录每次升级、解除、任务取消的时间和原因

✅ **多样化报警动作** - 支持LOG、EMAIL、SMS、WEBHOOK等

✅ **事件驱动架构** - 基于 Spring ApplicationEvent 的松耦合设计

✅ **高度可扩展** - 易于添加新的异常检测策略和报警动作

✅ **幂等性保护** - 防止任务重复执行，安全可靠

✅ **与调度框架融合** - 复用现有的任务调度、分布式锁、执行日志等

## 包结构

```
alert/
├── entity/                    # 数据实体
│   ├── ExceptionType          # 异常类型
│   ├── AlertRule              # 报警规则（包含 trigger_condition JSON）
│   ├── ExceptionEvent         # 异常事件（包含 pending_escalations JSON）
│   ├── AlertEventLog          # 报警事件日志
│   └── DailyTaskStatistics    # 每日统计
│
├── repository/                # 数据访问层
│   ├── ExceptionTypeRepository
│   ├── AlertRuleRepository
│   ├── ExceptionEventRepository
│   ├── AlertEventLogRepository
│   └── DailyTaskStatisticsRepository
│
├── enums/                     # 枚举类型
│   ├── ExceptionStatus        # 异常状态（ACTIVE/RESOLVING/RESOLVED）
│   └── AlertEventType         # 事件类型（TRIGGERED/RESOLVED/CANCELLED）
│
├── event/                     # Spring 事件系统
│   ├── AlertSystemEvent       # 事件基类
│   ├── AlertTriggeredEvent    # 告警触发事件
│   ├── AlertResolvedEvent     # 告警解除事件
│   ├── AlertRecoveredEvent    # 系统恢复事件
│   └── DependencyEventOccurred # 依赖事件发生
│
├── trigger/                   # 触发条件评估
│   ├── TriggerStrategy        # 策略接口
│   ├── TriggerStrategyFactory # 工厂类
│   └── strategy/
│       ├── TimeTrigger            # 时间触发
│       └── ConditionTrigger       # 条件触发
│
├── detection/                 # 异常检测
│   ├── ExceptionDetectionStrategy # 策略接口
│   └── impl/
│       └── RecordCheckDetector    # 记录检查
│
├── action/                    # 报警动作
│   ├── AlertActionExecutor    # 执行器接口
│   └── impl/
│       ├── LogAlertAction         # 日志输出
│       ├── EmailAlertAction       # 邮件通知
│       └── SmsAlertAction         # 短信通知
│
├── executor/                  # 集成到调度框架
│   └── AlertExecutor          # 实现 TaskExecutor（含幂等性检查）
│
├── service/                   # 业务逻辑（核心）
│   ├── AlertEscalationService     # 升级管理（任务创建、ID持久化）
│   ├── AlertResolutionService     # 解除管理（任务取消、状态转换）
│   ├── AlertDependencyManager     # 依赖事件管理
│   └── AlertRecoveryService       # 系统恢复（启动时清理旧任务）
│
└── controller/                # API 接口
    └── AlertRuleController    # REST API
```

## 数据库表

- `exception_type` - 异常类型定义
- `alert_rule` - 报警规则（包含 trigger_condition 和 dependent_events JSON 字段）
- `exception_event` - 异常事件（包含 pending_escalations JSON 字段，存储任务ID）
- `alert_event_log` - 报警日志（审计：TRIGGERED/RESOLVED/CANCELLED）
- `daily_task_statistics` - 每日任务统计

详见 [ALERT_DB_SCHEMA.md](ALERT_DB_SCHEMA.md) 和数据库迁移脚本

## 快速开始

### 1. 初始化数据库

```bash
mysql -u root -p scheduled_task < src/main/resources/alert-schema.sql
```

### 2. 创建异常类型

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

### 3. 创建触发条件和报警规则

```bash
# 创建 BLUE 等级的触发条件（每天16:00）
curl -X POST http://localhost:8080/api/alert/trigger-condition \
  -H "Content-Type: application/json" \
  -d '{
    "conditionType": "ABSOLUTE",
    "absoluteTime": "16:00:00"
  }'

# 创建 BLUE 等级的报警规则
curl -X POST http://localhost:8080/api/alert/rule \
  -H "Content-Type: application/json" \
  -d '{
    "exceptionTypeId": 1,
    "level": "BLUE",
    "triggerConditionId": 1,
    "actionType": "LOG",
    "priority": 5
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
      "shift_start_time": "2024-12-11T08:00:00"
    }
  }'
```

## API 文档

详见 [ALERT_SYSTEM_GUIDE.md](ALERT_SYSTEM_GUIDE.md)

主要接口：

| 方法 | 路径 | 说明 |
|-----|------|------|
| POST | /api/alert/exception-type | 创建异常类型 |
| GET | /api/alert/exception-types | 获取所有异常类型 |
| POST | /api/alert/trigger-condition | 创建触发条件 |
| POST | /api/alert/rule | 创建报警规则 |
| GET | /api/alert/rules/{exceptionTypeId} | 获取报警规则 |
| POST | /api/alert/event | 报告异常事件 |
| GET | /api/alert/events/active | 获取活跃异常 |
| GET | /api/alert/event/{eventId} | 获取事件详情 |
| PUT | /api/alert/event/{eventId}/resolve | 解决异常 |

## 设计亮点

### 1. 精确的时间计算

不需要定期轮询，而是：
- 根据触发条件精确计算下次评估时间
- 创建一个 `ONCE` 模式的 `ScheduledTask` 提交给调度系统
- 调度系统在该时刻精确执行评估

### 2. 等级逐步升级

- 异常事件创建时，只为**最低等级**创建评估任务
- 当前等级触发报警后，才为**下一个更高等级**创建任务
- 避免同时创建多个任务，逻辑清晰、开销小

### 3. 与调度框架完美融合

- 复用 `TaskScheduler`、`TaskExecutor` 等核心组件
- 复用分布式锁机制，支持集群部署
- 复用执行日志系统，完整的审计追踪

### 4. 高度可扩展

- **新的异常检测**：实现 `ExceptionDetectionStrategy`
- **新的触发条件**：实现 `TriggerStrategy`
- **新的报警动作**：实现 `AlertActionExecutor`

## 常见使用场景

### 场景1: 时间固定的报警

**需求**：每天18:00检查某个条件，如果未满足则报警

**配置**：
```
触发条件：absolute_time = 18:00
报警等级：BLUE（只需要一个等级）
动作：LOG/EMAIL/SMS
```

### 场景2: 事件驱动的升级报警

**需求**：班次开始无入井记录，8小时后发黄色预警，12小时后发红色预警

**配置**：
```
等级BLUE：relative_event = "SHIFT_START" + 0分钟（立即检测）
等级YELLOW：relative_event = "SHIFT_START" + 480分钟（8小时）
等级RED：relative_event = "SHIFT_START" + 720分钟（12小时）
```

### 场景3: 混合条件的升级报警

**需求**：既要在工作时间段，又要等待足够时间后才报警

**配置**：
```
混合条件：(absolute_time = 16:00) AND (relative_time > 8h)
动作：EMAIL/SMS
```

## 扩展示例

### 添加 Webhook 报警动作

```java
@Component
public class WebhookAlertAction implements AlertActionExecutor {
    
    @Override
    public void execute(Map<String, Object> actionConfig, ExceptionEvent event, AlertRule rule) throws Exception {
        String webhookUrl = (String) actionConfig.get("url");
        String payload = buildPayload(event, rule);
        // 发送HTTP POST请求
        restTemplate.postForObject(webhookUrl, payload, String.class);
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

## 监控和调试

### 查看所有活跃异常

```bash
curl http://localhost:8080/api/alert/events/active
```

### 查看异常的完整升级历史

```bash
curl http://localhost:8080/api/alert/event/{eventId}
```

### 查看系统日志

```bash
tail -f logs/application.log | grep "报警\|ALERT"
```

## 性能考虑

1. **数据库索引**：已在关键字段建立索引
2. **任务数量**：每个异常最多只有等级数量个任务并发
3. **内存占用**：轻量级，仅存储必要的状态信息

## 后续优化方向

- [ ] 支持自动修复（如果条件不再满足，自动降级等级）
- [ ] 支持报警频率限制（防止频繁报警）
- [ ] 支持多人协作处理（标记为已确认、已处理等）
- [ ] 提供可视化的规则配置界面
- [ ] 集成更多通知渠道（DingTalk、WeChat等）

---

如有任何问题或建议，欢迎提出！
