# 双调度器定时任务系统

基于 SpringBoot 的可扩展定时任务调度系统，支持 Simple（内置线程池）和 Quartz 两种调度器实现，通过配置无缝切换。

## ✨ 核心特性

- 🎯 **双调度器架构**：Simple（轻量级）+ Quartz（功能完整），配置切换
- ⏰ **双模式支持**：ONCE（一次性定时）+ CRON（周期性调度）
- 🔄 **失败重试**：支持自动重试，即时重新调度
- 🔒 **分布式锁**：支持本地锁和 Redis 锁，防止集群重复执行
- ⏱️ **超时控制**：任务执行超时自动中断，可配置超时时间
- 🎚️ **优先级调度**：支持 0-10 优先级，高优先级任务优先执行
- ⏸️ **暂停/恢复**：支持手动暂停和恢复任务执行
- 📊 **执行历史**：完整记录每次执行日志
- 📈 **统计报表**：任务统计、每日趋势、类型分布等多维度分析
- 🔌 **策略模式**：执行器可插拔扩展，轻松添加新任务类型
- 💾 **数据持久化**：MySQL 存储任务和日志
- 🚀 **生产就绪**：集群支持、监控指标、完整文档

---

## 📖 快速开始

### 1. 环境要求

- JDK 17+
- Maven 3.6+
- MySQL 8.0+
- Redis（可选，用于分布式锁）

### 2. 数据库初始化

```bash
# 创建数据库
mysql -u root -p
CREATE DATABASE scheduled_task CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

# 执行建表脚本
mysql -u root -p scheduled_task < src/main/resources/schema.sql

# 如果使用 Quartz，Quartz 表会自动创建（配置了 initialize-schema: always）
```

### 3. 配置文件

编辑 `src/main/resources/application.yml`：

```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/scheduled_task
    username: root
    password: your_password

scheduled:
  task:
    scheduler-type: simple  # simple 或 quartz
    lock-type: local        # local 或 redis
```

### 4. 启动应用

```bash
mvn spring-boot:run
```

访问：http://localhost:8080

---

## 🎮 使用示例

### 创建一次性定时任务（带优先级和超时）

```bash
POST http://localhost:8080/api/tasks/once
Content-Type: application/json

{
  "taskName": "生日提醒",
  "taskType": "LOG",
  "executeTime": "2025-11-14T18:00:00",
  "priority": 8,
  "executionTimeout": 60,
  "taskData": {
    "message": "今天是小明的生日！"
  },
  "maxRetryCount": 3
}
```

### 创建周期性 Cron 任务（需 Quartz）

```bash
POST http://localhost:8080/api/tasks/cron
Content-Type: application/json

{
  "taskName": "每日报表",
  "taskType": "LOG",
  "cronExpression": "0 0 9 * * ?",
  "priority": 7,
  "executionTimeout": 300,
  "taskData": {
    "reportType": "daily"
  }
}
```

### 任务控制操作

```bash
# 暂停任务
PUT http://localhost:8080/api/tasks/1/pause

# 恢复任务
PUT http://localhost:8080/api/tasks/1/resume

# 立即重试
POST http://localhost:8080/api/tasks/1/retry

# 取消任务
DELETE http://localhost:8080/api/tasks/1
```

### 查询任务状态

```bash
# 查询所有任务
GET http://localhost:8080/api/tasks

# 查询任务详情
GET http://localhost:8080/api/tasks/1

# 查询执行历史
GET http://localhost:8080/api/tasks/1/logs

# 调度器状态
GET http://localhost:8080/api/tasks/scheduler/status
```

### 统计报表

```bash
# 总体统计
GET http://localhost:8080/api/tasks/statistics/summary

# 每日统计（最近7天）
GET http://localhost:8080/api/tasks/statistics/daily?days=7

# 任务类型分布
GET http://localhost:8080/api/tasks/statistics/type-distribution

# 任务状态分布
GET http://localhost:8080/api/tasks/statistics/status-distribution

# 任务模式分布
GET http://localhost:8080/api/tasks/statistics/mode-distribution
```

---

## 🔧 调度器切换

### Simple 调度器（默认）

```yaml
scheduled:
  task:
    scheduler-type: simple
```

**特点**：
- ✅ 轻量级，无额外依赖
- ✅ 启动快速
- ✅ 内存占用小
- ❌ 仅支持 ONCE 模式
- 📦 适合：一次性定时任务、小规模场景

### Quartz 调度器

```yaml
scheduled:
  task:
    scheduler-type: quartz
```

**特点**：
- ✅ 支持 ONCE + CRON 模式
- ✅ 原生集群支持
- ✅ 数据库持久化调度状态
- ✅ 强大的 Cron 表达式
- 📦 适合：复杂调度、周期任务、大规模场景

**两种调度器可随时切换，任务数据不丢失！**

---

## 📁 项目结构

