# 告警系统恢复机制与Quartz持久化冲突解决方案

## 问题描述

当前系统存在双重恢复机制冲突：
1. **Quartz自动恢复**：Quartz会持久化任务到数据库，重启后自动加载
2. **Alert手动恢复**：AlertRecoveryService会重新创建任务

这导致：
- **重复执行**：同一个告警评估被触发两次
- **状态不同步**：PENDING_TASK_MAP是内存Map，重启后清空
- **无法取消**：找不到旧任务ID，无法正确取消

## 解决方案：任务ID持久化 + 启动清理

### 1. 将任务ID持久化到数据库

**修改 ExceptionEvent.pending_escalations 结构：**
```json
{
  "LEVEL_2": {
    "status": "WAITING",
    "dependencies": [...],
    "taskId": "12345",           // 新增：持久化任务ID
    "scheduledTime": "2025-12-12T10:30:00",
    "createdAt": "2025-12-12T10:02:00"
  }
}
```

**好处：**
- 任务ID随异常事件持久化，重启后仍可访问
- 可以精确定位需要清理的Quartz任务

### 2. 启动时清理旧任务

**修改 AlertRecoveryService.recoverSingleEvent()：**

```java
@Transactional(rollbackFor = Exception.class)
private void recoverSingleEvent(ExceptionEvent event) {
    log.info("开始恢复异常事件: id={}, level={}", event.getId(), event.getCurrentAlertLevel());

    try {
        // 【新增】步骤1: 清理Quartz中的旧任务，避免重复执行
        if (event.getPendingEscalations() != null && !event.getPendingEscalations().isEmpty()) {
            cleanupOldQuartzTasks(event);
            log.info("已清理旧的Quartz任务: exceptionEventId={}", event.getId());
        }

        // 步骤2: 重新调度所有待机的升级任务
        if (event.getPendingEscalations() != null && !event.getPendingEscalations().isEmpty()) {
            reschedulePendingEscalations(event);
            log.info("已重新调度待机升级任务: exceptionEventId={}", event.getId());
        }

        // 步骤3: 标记为已恢复
        event.setRecoveryFlag(true);
        exceptionEventRepository.updateById(event);

        // 步骤4: 发布恢复事件
        eventPublisher.publishEvent(new AlertRecoveredEvent(
                this, event.getId(), 1, "系统恢复: 已清理旧任务并重新调度"
        ));

        log.info("异常事件恢复完成: exceptionEventId={}", event.getId());

    } catch (Exception e) {
        log.error("恢复异常事件失败: exceptionEventId={}", event.getId(), e);
        throw new RuntimeException("恢复异常事件失败: " + e.getMessage(), e);
    }
}

/**
 * 清理Quartz中的旧任务
 * 防止旧任务与新任务重复执行
 */
private void cleanupOldQuartzTasks(ExceptionEvent event) {
    for (String levelName : event.getPendingEscalations().keySet()) {
        Object levelData = event.getPendingEscalations().get(levelName);
        
        if (levelData instanceof Map) {
            Map<String, Object> levelStatus = (Map<String, Object>) levelData;
            String taskIdStr = (String) levelStatus.get("taskId");
            
            if (taskIdStr != null) {
                try {
                    // 调用TaskManagementService删除旧任务
                    taskManagementService.deleteTask(Long.parseLong(taskIdStr));
                    log.info("已删除旧任务: exceptionEventId={}, level={}, taskId={}", 
                            event.getId(), levelName, taskIdStr);
                } catch (Exception e) {
                    log.warn("删除旧任务失败（可能已不存在）: taskId={}", taskIdStr, e);
                    // 不抛出异常，继续处理
                }
            }
        }
    }
}
```

### 3. 修改 AlertEscalationService 持久化任务ID

**修改 createEvaluationTask() 方法：**

```java
@Transactional(rollbackFor = Exception.class)
public void createEvaluationTask(Long exceptionEventId, Long ruleId, Instant triggerTime) {
    log.info("为异常事件 [{}] 规则 [{}] 创建评估任务，触发时间: {}", exceptionEventId, ruleId, triggerTime);

    // 查询规则
    AlertRule rule = alertRuleRepository.selectById(ruleId);
    ExceptionEvent event = exceptionEventRepository.selectById(exceptionEventId);

    // 创建任务
    CreateTaskRequest request = new CreateTaskRequest();
    request.setTaskName("AlertEvaluation-" + exceptionEventId + "-" + rule.getLevel());
    request.setExecutorBeanName("alertExecutor");
    request.setTaskType("ONCE");
    request.setScheduleTime(triggerTime.atZone(java.time.ZoneId.systemDefault()).toLocalDateTime());
    
    Map<String, Object> taskData = new HashMap<>();
    taskData.put("exceptionEventId", exceptionEventId);
    taskData.put("alertRuleId", ruleId);
    taskData.put("evaluationType", "ALERT_EVALUATION");
    request.setTaskData(taskData);

    ScheduledTask task = taskManagementService.createOnceTask(request);
    String taskId = String.valueOf(task.getId());

    // 【修改】将任务ID持久化到 pending_escalations
    updatePendingEscalationsWithTaskId(event, rule.getLevel(), taskId, triggerTime);

    // 记录到内存Map（用于快速访问）
    recordPendingTask(exceptionEventId, taskId);
    
    log.info("已为异常事件 [{}] 等级 [{}] 创建评估任务: taskId={}", 
            exceptionEventId, rule.getLevel(), taskId);
}

/**
 * 更新 pending_escalations，添加任务ID
 */
private void updatePendingEscalationsWithTaskId(ExceptionEvent event, String level, 
                                                 String taskId, Instant scheduledTime) {
    Map<String, Object> pendingEscalations = event.getPendingEscalations();
    if (pendingEscalations == null) {
        pendingEscalations = new HashMap<>();
    }

    Map<String, Object> levelStatus = (Map<String, Object>) pendingEscalations.get(level);
    if (levelStatus == null) {
        levelStatus = new HashMap<>();
        pendingEscalations.put(level, levelStatus);
    }

    // 添加任务ID和调度时间
    levelStatus.put("taskId", taskId);
    levelStatus.put("scheduledTime", scheduledTime.toString());
    levelStatus.put("updatedAt", Instant.now().toString());

    event.setPendingEscalations(pendingEscalations);
    exceptionEventRepository.updateById(event);
}
```

