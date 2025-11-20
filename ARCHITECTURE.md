# 双调度器系统架构文档

本文档描述支持 Simple 和 Quartz 双调度器的系统架构设计。

---

## 一、整体架构图

```mermaid
graph TB
    subgraph "客户端层"
        Client[前端/API客户端]
    end

    subgraph "API层"
        Controller[TaskController<br/>RESTful API]
        DTO[CreateTaskRequest DTO]
        Response[ApiResponse]
    end

    subgraph "服务层"
        MgmtService[TaskManagementService<br/>任务管理服务]
    end

    subgraph "调度层 - 策略模式"
        SchedulerInterface[TaskScheduler 接口]
        SimpleScheduler[SimpleTaskScheduler<br/>内置线程池调度器]
        QuartzScheduler[QuartzTaskScheduler<br/>Quartz调度器]
        ThreadPool[ScheduledThreadPoolExecutor]
        QuartzCore[Quartz Scheduler]
        Job[ScheduledTaskJob]
    end

    subgraph "执行层 - 策略模式"
        ExecutorInterface[TaskExecutor 接口]
        LogExecutor[LogTaskExecutor]
        EmailExecutor[EmailTaskExecutor]
        CustomExecutor[自定义执行器...]
    end

    subgraph "基础设施层"
        LockInterface[DistributedLock 接口]
        LocalLock[LocalDistributedLock]
        RedisLock[RedisDistributedLock]
    end

    subgraph "持久化层"
        TaskRepo[ScheduledTaskRepository]
        LogRepo[TaskExecutionLogRepository]
    end

    subgraph "数据存储层"
        MySQL[(MySQL Database<br/>业务表 + Quartz表)]
        Redis[(Redis<br/>分布式锁)]
    end

    Client --> Controller
    Controller --> DTO
    Controller --> MgmtService
    MgmtService --> SchedulerInterface
    
    SchedulerInterface -.选择.-> SimpleScheduler
    SchedulerInterface -.选择.-> QuartzScheduler
    
    SimpleScheduler --> ThreadPool
    QuartzScheduler --> QuartzCore
    QuartzCore --> Job
    Job --> SchedulerInterface
    
    SimpleScheduler --> ExecutorInterface
    QuartzScheduler --> ExecutorInterface
    
    ExecutorInterface --> LogExecutor
    ExecutorInterface --> EmailExecutor
    ExecutorInterface --> CustomExecutor
    
    SimpleScheduler --> LockInterface
    QuartzScheduler --> LockInterface
    
    LockInterface --> LocalLock
    LockInterface --> RedisLock
    
    SimpleScheduler --> TaskRepo
    QuartzScheduler --> TaskRepo
    SimpleScheduler --> LogRepo
    QuartzScheduler --> LogRepo
    
    TaskRepo --> MySQL
    LogRepo --> MySQL
    RedisLock --> Redis
    QuartzCore --> MySQL
    
    Controller --> Response

    style SchedulerInterface fill:#ffeb3b
    style SimpleScheduler fill:#81c784
    style QuartzScheduler fill:#64b5f6
    style ExecutorInterface fill:#ffb74d
    style LockInterface fill:#ba68c8
```

---

## 二、分层架构详解

