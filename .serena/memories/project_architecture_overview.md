# 任务调度框架项目分析

## 项目核心架构

这是一个基于 Spring Boot + MyBatis Plus 的**双调度器定时任务系统**。

### 核心特性
- **双调度器**：SimpleTaskScheduler（内置线程池） + QuartzTaskScheduler
- **双模式**：ONCE（一次性定时） + CRON（周期性调度）
- **策略模式**：TaskScheduler 接口 + TaskExecutor 接口
- **分布式锁**：LocalDistributedLock + RedisDistributedLock
- **执行历史**：TaskExecutionLog 记录每次执行

### 关键类关系
```
TaskController 
  ↓ (TaskManagementService)
TaskScheduler (Interface)
  ├── SimpleTaskScheduler
  └── QuartzTaskScheduler
       ↓ (使用)
   TaskExecutor (Interface)
     ├── LogTaskExecutor
     ├── EmailTaskExecutor
     ├── SmsTaskExecutor
     ├── WebhookTaskExecutor
     └── PlanTaskExecutor
```

### 数据模型
**scheduled_task**：存储任务本身（id, taskName, taskType, scheduleMode, executeTime, cronExpression, priority, executionTimeout, taskData, status, retryCount等）

**task_execution_log**：存储每次执行日志（taskId, executeTime, status, errorMessage, executionDurationMs）

## 现有设计优势
1. ✅ 清晰的分层架构（Controller → Service → Scheduler → Executor）
2. ✅ 灵活的策略模式支持新任务类型扩展
3. ✅ 完整的执行日志和统计功能
4. ✅ 支持分布式环境的锁机制

## 与报警规则设计的融合点
- 可以将"报警"看作一种特殊的TaskType（如 ALERT）
- 可以创建AlertTask 继承 ScheduledTask
- 可以在 TaskExecutor 层实现 AlertExecutor
- 利用现有的 taskData (JSON) 存储报警规则配置
- 利用现有的定时执行和重试机制
