# 双调度器模式使用示例

本文档提供 Simple 和 Quartz 两种调度器的完整使用示例。

---

## 示例 1：创建一次性定时任务（ONCE 模式）

**场景**：30 分钟后发送生日提醒

### 使用 Simple 调度器

```bash
# 1. 配置文件（application.yml）
scheduled:
  task:
    scheduler-type: simple

# 2. 创建任务
POST http://localhost:8080/api/tasks/once
Content-Type: application/json

{
  "taskName": "生日提醒 - 小明",
  "taskType": "LOG",
  "executeTime": "2025-11-14T16:30:00",
  "taskData": {
    "recipient": "xiaoming",
    "message": "今天是你的生日，祝你生日快乐！",
    "priority": "HIGH"
  },
  "maxRetryCount": 3
}

# 3. 响应
{
  "code": 200,
  "message": "一次性任务创建成功",
  "data": {
    "id": 1,
    "taskName": "生日提醒 - 小明",
    "taskType": "LOG",
    "scheduleMode": "ONCE",
    "executeTime": "2025-11-14T16:30:00",
    "status": "PENDING",
    "retryCount": 0,
    "maxRetryCount": 3,
    "createdAt": "2025-11-14T16:00:00"
  }
}
```

### 使用 Quartz 调度器

```bash
# 1. 配置文件（application.yml）
scheduled:
  task:
    scheduler-type: quartz

# 2. 创建任务（请求完全相同）
POST http://localhost:8080/api/tasks/once
Content-Type: application/json

{
  "taskName": "生日提醒 - 小明",
  "taskType": "LOG",
  "executeTime": "2025-11-14T16:30:00",
  "taskData": {
    "recipient": "xiaoming",
    "message": "今天是你的生日，祝你生日快乐！"
  },
  "maxRetryCount": 3
}
```

**两种调度器执行效果完全一致！**

---

## 示例 2：创建周期性 Cron 任务（仅 Quartz 支持）

**场景**：每天早上 9 点发送日报

```bash
# 1. 配置文件（必须使用 Quartz）
scheduled:
  task:
    scheduler-type: quartz

# 2. 创建 Cron 任务
POST http://localhost:8080/api/tasks/cron
Content-Type: application/json

{
  "taskName": "每日数据报表",
  "taskType": "LOG",
  "cronExpression": "0 0 9 * * ?",
  "taskData": {
    "reportType": "daily",
    "recipients": ["admin@example.com", "manager@example.com"]
  }
}

# 3. 响应
{
  "code": 200,
  "message": "Cron 任务创建成功",
  "data": {
    "id": 2,
    "taskName": "每日数据报表",
    "taskType": "LOG",
    "scheduleMode": "CRON",
    "cronExpression": "0 0 9 * * ?",
    "status": "PENDING",
    "createdAt": "2025-11-14T16:00:00"
  }
}
```

**执行时间线**：
- 2025-11-15 09:00:00 → 第 1 次执行
- 2025-11-16 09:00:00 → 第 2 次执行
- 2025-11-17 09:00:00 → 第 3 次执行
- ... 持续执行

---

## 示例 3：常用 Cron 表达式示例

```bash
# 每小时整点执行
POST /api/tasks/cron
{
  "taskName": "每小时健康检查",
  "taskType": "LOG",
  "cronExpression": "0 0 * * * ?"
}

# 每 30 分钟执行
POST /api/tasks/cron
{
  "taskName": "每半小时数据同步",
  "taskType": "LOG",
  "cronExpression": "0 */30 * * * ?"
}

# 每天凌晨 2 点执行（数据清理）
POST /api/tasks/cron
{
  "taskName": "数据清理任务",
  "taskType": "LOG",
  "cronExpression": "0 0 2 * * ?"
}

# 工作日上午 10 点执行
POST /api/tasks/cron
{
  "taskName": "工作日站会提醒",
  "taskType": "LOG",
  "cronExpression": "0 0 10 * * MON-FRI"
}

# 每月 1 号凌晨执行
POST /api/tasks/cron
{
  "taskName": "月度账单生成",
  "taskType": "LOG",
  "cronExpression": "0 0 0 1 * ?"
}

# 每周日晚上 11 点执行
POST /api/tasks/cron
{
  "taskName": "周报汇总",
  "taskType": "LOG",
  "cronExpression": "0 0 23 * * SUN"
}
```

---

## 示例 4：查询和管理任务