```mermaid
graph TD
    subgraph "表现层 Presentation Layer"
        A1[TaskController]
        A2[DTO 数据传输对象]
        A3[ApiResponse 统一响应]
    end

    subgraph "应用服务层 Application Layer"
        B1[TaskManagementService<br/>任务生命周期管理]
    end

    subgraph "调度策略层 Scheduler Strategy Layer"
        C1[TaskScheduler 接口]
        C2[SimpleTaskScheduler<br/>ONCE模式]
        C3[QuartzTaskScheduler<br/>ONCE + CRON模式]
    end

    subgraph "执行策略层 Executor Strategy Layer"
        D1[TaskExecutor 接口]
        D2[LogTaskExecutor]
        D3[EmailTaskExecutor]
        D4[可扩展...]
    end

    subgraph "领域层 Domain Layer"
        E1[ScheduledTask 实体<br/>scheduleMode: ONCE/CRON]
        E2[TaskExecutionLog 实体]
        E3[枚举: TaskType, TaskStatus]
    end

    subgraph "基础设施层 Infrastructure Layer"
        F1[Repository 仓储]
        F2[DistributedLock 分布式锁]
        F3[配置管理]
    end

    subgraph "持久化层 Persistence Layer"
        G1[(MySQL<br/>scheduled_task<br/>task_execution_log<br/>QRTZ_*)]
        G2[(Redis)]
    end

    A1 --> B1
    B1 --> C1
    C1 --> C2
    C1 --> C3
    C2 --> D1
    C3 --> D1
    D1 --> D2
    D1 --> D3
    D1 --> D4
    B1 --> E1
    C2 --> E1
    C3 --> E1
    C2 --> F1
    C3 --> F1
    C2 --> F2
    C3 --> F2
    F1 --> G1
    F2 --> G2

    style C1 fill:#fff9c4
    style C2 fill:#c5e1a5
    style C3 fill:#90caf9
    style D1 fill:#ffcc80
```

---

## 三、核心类关系图

```mermaid
classDiagram
    class TaskController {
        -TaskManagementService taskService
        +createOnceTask() ApiResponse
        +createCronTask() ApiResponse
        +createTask() ApiResponse
        +getTask() ApiResponse
        +getAllTasks() ApiResponse
        +cancelTask() ApiResponse
        +getSchedulerStatus() ApiResponse
    }

    class TaskManagementService {
        -ScheduledTaskRepository taskRepository
        -TaskScheduler taskScheduler
        +createOnceTask() ScheduledTask
        +createCronTask() ScheduledTask
        +getTaskById() ScheduledTask
        +cancelTask() boolean
        +getSchedulerStatus() Map
    }

    class TaskScheduler {
        <<interface>>
        +init() void
        +scheduleTask(task) void
        +executeTask(taskId) void
        +cancelTask(taskId) boolean
        +destroy() void
        +getSchedulerStatus() Map
        +getSchedulerType() String
    }

    class SimpleTaskScheduler {
        -ScheduledThreadPoolExecutor scheduler
        -ConcurrentHashMap scheduledTasks
        -Map executorMap
        -DistributedLock lock
        +init() void
        +scheduleTask(task) void
        +executeTask(taskId) void
        +loadAllPendingOnceTasks() void
    }

    class QuartzTaskScheduler {
        -Scheduler quartzScheduler
        -Map executorMap
        -DistributedLock lock
        +init() void
        +scheduleTask(task) void
        +executeTask(taskId) void
        +loadAllPendingTasks() void
    }

    class ScheduledTaskJob {
        -TaskScheduler taskScheduler
        +executeInternal(context) void
    }

    class TaskExecutor {
        <<interface>>
        +support(TaskType) boolean
        +execute(ScheduledTask) void
        +getName() String
    }

    class LogTaskExecutor {
        +support(TaskType) boolean
        +execute(ScheduledTask) void
    }

    class DistributedLock {
        <<interface>>
        +tryLock(key, expire) boolean
        +unlock(key) void
    }

    class ScheduledTask {
        +Long id
        +String taskName
        +TaskType taskType
        +ScheduleMode scheduleMode
        +LocalDateTime executeTime
        +String cronExpression
        +TaskStatus status
        +Integer retryCount
    }

    TaskController --> TaskManagementService
    TaskManagementService --> TaskScheduler
    TaskManagementService --> ScheduledTaskRepository
    TaskScheduler <|.. SimpleTaskScheduler
    TaskScheduler <|.. QuartzTaskScheduler
    SimpleTaskScheduler --> TaskExecutor
    QuartzTaskScheduler --> TaskExecutor
    QuartzTaskScheduler --> ScheduledTaskJob
    ScheduledTaskJob --> TaskScheduler
    TaskExecutor <|.. LogTaskExecutor
    SimpleTaskScheduler --> DistributedLock
    QuartzTaskScheduler --> DistributedLock
    SimpleTaskScheduler ..> ScheduledTask
    QuartzTaskScheduler ..> ScheduledTask
```

