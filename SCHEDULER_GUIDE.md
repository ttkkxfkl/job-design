# 双调度器模式使用指南

## 概述

本系统支持两种任务调度器实现，可通过配置文件无缝切换：

1. **Simple 调度器**（默认）：基于 `ScheduledThreadPoolExecutor`，轻量级，仅支持 ONCE（一次性定时）模式
2. **Quartz 调度器**：基于 Quartz 框架，功能强大，支持 ONCE 和 CRON（周期性调度）两种模式

---

## 调度器切换

### 方式一：配置文件切换

编辑 `application.yml`：

```yaml
scheduled:
  task:
    # 调度器类型：simple 或 quartz
    scheduler-type: simple  # 改为 quartz 启用 Quartz 调度器
```

### 方式二：启动参数

```bash
# 使用 Simple 调度器（默认）
java -jar app.jar

# 使用 Quartz 调度器
java -jar app.jar --scheduled.task.scheduler-type=quartz
```

### 方式三：环境变量

```bash
export SCHEDULED_TASK_SCHEDULER_TYPE=quartz
java -jar app.jar
```

---

## 两种调度器对比

| 特性 | Simple 调度器 | Quartz 调度器 |
|------|--------------|--------------|
| **支持模式** | 仅 ONCE | ONCE + CRON |
| **Cron 表达式** | ❌ 不支持 | ✅ 支持 |
| **依赖** | 无额外依赖 | 需要 Quartz 库 |
| **数据库表** | 仅业务表 | 业务表 + Quartz 表 |
| **集群支持** | 需配合分布式锁 | 原生支持集群 |
| **适用场景** | 轻量级、一次性任务 | 复杂调度、周期任务 |
| **内存占用** | 低 | 中等 |
| **启动速度** | 快 | 稍慢 |

---

## 任务调度模式

### ONCE 模式（一次性定时）

在指定的时间执行一次，适用于定时提醒、延迟通知等场景。

**两种调度器都支持 ONCE 模式。**

#### 创建示例

```bash
POST /api/tasks/once
Content-Type: application/json

{
  "taskName": "生日提醒",
  "taskType": "LOG",
  "executeTime": "2025-11-14T10:00:00",
  "taskData": {
    "message": "今天是小明的生日！"
  },
  "maxRetryCount": 3
}
```

**特点**：
- 支持失败重试（最多 `maxRetryCount` 次）
- 执行时间过期会立即执行
- 执行成功后状态变为 `SUCCESS`
- 重启后自动恢复调度

---

### CRON 模式（周期性调度）

按照 Cron 表达式周期性执行，适用于定时报表、日常提醒等场景。

**仅 Quartz 调度器支持 CRON 模式。**

#### 创建示例

```bash
POST /api/tasks/cron
Content-Type: application/json

{
  "taskName": "每日报表",
  "taskType": "LOG",
  "cronExpression": "0 0 9 * * ?",
  "taskData": {
    "reportType": "daily"
  }
}
```

**常用 Cron 表达式**：

| 表达式 | 说明 |
|--------|------|
| `0 0 9 * * ?` | 每天 9:00 执行 |
| `0 */30 * * * ?` | 每 30 分钟执行 |
| `0 0 0 * * ?` | 每天 00:00 执行 |
| `0 0 12 * * MON-FRI` | 工作日 12:00 执行 |
| `0 0 0 1 * ?` | 每月 1 号 00:00 执行 |

**Cron 表达式格式**：`秒 分 时 日 月 星期`

**特点**：
- 周期性自动触发
- 不支持重试（失败后等待下次触发）
- 执行成功后状态保持 `PENDING`
- 取消任务会从 Quartz 中移除

---

## 完整配置示例

### Simple 调度器配置

```yaml
scheduled:
  task:
    # 调度器类型
    scheduler-type: simple
    # 核心线程数
    core-pool-size: 10
    # 最大重试次数
    max-retry-count: 3
    # 重试间隔（秒）
    retry-interval-seconds: 60
    # 锁类型：local / redis
    lock-type: local
```

### Quartz 调度器配置

```yaml
scheduled:
  task:
    # 调度器类型
    scheduler-type: quartz
    # 最大重试次数（仅 ONCE 模式）
    max-retry-count: 3
    # 重试间隔（秒）
    retry-interval-seconds: 60
    # 锁类型：local / redis
    lock-type: local

spring:
  # Quartz 数据库持久化配置
  quartz:
    job-store-type: jdbc
    jdbc:
      initialize-schema: always
    properties:
      org:
        quartz:
          scheduler:
            instanceName: ScheduledTaskScheduler
            instanceId: AUTO
          jobStore:
            class: org.quartz.impl.jdbcjobstore.JobStoreTX
            driverDelegateClass: org.quartz.impl.jdbcjobstore.StdJDBCDelegate
            tablePrefix: QRTZ_
            isClustered: true
            clusterCheckinInterval: 10000
          threadPool:
            threadCount: 10
```