### 4. 添加任务幂等性保护

**修改 AlertExecutor.execute()：**

```java
@Override
public void execute(Map<String, Object> taskData) {
    Long exceptionEventId = ((Number) taskData.get("exceptionEventId")).longValue();
    Long alertRuleId = ((Number) taskData.get("alertRuleId")).longValue();

    log.info("开始执行告警评估: exceptionEventId={}, alertRuleId={}", exceptionEventId, alertRuleId);

    // 【新增】幂等性检查：如果事件已解除，跳过执行
    ExceptionEvent event = exceptionEventRepository.selectById(exceptionEventId);
    if (event == null) {
        log.warn("异常事件不存在，跳过执行: exceptionEventId={}", exceptionEventId);
        return;
    }
    
    if (!"ACTIVE".equals(event.getStatus())) {
        log.info("异常事件已解除（status={}），跳过评估: exceptionEventId={}", 
                event.getStatus(), exceptionEventId);
        return;
    }

    // 【新增】检查是否已执行过此等级
    AlertRule rule = alertRuleRepository.selectById(alertRuleId);
    if (isLevelAlreadyTriggered(event, rule.getLevel())) {
        log.info("等级 [{}] 已触发过，跳过重复执行: exceptionEventId={}", 
                rule.getLevel(), exceptionEventId);
        return;
    }

    // 继续原有逻辑...
}

/**
 * 检查指定等级是否已触发过
 */
private boolean isLevelAlreadyTriggered(ExceptionEvent event, String level) {
    // 检查 alert_event_log 中是否有该等级的 ALERT_TRIGGERED 记录
    LambdaQueryWrapper<AlertEventLog> wrapper = new LambdaQueryWrapper<>();
    wrapper.eq(AlertEventLog::getExceptionEventId, event.getId())
           .eq(AlertEventLog::getAlertLevel, level)
           .eq(AlertEventLog::getEventType, "ALERT_TRIGGERED");
    
    return alertEventLogRepository.selectCount(wrapper) > 0;
}
```

## 实施步骤

### 阶段1：数据库迁移
```sql
-- 无需修改表结构，只需更新 pending_escalations JSON格式
-- 示例数据迁移（如果需要）
UPDATE exception_event 
SET pending_escalations = JSON_SET(
    pending_escalations, 
    '$.LEVEL_2.taskId', 
    NULL
)
WHERE pending_escalations IS NOT NULL;
```

### 阶段2：代码修改
1. ✅ 修改 `AlertEscalationService.createEvaluationTask()`：持久化taskId
2. ✅ 修改 `AlertRecoveryService.recoverSingleEvent()`：启动时清理旧任务
3. ✅ 修改 `AlertExecutor.execute()`：添加幂等性检查

### 阶段3：测试验证
```bash
# 测试场景1：正常恢复
1. 创建异常事件
2. 停止服务
3. 重启服务
4. 验证：只触发一次评估

# 测试场景2：等级已触发后重启
1. 创建异常事件，触发LEVEL_1
2. 停止服务
3. 重启服务
4. 验证：LEVEL_1不会重复触发

# 测试场景3：任务取消
1. 创建异常事件
2. 解除异常
3. 验证：所有待机任务被取消
```

## 关键改进点

| 问题 | 原有设计 | 改进方案 |
|------|---------|---------|
| 任务ID丢失 | PENDING_TASK_MAP（内存） | pending_escalations持久化 |
| 重复执行 | 无保护 | 启动时清理 + 幂等性检查 |
| 状态不同步 | 内存与DB分离 | 统一以DB为准 |
| 无法取消旧任务 | 找不到taskId | 从pending_escalations读取 |

## 备选方案

### 方案A：使用Quartz的@DisallowConcurrentExecution
```java
@Component("alertExecutor")
@DisallowConcurrentExecution  // 禁止并发执行
public class AlertExecutor implements TaskExecutor {
    // ...
}
```
**局限**：只能防止同一任务并发，无法防止不同任务重复执行同一事件

### 方案B：使用分布式锁
```java
@Override
public void execute(Map<String, Object> taskData) {
    String lockKey = "alert:eval:" + exceptionEventId + ":" + level;
    if (!redisLock.tryLock(lockKey, 30, TimeUnit.SECONDS)) {
        log.info("任务执行中，跳过重复执行: {}", lockKey);
        return;
    }
    try {
        // 执行逻辑
    } finally {
        redisLock.unlock(lockKey);
    }
}
```
**优点**：更健壮，支持集群
**缺点**：需要引入Redis

## 总结

推荐使用**方案2：任务ID持久化 + 启动清理 + 幂等性检查**，理由：
1. ✅ 无需额外依赖（Redis）
2. ✅ 解决所有冲突场景
3. ✅ 易于测试和维护
4. ✅ 保留Quartz持久化能力