---

## 四、任务创建流程

### ONCE 模式（一次性任务）

```mermaid
sequenceDiagram
    actor User
    participant Controller as TaskController
    participant Service as TaskManagementService
    participant Scheduler as TaskScheduler<br/>(Simple/Quartz)
    participant Repo as TaskRepository
    participant DB as MySQL

    User->>Controller: POST /api/tasks/once<br/>{taskName, executeTime...}
    activate Controller
    
    Controller->>Controller: 参数校验 @Valid
    
    Controller->>Service: createOnceTask(...)
    activate Service
    
    Service->>Service: 验证 executeTime > now()
    
    Service->>Repo: save(task)<br/>scheduleMode=ONCE
    Repo->>DB: INSERT INTO scheduled_task
    DB-->>Repo: 返回 task with id
    Repo-->>Service: ScheduledTask
    
    Service->>Scheduler: scheduleTask(task)
    activate Scheduler
    
    alt Simple 调度器
        Scheduler->>Scheduler: 计算 delay = executeTime - now
        Scheduler->>Scheduler: scheduler.schedule(executeTask, delay)
        Scheduler->>Scheduler: scheduledTasks.put(id, future)
    else Quartz 调度器
        Scheduler->>Scheduler: 创建 JobDetail + SimpleTrigger
        Scheduler->>Scheduler: quartzScheduler.scheduleJob(job, trigger)
    end
    
    Scheduler-->>Service: 调度成功
    deactivate Scheduler
    
    Service-->>Controller: ScheduledTask
    deactivate Service
    
    Controller-->>User: ApiResponse<ScheduledTask>
    deactivate Controller
```

### CRON 模式（周期性任务，仅 Quartz）

```mermaid
sequenceDiagram
    actor User
    participant Controller as TaskController
    participant Service as TaskManagementService
    participant Scheduler as QuartzTaskScheduler
    participant Quartz as Quartz Scheduler
    participant Repo as TaskRepository
    participant DB as MySQL

    User->>Controller: POST /api/tasks/cron<br/>{taskName, cronExpression...}
    activate Controller
    
    Controller->>Controller: 验证 cronExpression 非空
    
    Controller->>Service: createCronTask(...)
    activate Service
    
    Service->>Repo: save(task)<br/>scheduleMode=CRON
    Repo->>DB: INSERT INTO scheduled_task
    DB-->>Repo: 返回 task with id
    Repo-->>Service: ScheduledTask
    
    Service->>Scheduler: scheduleTask(task)
    activate Scheduler
    
    Scheduler->>Scheduler: 创建 JobDetail
    Scheduler->>Scheduler: 创建 CronTrigger<br/>withCronExpression(cron)
    Scheduler->>Quartz: scheduleJob(jobDetail, cronTrigger)
    Quartz-->>Scheduler: 调度成功
    
    Scheduler-->>Service: 调度成功
    deactivate Scheduler
    
    Service-->>Controller: ScheduledTask
    deactivate Service
    
    Controller-->>User: ApiResponse<ScheduledTask>
    deactivate Controller
    
    Note over Quartz: Cron 表达式周期触发<br/>持续执行
```

---

## 五、任务执行流程

