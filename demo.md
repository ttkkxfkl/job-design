```mermaid
classDiagram
    %% 实体类
    class ScheduledTask {
        +Long id
        +String taskName
        +TaskType taskType
        +ScheduleMode scheduleMode
        +LocalDateTime executeTime
        +String cronExpression
        +Integer priority
        +Long executionTimeout
        +Map~String,Object~ taskData
        +TaskStatus status
        +Integer retryCount
        +Integer maxRetryCount
        +LocalDateTime lastExecuteTime
        +String errorMessage
        +LocalDateTime createdAt
        +LocalDateTime updatedAt
        +ScheduleMode ONCE
        +ScheduleMode CRON
        +TaskType LOG
        +TaskType EMAIL
        +TaskType SMS
        +TaskType WEBHOOK
        +TaskType MQ
        +TaskType PLAN
        +TaskType ALERT
        +TaskStatus PENDING
        +TaskStatus EXECUTING
        +TaskStatus SUCCESS
        +TaskStatus FAILED
        +TaskStatus CANCELLED
        +TaskStatus PAUSED
        +TaskStatus TIMEOUT
    }
    
    class TaskExecutionLog {
        +Long id
        +Long taskId
        +LocalDateTime executeTime
        +ScheduledTask.TaskStatus status
        +String errorMessage
        +Long executionDurationMs
        +LocalDateTime createdAt
    }
    
    class TaskStatistics {
        +Integer pendingCount
        +Integer executingCount
        +Integer successCount
        +Integer failedCount
        +Integer cancelledCount
        +Integer pausedCount
        +Integer timeoutCount
        +Integer totalCount
        +Double successRate
        +Double avgExecutionDuration
        +Long maxExecutionDuration
        +Long minExecutionDuration
    }
    
    class DailyTaskStatistics {
        +String date
        +Long executedCount
        +Long successCount
        +Long failedCount
        +Long timeoutCount
        +Double successRate
    }
    
    class TaskTypeStatistics {
        +ScheduledTask.TaskType taskType
        +Long count
        +Double percentage
    }
    
    %% DTO类
    class CreateTaskRequest {
        +String taskName
        +ScheduledTask.TaskType taskType
        +LocalDateTime executeTime
        +String cronExpression
        +Integer priority
        +Long executionTimeout
        +Map~String,Object~ taskData
        +Integer maxRetryCount
    }
    
    class TaskTypeInfo {
        +String code
        +String name
        +String description
    }
    
    class ApiResponse {
        +Integer code
        +String message
        +T data
        +success(T data)
        +success(String message, T data)
        +error(String message)
        +error(Integer code, String message)
    }
    
    %% 注解类
    class TaskExecutorInfo {
        +ScheduledTask.TaskType taskType()
        +String displayName()
        +String description()
        +int priority()
    }
    
    %% 接口类
    class TaskExecutor {
        +boolean support(ScheduledTask.TaskType taskType)
        +void execute(ScheduledTask task)
        +String getName()
    }
    
    class TaskScheduler {
        +void init()
        +void scheduleTask(ScheduledTask task)
        +void executeTask(Long taskId)
        +boolean cancelTask(Long taskId)
        +void destroy()
        +Map~String,Object~ getSchedulerStatus()
        +String getSchedulerType()
    }
    
    class DistributedLock {
        +boolean tryLock(String lockKey, long expireSeconds)
        +void unlock(String lockKey)
    }
    
    %% 配置类
    class ScheduledTaskProperties {
        +String schedulerType
        +int corePoolSize
        +int maxRetryCount
        +long retryIntervalSeconds
        +String lockType
    }
    
    %% 控制器类
    class TaskController {
        +ApiResponse~ScheduledTask~ createOnceTask(CreateTaskRequest request)
        +ApiResponse~ScheduledTask~ createCronTask(CreateTaskRequest request)
        +ApiResponse~ScheduledTask~ createTask(CreateTaskRequest request)
        +ApiResponse~ScheduledTask~ getTask(Long id)
        +ApiResponse~List~ScheduledTask~~ getAllTasks(ScheduledTask.TaskStatus status)
        +ApiResponse~Boolean~ cancelTask(Long id)
        +ApiResponse~Boolean~ pauseTask(Long id)
        +ApiResponse~Boolean~ resumeTask(Long id)
        +ApiResponse~Boolean~ retryTask(Long id)
        +ApiResponse~List~TaskExecutionLog~~ getTaskLogs(Long id)
        +ApiResponse~Map~String,Object~~ getSchedulerStatus()
        +ApiResponse~Long~ countPendingTasks()
        +ApiResponse~TaskStatistics~ getStatisticsSummary()
        +ApiResponse~List~DailyTaskStatistics~~ getDailyStatistics(int days)
        +ApiResponse~List~TaskTypeStatistics~~ getTypeDistribution()
        +ApiResponse~Map~String,Long~~ getModeDistribution()
        +ApiResponse~Map~String,Long~~ getStatusDistribution()
        +ApiResponse~List~TaskTypeInfo~~ getTaskTypes()
    }
    
    %% 服务类
    class TaskManagementService {
        +ScheduledTask createOnceTask(...)
        +ScheduledTask createCronTask(...)
        +ScheduledTask getTaskById(Long id)
        +List~ScheduledTask~ getAllTasks()
        +List~ScheduledTask~ getTasksByStatus(ScheduledTask.TaskStatus status)
        +boolean cancelTask(Long taskId)
        +boolean pauseTask(Long taskId)
        +boolean resumeTask(Long taskId)
        +boolean retryTaskNow(Long taskId)
        +List~TaskExecutionLog~ getTaskExecutionLogs(Long taskId)
        +long countPendingTasks()
        +Map~String,Object~ getSchedulerStatus()
    }
    
    class TaskStatisticsService {
        +TaskStatistics getOverallStatistics()
        +List~DailyTaskStatistics~ getDailyStatistics(int days)
        +List~TaskTypeStatistics~ getTaskTypeDistribution()
        +Map~String,Long~ getScheduleModeDistribution()
        +Map~String,Long~ getStatusDistribution()
    }
    
    class TaskTypeInfoService {
        +List~TaskTypeInfo~ getAllTaskTypes()
        +TaskTypeInfo getTaskTypeInfo(ScheduledTask.TaskType taskType)
        +boolean isTaskTypeRegistered(ScheduledTask.TaskType taskType)
    }
    
    %% 执行器实现类
    class LogTaskExecutor {
        +boolean support(ScheduledTask.TaskType taskType)
        +void execute(ScheduledTask task)
        +String getName()
    }
    
    class EmailTaskExecutor {
        +boolean support(ScheduledTask.TaskType taskType)
        +void execute(ScheduledTask task)
        +String getName()
    }
    
    class SmsTaskExecutor {
        +boolean support(ScheduledTask.TaskType taskType)
        +void execute(ScheduledTask task)
        +String getName()
    }
    
    class WebhookTaskExecutor {
        +boolean support(ScheduledTask.TaskType taskType)
        +void execute(ScheduledTask task)
        +String getName()
    }
    
    class PlanTaskExecutor {
        +boolean support(ScheduledTask.TaskType taskType)
        +void execute(ScheduledTask task)
        +String getName()
    }
    
    %% 调度器实现类
    class SimpleTaskScheduler {
        +void init()
        +void scheduleTask(ScheduledTask task)
        +void executeTask(Long taskId)
        +boolean cancelTask(Long taskId)
        +void destroy()
        +Map~String,Object~ getSchedulerStatus()
        +String getSchedulerType()
    }
    
    class QuartzTaskScheduler {
        +void init()
        +void scheduleTask(ScheduledTask task)
        +void executeTask(Long taskId)
        +boolean cancelTask(Long taskId)
        +void destroy()
        +Map~String,Object~ getSchedulerStatus()
        +String getSchedulerType()
    }
    
    %% 锁实现类
    class LocalDistributedLock {
        +boolean tryLock(String lockKey, long expireSeconds)
        +void unlock(String lockKey)
    }
    
    class RedisDistributedLock {
        +boolean tryLock(String lockKey, long expireSeconds)
        +void unlock(String lockKey)
    }
    
    %% 数据访问层
    class ScheduledTaskRepository {
        <<interface>>
    }
    
    class TaskExecutionLogRepository {
        <<interface>>
    }
    
    %% Job类
    class ScheduledTaskJob {
        +executeInternal(JobExecutionContext context)
    }
    
    %% 关系定义
    
    %% 实体关系
    ScheduledTask --> "任务日志" TaskExecutionLog : 1..*
    
    %% DTO关系
    ApiResponse --> "数据泛型" Object : uses
    
    %% 注解关系
    TaskExecutorInfo --> ScheduledTask : 标注任务类型
    
    %% 接口实现关系
    LogTaskExecutor ..|> TaskExecutor
    EmailTaskExecutor ..|> TaskExecutor
    SmsTaskExecutor ..|> TaskExecutor
    WebhookTaskExecutor ..|> TaskExecutor
    PlanTaskExecutor ..|> TaskExecutor
    
    SimpleTaskScheduler ..|> TaskScheduler
    QuartzTaskScheduler ..|> TaskScheduler
    
    LocalDistributedLock ..|> DistributedLock
    RedisDistributedLock ..|> DistributedLock
    
    %% 控制器依赖关系
    TaskController --> TaskManagementService
    TaskController --> TaskStatisticsService
    TaskController --> TaskTypeInfoService
    TaskController --> ApiResponse
    
    %% 服务依赖关系
    TaskManagementService --> ScheduledTaskRepository
    TaskManagementService --> TaskExecutionLogRepository
    TaskManagementService --> TaskScheduler
    
    TaskStatisticsService --> ScheduledTaskRepository
    TaskStatisticsService --> TaskExecutionLogRepository
    
    TaskTypeInfoService --> TaskExecutor
    
    %% 执行器注解关系
    LogTaskExecutor --> TaskExecutorInfo
    EmailTaskExecutor --> TaskExecutorInfo
    SmsTaskExecutor --> TaskExecutorInfo
    WebhookTaskExecutor --> TaskExecutorInfo
    PlanTaskExecutor --> TaskExecutorInfo
    
    %% 调度器依赖关系
    SimpleTaskScheduler --> ScheduledTaskRepository
    SimpleTaskScheduler --> TaskExecutionLogRepository
    SimpleTaskScheduler --> ScheduledTaskProperties
    SimpleTaskScheduler --> DistributedLock
    SimpleTaskScheduler --> TaskExecutor
    
    QuartzTaskScheduler --> ScheduledTaskRepository
    QuartzTaskScheduler --> TaskExecutionLogRepository
    QuartzTaskScheduler --> ScheduledTaskProperties
    QuartzTaskScheduler --> DistributedLock
    QuartzTaskScheduler --> TaskExecutor
    QuartzTaskScheduler --> ScheduledTaskJob
    
    %% 锁依赖关系
    RedisDistributedLock --> "Redis模板" StringRedisTemplate
    
    %% Job依赖关系
    ScheduledTaskJob --> TaskScheduler
    
    %% 配置类使用关系
    SimpleTaskScheduler --> ScheduledTaskProperties
    QuartzTaskScheduler --> ScheduledTaskProperties
    LocalDistributedLock --> ScheduledTaskProperties
    RedisDistributedLock --> ScheduledTaskProperties
    
    %% 类注解关系
    TaskController --> "@RestController"
    TaskController --> "@RequestMapping"
    ScheduledTask --> "@TableName"
    TaskExecutionLog --> "@TableName"
    ScheduledTaskRepository --> "@Mapper"
    TaskExecutionLogRepository --> "@Mapper"
    LogTaskExecutor --> "@Component"
    EmailTaskExecutor --> "@Component"
    SmsTaskExecutor --> "@Component"
    WebhookTaskExecutor --> "@Component"
    PlanTaskExecutor --> "@Component"
    ScheduledTaskJob --> "@Component"
    LocalDistributedLock --> "@Component"
    RedisDistributedLock --> "@Component"
    SimpleTaskScheduler --> "@Service"
    QuartzTaskScheduler --> "@Service"
    TaskManagementService --> "@Service"
    TaskStatisticsService --> "@Service"
    TaskTypeInfoService --> "@Service"
    ScheduledTaskProperties --> "@Component"
    ScheduledTaskProperties --> "@ConfigurationProperties"


```