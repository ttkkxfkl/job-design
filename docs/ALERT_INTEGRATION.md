# 报警规则系统 - 集成说明

本文档说明如何将报警规则系统与现有的任务调度框架进行集成。

## 整体架构

```
现有系统 (TaskScheduler)         报警系统 (AlertExecutor)
─────────────────────────────────────────────────────────
TaskController
  ↓
TaskManagementService
  ├─ createOnceTask()
  └─ createCronTask()
       ↓
   TaskScheduler (Interface)
   ├─ SimpleTaskScheduler
   └─ QuartzTaskScheduler
       ↓
   TaskExecutor (Interface)
   ├─ LogTaskExecutor
   ├─ EmailTaskExecutor
   ├─ SmsTaskExecutor
   └─ AlertExecutor ← 新增！
       ↓
   [数据库执行结果]
```

## 关键集成点

### 1. 任务类型扩展

在 `ScheduledTask.TaskType` 枚举中已添加 `ALERT` 类型：

```java
public enum TaskType {
    LOG,        // 现有
    EMAIL,      // 现有
    SMS,        // 现有
    WEBHOOK,    // 现有
    MQ,         // 现有
    PLAN,       // 现有
    ALERT       // 新增！用于报警评估任务
}
```

### 2. 执行器集成

`AlertExecutor` 实现了 `TaskExecutor` 接口，会被自动注入到任务调度系统中：

```java
@Component
@RequiredArgsConstructor
public class AlertExecutor implements TaskExecutor {
    
    @Override
    public boolean support(ScheduledTask.TaskType taskType) {
        return taskType == ScheduledTask.TaskType.ALERT;
    }
    
    @Override
    public void execute(ScheduledTask task) throws Exception {
        // 报警评估逻辑
    }
}
```

任务调度系统会自动发现并使用这个执行器。

### 3. 任务创建流程

当异常事件被报告时：

```
1. 异常事件创建
   └─ AlertEscalationService.scheduleInitialEvaluation()
   
2. 计算下次评估时间
   └─ TriggerStrategy.calculateNextEvaluationTime()
   
3. 创建 ScheduledTask
   └─ TaskManagementService.createOnceTask()
      ├─ taskType: ALERT
      ├─ executeTime: 计算出的下次评估时间
      └─ taskData: {exceptionEventId, alertRuleId, ...}
   
4. 提交给调度系统
   └─ TaskScheduler.scheduleTask()
```

## 数据流

```
用户报告异常
    ↓
POST /api/alert/event
    ↓
AlertRuleController.reportExceptionEvent()
    ↓
ExceptionEvent 入库
    ↓
AlertEscalationService.scheduleInitialEvaluation()
    ├─ 获取最低等级报警规则
    ├─ 计算下次评估时间
    └─ 创建 ScheduledTask (ONCE 模式)
    
[等待到达评估时间]
    ↓
任务调度系统触发
    ↓
AlertExecutor.execute()
    ├─ 获取异常事件和报警规则
    ├─ 创建 TriggerStrategy 并评估
    ├─ 如果满足条件：
    │  ├─ 执行报警动作
    │  ├─ 记录到 AlertEventLog
    │  └─ 创建下一等级评估任务
    └─ 如果不满足：
       └─ 等待下次评估
```

## 关键配置

### 1. 确保 ALERT 任务类型被识别

在 `application.yml` 中：

```yaml
spring:
  task:
    execution:
      pool:
        core-size: 10
        max-size: 20
        queue-capacity: 100

scheduled:
  task:
    scheduler-type: simple  # 或 quartz
    lock-type: local        # 或 redis
```

### 2. 启用数据库扫描

确保 MyBatis Mapper 扫描包含了 alert 模块：

```java
@SpringBootApplication
@MapperScan({
    "com.example.scheduled.repository",
    "com.example.scheduled.alert.repository"  // 新增！
})
public class ScheduledTaskApplication {
    // ...
}
```

或者在 `application.yml` 中：

```yaml
mybatis:
  mapper-locations: classpath*:/mapper/**/*.xml
  
mybatis-plus:
  mapper-locations: classpath*:/mapper/**/*.xml
  type-aliases-package: com.example.scheduled.entity,com.example.scheduled.alert.entity
```

### 3. 启用事务管理

确保 Spring 的 `@Transactional` 生效：

```java
@SpringBootApplication
@EnableTransactionManagement
public class ScheduledTaskApplication {
    // ...
}
```

## 数据库表初始化

1. **新建表**：执行 `alert-schema.sql`

```bash
mysql -u root -p scheduled_task < src/main/resources/alert-schema.sql
```

2. **表关系**

```
exception_type (异常类型)
    ↑
    ├─ exception_event (异常事件) ← 多对一
    │
    └─ alert_rule (报警规则) ← 一对多
         ↓
         ├─ trigger_condition (触发条件) ← 多对一
         └─ alert_event_log (报警日志) ← 一对多
```

## 依赖关系

报警系统依赖的现有组件：

- ✅ `TaskManagementService` - 创建评估任务
- ✅ `TaskScheduler` - 调度评估任务
- ✅ `DistributedLock` - 防止并发评估
- ✅ `JsonTypeHandler` - 存储JSON配置

报警系统新增的组件：

- `TriggerStrategy` 及其实现类
- `ExceptionDetectionStrategy` 及其实现类
- `AlertActionExecutor` 及其实现类
- `AlertRuleController` API 接口