```mermaid
sequenceDiagram
    participant Timer as 调度触发器<br/>(ThreadPool/Quartz)
    participant Scheduler as TaskScheduler
    participant Lock as DistributedLock
    participant Repo as TaskRepository
    participant Executor as TaskExecutor
    participant LogRepo as LogRepository
    participant DB as MySQL

    Timer->>Scheduler: 触发 executeTask(taskId)
    activate Scheduler

    Scheduler->>Lock: tryLock("task:" + taskId, 300s)
    activate Lock
    
    alt 获取锁失败
        Lock-->>Scheduler: false
        Scheduler-->>Timer: 其他节点正在执行，退出
    else 获取锁成功
        Lock-->>Scheduler: true
        
        Scheduler->>Repo: findById(taskId)
        Repo->>DB: SELECT * FROM scheduled_task
        DB-->>Repo: ScheduledTask
        Repo-->>Scheduler: task
        
        alt ONCE 模式且状态非 PENDING
            Scheduler-->>Scheduler: 跳过执行
        else 可以执行
            Scheduler->>Repo: 更新 status=EXECUTING
            Repo->>DB: UPDATE scheduled_task
            
            Scheduler->>Executor: execute(task)
            activate Executor
            
            alt 执行成功
                Executor-->>Scheduler: 成功
                
                alt ONCE 模式
                    Scheduler->>Repo: status=SUCCESS
                else CRON 模式
                    Scheduler->>Repo: status=PENDING<br/>(等待下次触发)
                end
                
                Scheduler->>LogRepo: save(log, status=SUCCESS)
                
            else 执行失败
                Executor-->>Scheduler: 异常
                Scheduler->>Scheduler: retryCount++
                
                alt ONCE 且 retryCount < maxRetry
                    Scheduler->>Repo: executeTime=now+60s<br/>status=PENDING
                    Scheduler->>Scheduler: 重新调度任务
                    Note over Scheduler: 即时重试机制
                else ONCE 且达到最大重试
                    Scheduler->>Repo: status=FAILED
                    Note over Scheduler: 最终失败
                else CRON 模式
                    Scheduler->>Repo: status=PENDING
                    Note over Scheduler: 等待下次触发
                end
                
                Scheduler->>LogRepo: save(log, status=FAILED, error)
            end
            
            deactivate Executor
        end
        
        Scheduler->>Lock: unlock("task:" + taskId)
        Lock-->>Scheduler: 锁已释放
    end
    
    deactivate Lock
    deactivate Scheduler
```

---

## 六、调度器选择机制

```mermaid
flowchart TD
    Start([应用启动]) --> LoadConfig[读取配置<br/>scheduled.task.scheduler-type]
    
    LoadConfig --> CheckType{scheduler-type<br/>值是什么?}
    
    CheckType -->|simple 或未配置| ActivateSimple[@ConditionalOnProperty<br/>havingValue=simple<br/>matchIfMissing=true]
    
    CheckType -->|quartz| ActivateQuartz[@ConditionalOnProperty<br/>havingValue=quartz]
    
    ActivateSimple --> CreateSimple[创建 SimpleTaskScheduler Bean]
    ActivateQuartz --> CreateQuartz[创建 QuartzTaskScheduler Bean]
    
    CreateSimple --> InitSimple[初始化线程池<br/>加载 ONCE 任务]
    CreateQuartz --> InitQuartz[初始化 Quartz<br/>加载 ONCE + CRON 任务]
    
    InitSimple --> RegisterService[注册到 TaskManagementService]
    InitQuartz --> RegisterService
    
    RegisterService --> Ready([系统就绪<br/>接收任务请求])
    
    style CheckType fill:#fff9c4
    style CreateSimple fill:#c5e1a5
    style CreateQuartz fill:#90caf9
    style Ready fill:#a5d6a7
```

---

## 七、调度器对比架构

### Simple 调度器架构

```mermaid
graph TB
    subgraph "SimpleTaskScheduler"
        Init[启动初始化]
        ThreadPool[ScheduledThreadPoolExecutor<br/>核心线程数: 10]
        TaskMap[ConcurrentHashMap<br/>Long → ScheduledFuture]
        Schedule[schedule 方法<br/>计算延迟秒数]
        Execute[executeTask 方法<br/>执行 + 重试]
    end
    
    subgraph "支持特性"
        OnceOnly[仅 ONCE 模式]
        Memory[全量内存调度]
        Retry[即时重试机制]
        LocalFirst[优先本地锁]
    end
    
    Init --> ThreadPool
    Init --> TaskMap
    Schedule --> TaskMap
    ThreadPool --> Execute
    
    OnceOnly -.限制.-> Schedule
    Memory -.特点.-> TaskMap
    Retry -.特点.-> Execute
    LocalFirst -.默认.-> Execute
    
    style ThreadPool fill:#c5e1a5
    style OnceOnly fill:#ffccbc
```

### Quartz 调度器架构