```bash
# 查询所有任务
GET http://localhost:8080/api/tasks

# 查询待执行任务
GET http://localhost:8080/api/tasks?status=PENDING

# 查询任务详情
GET http://localhost:8080/api/tasks/1

# 查询任务执行历史
GET http://localhost:8080/api/tasks/1/logs

# 响应示例
{
  "code": 200,
  "data": [
    {
      "id": 1,
      "taskId": 1,
      "executeTime": "2025-11-14T16:30:00",
      "status": "SUCCESS",
      "executionDurationMs": 125,
      "createdAt": "2025-11-14T16:30:00"
    },
    {
      "id": 2,
      "taskId": 1,
      "executeTime": "2025-11-14T16:31:00",
      "status": "FAILED",
      "errorMessage": "Network timeout",
      "executionDurationMs": 5002,
      "createdAt": "2025-11-14T16:31:00"
    }
  ]
}

# 取消任务
DELETE http://localhost:8080/api/tasks/1

# 查看调度器状态
GET http://localhost:8080/api/tasks/scheduler/status
```

---

## 示例 5：失败重试演示（ONCE 模式）

```bash
# 创建会失败的任务（模拟）
POST /api/tasks/once
{
  "taskName": "模拟失败任务",
  "taskType": "LOG",
  "executeTime": "2025-11-14T16:35:00",
  "maxRetryCount": 3
}

# 任务执行时间线（假设每次都失败）：
# 16:35:00 - 第 1 次执行失败 → retryCount=1, 下次 16:36:00
# 16:36:00 - 第 2 次执行失败 → retryCount=2, 下次 16:37:00
# 16:37:00 - 第 3 次执行失败 → retryCount=3, 下次 16:38:00
# 16:38:00 - 第 4 次执行失败 → retryCount=4 >= maxRetryCount=3, 标记为 FAILED
```

**查看执行日志**：
```bash
GET /api/tasks/1/logs

# 响应
{
  "code": 200,
  "data": [
    {
      "executeTime": "2025-11-14T16:35:00",
      "status": "FAILED",
      "errorMessage": "Simulated error",
      "executionDurationMs": 50
    },
    {
      "executeTime": "2025-11-14T16:36:00",
      "status": "FAILED",
      "errorMessage": "Simulated error",
      "executionDurationMs": 48
    },
    {
      "executeTime": "2025-11-14T16:37:00",
      "status": "FAILED",
      "errorMessage": "Simulated error",
      "executionDurationMs": 52
    },
    {
      "executeTime": "2025-11-14T16:38:00",
      "status": "FAILED",
      "errorMessage": "Simulated error (max retry reached)",
      "executionDurationMs": 49
    }
  ]
}
```

---

## 示例 6：调度器切换演示

### 步骤 1：使用 Simple 调度器

```yaml
# application.yml
scheduled:
  task:
    scheduler-type: simple
```

```bash
# 启动应用
mvn spring-boot:run

# 日志输出
2025-11-14 16:00:00 INFO  - 初始化 Simple 任务调度器，核心线程数：10
2025-11-14 16:00:00 INFO  - 加载所有待执行的一次性任务，数量：5
2025-11-14 16:00:00 INFO  - Simple 任务调度器初始化完成
```

### 步骤 2：切换到 Quartz 调度器

```yaml
# application.yml（修改）
scheduled:
  task:
    scheduler-type: quartz
```

```bash
# 重启应用
mvn spring-boot:run

# 日志输出
2025-11-14 16:05:00 INFO  - 初始化 Quartz 任务调度器
2025-11-14 16:05:00 INFO  - 加载所有待执行任务，数量：5
2025-11-14 16:05:00 INFO  - Quartz 任务调度器初始化完成
```

**原有的 5 个任务会自动恢复调度！**

### 步骤 3：在 Quartz 下创建 Cron 任务

```bash
POST /api/tasks/cron
{
  "taskName": "定时备份",
  "taskType": "LOG",
  "cronExpression": "0 0 1 * * ?"
}

# 成功创建！
```

### 步骤 4：切回 Simple 调度器

```yaml
# application.yml（再次修改）
scheduled:
  task:
    scheduler-type: simple
```

```bash
# 重启应用
mvn spring-boot:run

# 日志输出
2025-11-14 16:10:00 INFO  - 初始化 Simple 任务调度器
2025-11-14 16:10:00 INFO  - 加载所有待执行的一次性任务，数量：5
2025-11-14 16:10:00 WARN  - Simple 调度器不支持 CRON 模式任务：定时备份
2025-11-14 16:10:00 INFO  - Simple 任务调度器初始化完成
```