```
src/main/java/com/example/scheduled/
├── ScheduledTaskApplication.java          # 启动类
├── config/
│   ├── ScheduledTaskProperties.java       # 配置属性
│   └── QuartzConfig.java                  # Quartz 配置
├── controller/
│   └── TaskController.java                # REST API
├── dto/
│   ├── CreateTaskRequest.java             # 请求 DTO
│   └── ApiResponse.java                   # 响应封装
├── entity/
│   ├── ScheduledTask.java                 # 任务实体
│   └── TaskExecutionLog.java              # 执行日志实体
├── scheduler/
│   ├── TaskScheduler.java                 # 调度器接口
│   └── impl/
│       ├── SimpleTaskScheduler.java       # Simple 实现
│       └── QuartzTaskScheduler.java       # Quartz 实现
├── executor/
│   ├── TaskExecutor.java                  # 执行器接口
│   └── impl/
│       ├── LogTaskExecutor.java           # 日志执行器
│       ├── EmailTaskExecutor.java         # 邮件执行器（示例）
│       └── WebhookTaskExecutor.java       # Webhook 执行器（示例）
├── job/
│   └── ScheduledTaskJob.java             # Quartz Job 封装
├── lock/
│   ├── DistributedLock.java              # 分布式锁接口
│   └── impl/
│       ├── LocalDistributedLock.java     # 本地锁
│       └── RedisDistributedLock.java     # Redis 锁
├── repository/
│   ├── ScheduledTaskRepository.java      # 任务仓储
│   └── TaskExecutionLogRepository.java   # 日志仓储
├── service/
│   └── TaskManagementService.java        # 任务管理服务
└── exception/
    └── GlobalExceptionHandler.java       # 全局异常处理
```

---

## 🔌 扩展执行器

实现 `TaskExecutor` 接口即可添加新的任务类型：

```java
@Component
public class SmsTaskExecutor implements TaskExecutor {
    
    @Override
    public boolean support(ScheduledTask.TaskType taskType) {
        return taskType == ScheduledTask.TaskType.SMS;
    }
    
    @Override
    public void execute(ScheduledTask task) throws Exception {
        String phone = (String) task.getTaskData().get("phone");
        String message = (String) task.getTaskData().get("message");
        // 发送短信逻辑
        smsService.send(phone, message);
    }
    
    @Override
    public String getName() {
        return "短信执行器";
    }
}
```

**无需修改调度器代码，系统自动扫描注册！**

---

## 🌐 集群部署

### Simple 调度器集群

配置 Redis 分布式锁：

```yaml
scheduled:
  task:
    scheduler-type: simple
    lock-type: redis

spring:
  redis:
    host: localhost
    port: 6379
```

### Quartz 调度器集群

Quartz 原生支持集群，只需共享数据库：

```yaml
scheduled:
  task:
    scheduler-type: quartz

spring:
  quartz:
    properties:
      org:
        quartz:
          jobStore:
            isClustered: true  # 启用集群
```

---

## 📊 监控指标

### 调度器状态

```bash
GET /api/tasks/scheduler/status
```

**Simple 响应**：
```json
{
  "schedulerType": "Simple",
  "activeCount": 2,
  "poolSize": 10,
  "queueSize": 5,
  "scheduledTaskCount": 15,
  "completedTaskCount": 42
}
```

**Quartz 响应**：
```json
{
  "schedulerType": "Quartz",
  "schedulerName": "ScheduledTaskScheduler",
  "numberOfJobsExecuted": 156,
  "isStarted": true,
  "runningSince": "2025-11-14T10:00:00"
}
```

### 告警规则建议

- `queueSize > 100` → 任务堆积
- `activeCount == poolSize` → 线程池满载
- 执行失败率 > 10% → 系统异常

---

## 🛠️ 常用 Cron 表达式

| 表达式 | 说明 |
|--------|------|
| `0 0 9 * * ?` | 每天 9:00 |
| `0 */30 * * * ?` | 每 30 分钟 |
| `0 0 0 * * ?` | 每天 00:00 |
| `0 0 12 * * MON-FRI` | 工作日 12:00 |
| `0 0 0 1 * ?` | 每月 1 号 00:00 |
| `0 0 0 * * SUN` | 每周日 00:00 |

**Cron 格式**：`秒 分 时 日 月 星期`

---

## 📚 文档导航

- **[ARCHITECTURE.md](ARCHITECTURE.md)** - 完整架构设计和流程图
- **[SCHEDULER_GUIDE.md](SCHEDULER_GUIDE.md)** - 调度器使用指南和切换说明
- **[SCHEDULER_EXAMPLES.md](SCHEDULER_EXAMPLES.md)** - 完整使用示例和最佳实践

---

## ❓ 常见问题

### Q: 为什么我的 CRON 任务没执行？

**A**: 确保使用了 Quartz 调度器：

```yaml
scheduled:
  task:
    scheduler-type: quartz
```

### Q: 切换调度器会丢失任务吗？

**A**: 不会。任务数据保存在数据库，切换后重启应用会自动恢复。

### Q: Simple 和 Quartz 性能差异？

**A**: 
- Simple：几百到几千任务，性能优秀
- Quartz：支持更大规模，万级任务依然稳定

### Q: 如何选择调度器？

**A**:
- 只需一次性定时 → **Simple**
- 需要周期性调度 → **Quartz**
- 开发测试环境 → **Simple**（快速）
- 生产环境（复杂需求）→ **Quartz**

---

## 🤝 贡献

欢迎提交 Issue 和 Pull Request！

---

## 📄 许可证

MIT License

---

## 🎯 技术栈

- SpringBoot 3.2.0
- Spring Data JPA
- MySQL 8.0+
- Quartz 2.3.2
- Lombok
- Redis（可选）

---

## 📞 支持

如有问题，请查看文档或提交 Issue。

**祝使用愉快！** 🎉