```mermaid
graph TB
    subgraph "QuartzTaskScheduler"
        Init[启动初始化]
        QuartzCore[Quartz Scheduler 核心]
        JobStore[JDBC JobStore<br/>数据库持久化]
        Trigger[Trigger 管理<br/>SimpleTrigger/CronTrigger]
        JobBean[ScheduledTaskJob<br/>Job 执行包装]
        Execute[executeTask 方法<br/>执行 + 重试]
    end
    
    subgraph "支持特性"
        BothMode[ONCE + CRON 模式]
        Cluster[原生集群支持]
        Persistent[持久化调度状态]
        Cron[Cron 表达式解析]
    end
    
    Init --> QuartzCore
    QuartzCore --> JobStore
    QuartzCore --> Trigger
    Trigger --> JobBean
    JobBean --> Execute
    
    BothMode -.支持.-> Trigger
    Cluster -.特点.-> JobStore
    Persistent -.特点.-> JobStore
    Cron -.特点.-> Trigger
    
    style QuartzCore fill:#90caf9
    style BothMode fill:#c5e1a5
```

---

## 八、数据库 ER 图

```mermaid
erDiagram
    SCHEDULED_TASK ||--o{ TASK_EXECUTION_LOG : "执行历史"
    SCHEDULED_TASK {
        BIGINT id PK "主键ID"
        VARCHAR task_name "任务名称"
        VARCHAR task_type "任务类型: LOG/EMAIL/SMS/WEBHOOK/MQ"
        VARCHAR schedule_mode "调度模式: ONCE/CRON"
        DATETIME execute_time "执行时间(ONCE)"
        VARCHAR cron_expression "Cron表达式(CRON)"
        JSON task_data "任务数据"
        VARCHAR status "状态: PENDING/EXECUTING/SUCCESS/FAILED/CANCELLED"
        INT retry_count "已重试次数"
        INT max_retry_count "最大重试次数"
        DATETIME last_execute_time "最后执行时间"
        TEXT error_message "错误信息"
        DATETIME created_at "创建时间"
        DATETIME updated_at "更新时间"
    }
    
    TASK_EXECUTION_LOG {
        BIGINT id PK "主键ID"
        BIGINT task_id FK "关联任务ID"
        DATETIME execute_time "执行时间"
        VARCHAR status "执行状态"
        TEXT error_message "错误信息"
        BIGINT execution_duration_ms "执行耗时(ms)"
        DATETIME created_at "创建时间"
    }
    
    QRTZ_JOB_DETAILS ||--o{ QRTZ_TRIGGERS : "Job定义"
    QRTZ_JOB_DETAILS {
        VARCHAR SCHED_NAME PK
        VARCHAR JOB_NAME PK
        VARCHAR JOB_GROUP PK
        VARCHAR JOB_CLASS_NAME "Job类名"
        BLOB JOB_DATA "Job数据"
    }
    
    QRTZ_TRIGGERS ||--o| QRTZ_CRON_TRIGGERS : "Cron触发器"
    QRTZ_TRIGGERS ||--o| QRTZ_SIMPLE_TRIGGERS : "Simple触发器"
    QRTZ_TRIGGERS {
        VARCHAR SCHED_NAME PK
        VARCHAR TRIGGER_NAME PK
        VARCHAR TRIGGER_GROUP PK
        VARCHAR TRIGGER_STATE "触发器状态"
        VARCHAR TRIGGER_TYPE "触发器类型"
        BIGINT NEXT_FIRE_TIME "下次触发时间"
    }
    
    QRTZ_CRON_TRIGGERS {
        VARCHAR SCHED_NAME PK
        VARCHAR TRIGGER_NAME PK
        VARCHAR TRIGGER_GROUP PK
        VARCHAR CRON_EXPRESSION "Cron表达式"
    }
```

---

## 九、部署架构图

### 单机部署 - Simple 调度器