**ONCE 任务正常调度，CRON 任务被跳过（Simple 不支持）。**

---

## 示例 7：监控调度器状态

### Simple 调度器状态

```bash
GET /api/tasks/scheduler/status

# 响应
{
  "code": 200,
  "data": {
    "schedulerType": "Simple",
    "activeCount": 2,         # 当前活跃线程数
    "poolSize": 10,           # 线程池大小
    "queueSize": 3,           # 等待队列长度
    "scheduledTaskCount": 15, # 已调度任务数
    "completedTaskCount": 42, # 已完成任务数
    "pendingTasksInDb": 18    # 数据库中待执行任务数
  }
}
```

**告警规则**：
- `queueSize > 50` → 任务堆积，考虑扩大线程池
- `activeCount == poolSize` → 线程池满载

### Quartz 调度器状态

```bash
GET /api/tasks/scheduler/status

# 响应
{
  "code": 200,
  "data": {
    "schedulerType": "Quartz",
    "schedulerName": "ScheduledTaskScheduler",
    "schedulerInstanceId": "NON_CLUSTERED",
    "numberOfJobsExecuted": 156,
    "isStarted": true,
    "isInStandbyMode": false,
    "runningSince": "2025-11-14T10:00:00",
    "pendingTasksInDb": 18
  }
}
```

---

## 示例 8：集群部署（Quartz）

### 配置集群模式

```yaml
# application.yml（节点1 和 节点2 相同配置）
scheduled:
  task:
    scheduler-type: quartz

spring:
  datasource:
    url: jdbc:mysql://共享数据库:3306/scheduled_task
    username: root
    password: password
  
  quartz:
    job-store-type: jdbc
    properties:
      org:
        quartz:
          jobStore:
            isClustered: true          # 启用集群
            clusterCheckinInterval: 10000
```

### 启动多节点

```bash
# 节点1
java -jar app.jar --server.port=8080

# 节点2
java -jar app.jar --server.port=8081

# 日志输出（节点1）
2025-11-14 16:20:00 INFO  - 初始化 Quartz 任务调度器
2025-11-14 16:20:00 INFO  - Quartz Scheduler 'ScheduledTaskScheduler' started in CLUSTERED mode

# 日志输出（节点2）
2025-11-14 16:20:05 INFO  - 初始化 Quartz 任务调度器
2025-11-14 16:20:05 INFO  - Detected existing cluster, joining...
```

### 任务分配

```bash
# 创建任务
POST http://localhost:8080/api/tasks/cron
{
  "taskName": "集群任务",
  "cronExpression": "0 */5 * * * ?"
}

# Quartz 会自动在两个节点间负载均衡
# 某个时刻可能由节点1 执行，下次可能由节点2 执行
# 但同一时刻只会有一个节点执行（分布式锁保证）
```

---

## 示例 9：自定义执行器扩展

```java
// 创建自定义邮件执行器
@Slf4j
@Component
public class RealEmailTaskExecutor implements TaskExecutor {

    @Autowired
    private JavaMailSender mailSender;

    @Override
    public boolean support(ScheduledTask.TaskType taskType) {
        return taskType == ScheduledTask.TaskType.EMAIL;
    }

    @Override
    public void execute(ScheduledTask task) throws Exception {
        String recipient = (String) task.getTaskData().get("recipient");
        String subject = (String) task.getTaskData().get("subject");
        String content = (String) task.getTaskData().get("content");

        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(recipient);
        message.setSubject(subject);
        message.setText(content);
        
        mailSender.send(message);
        log.info("邮件发送成功：{}", recipient);
    }

    @Override
    public String getName() {
        return "真实邮件执行器";
    }
}
```

```bash
# 使用自定义执行器（两种调度器都支持！）
POST /api/tasks/once
{
  "taskName": "发送邮件提醒",
  "taskType": "EMAIL",
  "executeTime": "2025-11-14T17:00:00",
  "taskData": {
    "recipient": "user@example.com",
    "subject": "会议提醒",
    "content": "今天下午3点有项目评审会议"
  }
}
```

---

## 总结

- **Simple 调度器**：轻量、快速、适合一次性任务
- **Quartz 调度器**：功能强大、支持 Cron、适合复杂调度
- **无缝切换**：通过配置文件一键切换，数据不丢失
- **扩展性**：自定义执行器对两种调度器透明

选择合适的调度器，享受灵活的任务调度体验！