## 扩展点

### 1. 添加新的触发条件

在 `trigger/strategy/` 目录下创建新类：

```java
public class MyCustomTrigger implements TriggerStrategy {
    @Override
    public boolean shouldTrigger(...) { ... }
    
    @Override
    public LocalDateTime calculateNextEvaluationTime(...) { ... }
}
```

在 `TriggerStrategyFactory` 中注册：

```java
public TriggerStrategy createStrategy(TriggerCondition condition) {
    return switch(condition.getConditionType()) {
        case "ABSOLUTE" -> new AbsoluteTimeTrigger();
        case "RELATIVE" -> new RelativeEventTrigger();
        case "CUSTOM" -> new MyCustomTrigger();  // 新增
        // ...
    };
}
```

### 2. 添加新的报警动作

在 `action/impl/` 目录下创建新类：

```java
@Component
public class PushAlertAction implements AlertActionExecutor {
    @Override
    public void execute(...) { ... }
    
    @Override
    public boolean supports(String actionType) {
        return "PUSH".equalsIgnoreCase(actionType);
    }
    
    @Override
    public String getActionType() {
        return "PUSH";
    }
}
```

Spring 会自动装配到 `AlertExecutor` 的 `actionExecutors` 列表中。

### 3. 添加新的异常检测策略

在 `detection/impl/` 目录下创建新类：

```java
@Component("customDetector")
public class MyDetector implements ExceptionDetectionStrategy {
    @Override
    public boolean detect(...) { ... }
    
    @Override
    public String getStrategyName() {
        return "CUSTOM";
    }
}
```

## 调试技巧

### 1. 查看任务执行日志

在日志配置中设置：

```yaml
logging:
  level:
    com.example.scheduled.alert: DEBUG
    com.example.scheduled.executor: DEBUG
```

### 2. 手动测试评估任务

可以直接调用 `AlertExecutor.execute()` 进行测试：

```java
@RunWith(SpringRunner.class)
@SpringBootTest
public class AlertExecutorTest {
    
    @Autowired
    private AlertExecutor alertExecutor;
    
    @Test
    public void testExecute() throws Exception {
        ScheduledTask task = new ScheduledTask();
        task.setTaskType(ScheduledTask.TaskType.ALERT);
        task.setTaskData(Map.of(
            "exceptionEventId", 1L,
            "alertRuleId", 1L
        ));
        alertExecutor.execute(task);
    }
}
```

### 3. 监控任务队列

```bash
# 查看 scheduled_task 表中待执行任务
mysql> SELECT id, task_name, execute_time, status FROM scheduled_task 
       WHERE task_type = 'ALERT' ORDER BY execute_time;
```

## 性能优化

### 1. 索引优化

`alert-schema.sql` 中已创建必要的索引：

```sql
-- 加快活跃异常查询
INDEX idx_exception_event_type_status 
ON exception_event(exception_type_id, status);

-- 加快规则查询
CREATE INDEX idx_alert_rule_exception_type_enabled 
ON alert_rule(exception_type_id, enabled);
```

### 2. 批量操作

如果需要处理大量异常事件，可以创建专门的批量处理接口：

```java
@PostMapping("/events/batch-report")
public ApiResponse<?> batchReportEvents(@RequestBody List<ExceptionEvent> events) {
    events.forEach(event -> {
        event.setDetectedAt(LocalDateTime.now());
        event.setStatus("ACTIVE");
        exceptionEventRepository.insert(event);
        alertEscalationService.scheduleInitialEvaluation(event);
    });
    return ApiResponse.success("批量上报完成");
}
```

## 故障排除

### 问题1: 评估任务没有被触发

**可能原因**：
1. 任务时间配置错误
2. 调度器未启动
3. 数据库连接问题

**排查步骤**：
```bash
# 检查任务是否创建
mysql> SELECT * FROM scheduled_task WHERE task_type = 'ALERT';

# 查看日志
tail -f logs/application.log | grep AlertExecutor

# 检查调度器状态
curl http://localhost:8080/api/tasks/scheduler-status
```

### 问题2: 报警动作执行失败

**可能原因**：
1. 动作配置不完整（如邮件地址为空）
2. 外部服务不可用（如邮件服务器）
3. 权限不足

**排查步骤**：
```bash
# 查看报警日志中的错误信息
mysql> SELECT * FROM alert_event_log 
       WHERE action_status = 'FAILED' 
       ORDER BY triggered_at DESC LIMIT 10;

# 检查配置
mysql> SELECT action_config FROM alert_rule WHERE id = ?;
```

## 总结

报警规则系统通过以下方式与现有的任务调度框架完美集成：

1. **复用 TaskExecutor 接口** - 无缝融入现有执行框架
2. **复用 TaskScheduler 系统** - 利用成熟的调度机制
3. **共享数据库存储** - 统一的持久化管理
4. **共享分布式锁** - 保证集群环境的一致性
5. **复用执行日志** - 完整的审计追踪

这样的设计既保持了报警系统的独立性和可扩展性，又能充分利用现有框架的能力。

---

更多信息请参考：
- [ALERT_README.md](ALERT_README.md) - 报警系统概览
- [ALERT_SYSTEM_GUIDE.md](ALERT_SYSTEM_GUIDE.md) - 详细使用指南