```mermaid
graph TB
    subgraph "单机服务器"
        subgraph "SpringBoot 应用"
            App[ScheduledTaskApplication]
            SimpleScheduler[SimpleTaskScheduler]
            ThreadPool[ScheduledThreadPoolExecutor]
            LocalLock[LocalDistributedLock]
            Executor[TaskExecutor实现类]
        end
        
        MySQL[(MySQL Database<br/>scheduled_task<br/>task_execution_log)]
    end
    
    Client[客户端] --> App
    App --> SimpleScheduler
    SimpleScheduler --> ThreadPool
    SimpleScheduler --> LocalLock
    SimpleScheduler --> Executor
    SimpleScheduler --> MySQL
    
    style SimpleScheduler fill:#c5e1a5
    style LocalLock fill:#ffccbc
```

### 集群部署 - Quartz 调度器

```mermaid
graph TB
    subgraph "负载均衡层"
        LB[Nginx/LoadBalancer]
    end

    subgraph "应用集群"
        subgraph "节点1"
            App1[SpringBoot应用1]
            Quartz1[QuartzTaskScheduler]
        end
        
        subgraph "节点2"
            App2[SpringBoot应用2]
            Quartz2[QuartzTaskScheduler]
        end
        
        subgraph "节点3"
            App3[SpringBoot应用3]
            Quartz3[QuartzTaskScheduler]
        end
    end

    subgraph "数据层"
        subgraph "MySQL 主从"
            Master[(MySQL Master<br/>scheduled_task<br/>task_execution_log<br/>QRTZ_*)]
            Slave[(MySQL Slave<br/>只读副本)]
        end
        
        Redis[(Redis 集群<br/>分布式锁)]
    end

    Client[客户端] --> LB
    LB --> App1
    LB --> App2
    LB --> App3
    
    App1 --> Quartz1
    App2 --> Quartz2
    App3 --> Quartz3
    
    Quartz1 --> Master
    Quartz2 --> Master
    Quartz3 --> Master
    
    Master -.同步.-> Slave
    
    Quartz1 -.分布式锁.-> Redis
    Quartz2 -.分布式锁.-> Redis
    Quartz3 -.分布式锁.-> Redis
    
    style Quartz1 fill:#90caf9
    style Quartz2 fill:#90caf9
    style Quartz3 fill:#90caf9
    style Master fill:#a5d6a7
```

---

## 十、系统状态流转图

```mermaid
stateDiagram-v2
    [*] --> PENDING: 任务创建

    PENDING --> EXECUTING: 到达执行时间<br/>开始执行

    EXECUTING --> SUCCESS: 执行成功
    EXECUTING --> FAILED: 执行失败

    state is_once <<choice>>
    state can_retry <<choice>>
    
    FAILED --> is_once: 判断模式
    
    is_once --> can_retry: ONCE 模式
    is_once --> PENDING: CRON 模式<br/>等待下次触发
    
    can_retry --> PENDING: retryCount < maxRetryCount<br/>即时重新调度
    can_retry --> FAILED_FINAL: retryCount >= maxRetryCount<br/>最终失败
    
    SUCCESS --> SUCCESS_ONCE: ONCE 模式
    SUCCESS --> PENDING: CRON 模式<br/>等待下次触发
    
    SUCCESS_ONCE --> [*]: 任务完成
    FAILED_FINAL --> [*]: 任务失败

    PENDING --> CANCELLED: 用户取消
    CANCELLED --> [*]: 任务取消

    note right of PENDING
        待执行状态
        - ONCE: 等待执行时间
        - CRON: 等待Cron触发
    end note

    note right of EXECUTING
        执行中状态
        - 已获取分布式锁
        - 正在调用执行器
    end note

    note right of FAILED
        失败状态
        - 记录错误信息
        - 判断是否重试
    end note
```

---

## 十一、核心设计模式

### 1. 策略模式 - 调度器切换

```mermaid
classDiagram
    class TaskScheduler {
        <<interface>>
        +scheduleTask()
        +executeTask()
        +cancelTask()
    }
    
    class SimpleTaskScheduler {
        +scheduleTask()
        +executeTask()
    }
    
    class QuartzTaskScheduler {
        +scheduleTask()
        +executeTask()
    }
    
    class TaskManagementService {
        -TaskScheduler scheduler
        +createTask()
    }
    
    TaskScheduler <|.. SimpleTaskScheduler
    TaskScheduler <|.. QuartzTaskScheduler
    TaskManagementService --> TaskScheduler
    
    note for TaskManagementService "通过依赖注入自动选择<br/>@ConditionalOnProperty决定"
```