---

## 数据库初始化

### Simple 调度器

仅需执行 `schema.sql` 创建业务表：
- `scheduled_task`：任务表
- `task_execution_log`：执行日志表

### Quartz 调度器

除了业务表，还需创建 Quartz 表（自动创建或手动执行 `quartz-schema.sql`）：
- `QRTZ_*`：Quartz 内部调度表（约 11 张表）

配置 `spring.quartz.jdbc.initialize-schema=always` 会在首次启动时自动创建 Quartz 表。

---

## API 使用示例

### 兼容接口（自动识别模式）

```bash
# 创建一次性任务（根据参数自动判断）
POST /api/tasks
{
  "taskName": "测试任务",
  "taskType": "LOG",
  "executeTime": "2025-11-14T15:00:00",
  "maxRetryCount": 3
}

# 创建 Cron 任务（根据 cronExpression 自动判断）
POST /api/tasks
{
  "taskName": "定时任务",
  "taskType": "LOG",
  "cronExpression": "0 0 9 * * ?"
}
```

### 明确接口

```bash
# 创建一次性任务
POST /api/tasks/once

# 创建 Cron 任务
POST /api/tasks/cron
```

### 其他接口（两种调度器通用）

```bash
# 查询任务列表
GET /api/tasks

# 查询任务详情
GET /api/tasks/{id}

# 取消任务
DELETE /api/tasks/{id}

# 查询执行历史
GET /api/tasks/{id}/logs

# 调度器状态
GET /api/tasks/scheduler/status
```

---

## 切换注意事项

### Simple → Quartz

1. 修改配置文件：`scheduler-type: quartz`
2. 确保 Quartz 表已创建
3. 重启应用
4. 已存在的 ONCE 任务会自动迁移到 Quartz
5. 可以开始创建 CRON 任务

### Quartz → Simple

1. 修改配置文件：`scheduler-type: simple`
2. 重启应用
3. **注意**：已存在的 CRON 任务不会被调度（Simple 不支持）
4. ONCE 任务会自动恢复调度

**建议**：如需使用 CRON 功能，建议从一开始就选择 Quartz 调度器。

---

## 集群部署

### Simple 调度器集群

需配合 Redis 分布式锁防止任务重复执行：

```yaml
scheduled:
  task:
    lock-type: redis

spring:
  redis:
    host: localhost
    port: 6379
```

### Quartz 调度器集群

Quartz 原生支持集群，配置数据库持久化即可：

```yaml
spring:
  quartz:
    properties:
      org:
        quartz:
          jobStore:
            isClustered: true
            clusterCheckinInterval: 10000
```

---

## 监控和状态

### 查看调度器状态

```bash
GET /api/tasks/scheduler/status
```

**Simple 调度器响应**：
```json
{
  "schedulerType": "Simple",
  "activeCount": 2,
  "poolSize": 10,
  "queueSize": 3,
  "scheduledTaskCount": 15,
  "completedTaskCount": 42,
  "pendingTasksInDb": 18
}
```

**Quartz 调度器响应**：
```json
{
  "schedulerType": "Quartz",
  "schedulerName": "ScheduledTaskScheduler",
  "schedulerInstanceId": "NON_CLUSTERED",
  "numberOfJobsExecuted": 156,
  "isStarted": true,
  "isInStandbyMode": false,
  "runningSince": "2025-11-14T10:00:00",
  "pendingTasksInDb": 18
}
```

---

## 常见问题

### Q1: 为什么我的 CRON 任务没有执行？

**A**: 检查是否使用了 Quartz 调度器。Simple 调度器不支持 CRON 模式。

```yaml
# 确保配置为 quartz
scheduled:
  task:
    scheduler-type: quartz
```

### Q2: 切换调度器后任务丢失？

**A**: 任务数据保存在数据库中不会丢失，但调度状态需重启后恢复。确保：
- 应用完全重启
- 查看日志确认任务加载成功

### Q3: Quartz 表太多，能否简化？

**A**: Quartz 表是框架必需的，用于持久化和集群协调。可配置 `initialize-schema=never` 并手动管理表结构。

### Q4: Simple 调度器性能如何？

**A**: 对于几百到几千个任务，Simple 调度器性能优秀。如任务量达到万级，建议切换到 Quartz。

### Q5: 能否同时使用两种调度器？

**A**: 不行，系统同时只能激活一种调度器（通过 `@ConditionalOnProperty` 保证）。

---

## 总结

- **新项目**：根据需求选择调度器
  - 仅需一次性定时 → Simple
  - 需要周期性调度 → Quartz
  
- **已有项目**：可随时切换，数据不丢失

- **最佳实践**：
  - 开发/测试环境：Simple（轻量快速）
  - 生产环境（有 Cron 需求）：Quartz
  - 生产环境（仅一次性任务）：Simple + Redis 锁

- **功能扩展**：两种调度器共享执行器（TaskExecutor），扩展执行逻辑无需修改调度器代码