### 2. 策略模式 - 执行器扩展

```mermaid
classDiagram
    class TaskExecutor {
        <<interface>>
        +support(TaskType)
        +execute(ScheduledTask)
    }
    
    class LogTaskExecutor {
        +support(LOG)
        +execute()
    }
    
    class EmailTaskExecutor {
        +support(EMAIL)
        +execute()
    }
    
    class CustomExecutor {
        +support(CUSTOM)
        +execute()
    }
    
    class TaskScheduler {
        -Map~TaskType, TaskExecutor~ executorMap
        +executeTask()
    }
    
    TaskExecutor <|.. LogTaskExecutor
    TaskExecutor <|.. EmailTaskExecutor
    TaskExecutor <|.. CustomExecutor
    TaskScheduler --> TaskExecutor
    
    note for TaskScheduler "自动扫描所有实现<br/>构建类型映射表"
```

---

## 十二、关键技术决策

### 1. 为什么需要两种调度器？

| 场景 | 推荐调度器 | 原因 |
|------|-----------|------|
| 一次性定时任务 | Simple | 轻量、快速、无额外依赖 |
| 周期性 Cron 任务 | Quartz | 原生支持 Cron 表达式 |
| 小规模（< 1000 任务） | Simple | 内存占用小、启动快 |
| 大规模（> 1000 任务） | Quartz | 数据库持久化、集群支持 |
| 单机部署 | Simple | 简单直接 |
| 集群部署 | Quartz | 原生集群协调 |

### 2. 数据持久化策略

- **业务数据**：统一存储在 `scheduled_task` 和 `task_execution_log` 表
- **调度状态**：
  - Simple：内存中管理（重启恢复）
  - Quartz：持久化到 `QRTZ_*` 表（支持集群）

### 3. 分布式锁设计

```java
// 两种调度器共享锁接口
public interface DistributedLock {
    boolean tryLock(String lockKey, long expireSeconds);
    void unlock(String lockKey);
}

// 防止集群环境下任务重复执行
if (!distributedLock.tryLock("task:" + taskId, 300)) {
    return; // 其他节点正在执行
}
```

### 4. 即时重试机制

```java
// 失败后立即重新调度，无需等待周期扫描
if (retryCount < maxRetryCount) {
    task.setExecuteTime(now.plusSeconds(60));
    task.setStatus(PENDING);
    scheduleTask(task); // 立即加入调度队列
}
```

---

## 十三、监控和运维

### 调度器状态监控

```bash
GET /api/tasks/scheduler/status
```

**Simple 调度器指标**：
- `activeCount`: 当前执行中的任务数
- `poolSize`: 线程池大小
- `queueSize`: 等待队列长度
- `scheduledTaskCount`: 已调度任务数
- `completedTaskCount`: 已完成任务数

**Quartz 调度器指标**：
- `numberOfJobsExecuted`: 执行总数
- `isStarted`: 是否启动
- `isInStandbyMode`: 是否待机
- `runningSince`: 运行开始时间

### 告警规则

- `queueSize > 100`: 任务堆积
- `activeCount == poolSize`: 线程池满载
- 执行失败率 > 10%: 系统异常

---

## 总结

本系统采用**双调度器架构**，通过**策略模式**实现灵活切换：

- ✅ **Simple 调度器**：轻量级、快速启动、适合简单场景
- ✅ **Quartz 调度器**：功能强大、支持 Cron、适合复杂调度
- ✅ **无缝切换**：配置文件一键切换，数据不丢失
- ✅ **高扩展性**：执行器策略模式，易于扩展新功能
- ✅ **生产就绪**：分布式锁、失败重试、集群支持

**架构优势**：
1. 简单场景不引入重量级依赖
2. 复杂需求可平滑升级
3. 统一接口保证代码一致性
4. 策略模式保证扩展性
