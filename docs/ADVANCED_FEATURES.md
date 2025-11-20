# 高级功能使用指南

## 目录

1. [超时控制](#超时控制)
2. [优先级调度](#优先级调度)
3. [任务暂停/恢复](#任务暂停恢复)
4. [统计报表](#统计报表)
5. [最佳实践](#最佳实践)

---

## 超时控制

### 功能说明

- 任务执行超过设定时间会自动中断
- 默认超时时间：300秒（5分钟）
- 超时任务会标记为 `TIMEOUT` 状态
- 支持自动重试机制

### 配置示例

```json
{
  "taskName": "数据导出",
  "taskType": "LOG",
  "executeTime": "2025-11-14T20:00:00",
  "executionTimeout": 60,
  "maxRetryCount": 2,
  "taskData": {
    "exportType": "excel"
  }
}
```

### 超时处理流程

1. 任务开始执行
2. 启动超时监控（使用 `Future.get(timeout, TimeUnit.SECONDS)`）
3. 如果超时：
   - 尝试取消任务执行
   - 增加重试次数
   - 标记状态为 `TIMEOUT`（如果超过最大重试次数）
   - 记录错误信息
4. 如果未超过最大重试次数，重新调度执行

### 推荐超时时间

| 任务类型 | 推荐超时（秒） | 说明 |
|---------|--------------|------|
| 日志打印 | 10 | 快速执行 |
| 邮件发送 | 30 | 网络IO |
| 数据同步 | 300 | 批量操作 |
| 报表生成 | 600 | 复杂计算 |
| 数据导出 | 1800 | 大数据量 |

### 最佳实践

✅ **推荐**
```json
{
  "executionTimeout": 60,
  "maxRetryCount": 3
}
```

❌ **不推荐**
```json
{
  "executionTimeout": 0,  // 0 或负数会使用默认值
  "maxRetryCount": 0      // 超时后不重试
}
```

---

## 优先级调度

### 功能说明

- 优先级范围：0-10（数值越大优先级越高）
- 默认优先级：5
- Quartz 调度器原生支持优先级
- Simple 调度器通过 ScheduledThreadPoolExecutor 保证执行顺序

### 优先级等级建议

| 优先级 | 级别 | 使用场景 |
|-------|------|---------|
| 10 | 紧急 | 系统关键任务、告警通知 |
| 8-9 | 高 | 实时数据同步、支付回调 |
| 5-7 | 中 | 常规业务任务、报表生成 |
| 2-4 | 低 | 日志清理、数据归档 |
| 0-1 | 最低 | 非紧急批量任务 |

### 使用示例

#### 高优先级任务
```json
{
  "taskName": "支付回调",
  "taskType": "WEBHOOK",
  "priority": 9,
  "executeTime": "2025-11-14T20:00:00",
  "taskData": {
    "orderId": "12345",
    "url": "https://api.example.com/callback"
  }
}
```

#### 普通任务
```json
{
  "taskName": "日志清理",
  "taskType": "LOG",
  "priority": 3,
  "cronExpression": "0 0 2 * * ?",
  "taskData": {
    "cleanDays": 30
  }
}
```

### 调度行为

#### Quartz 调度器
- 同一时刻触发多个任务时，按优先级排序执行
- 高优先级任务会抢占低优先级任务的执行机会

#### Simple 调度器
- 任务按执行时间入队
- 相同执行时间的任务按创建顺序执行
- 优先级主要影响并发执行时的资源分配

---

## 任务暂停/恢复

### 功能说明

- 暂停：将 `PENDING` 状态任务标记为 `PAUSED`，取消调度
- 恢复：将 `PAUSED` 状态任务恢复为 `PENDING`，重新调度
- 手动重试：立即将任务重新调度执行

### API 接口

#### 1. 暂停任务
```bash
PUT http://localhost:8080/api/tasks/{id}/pause
```

**前置条件**：任务状态必须为 `PENDING`

**响应示例**：
```json
{
  "code": 200,
  "message": "任务已暂停",
  "data": true
}
```

#### 2. 恢复任务
```bash
PUT http://localhost:8080/api/tasks/{id}/resume
```

**前置条件**：任务状态必须为 `PAUSED`

**响应示例**：
```json
{
  "code": 200,
  "message": "任务已恢复",
  "data": true
}
```

#### 3. 手动重试
```bash
POST http://localhost:8080/api/tasks/{id}/retry
```

**功能**：将任务执行时间设置为当前时间，立即重新调度

**响应示例**：
```json
{
  "code": 200,
  "message": "任务已设置为立即重试",
  "data": true
}
```

### 使用场景

#### 场景1：临时维护
```bash
# 系统维护前，暂停所有待执行任务
GET /api/tasks?status=PENDING  # 获取任务列表
PUT /api/tasks/1/pause
PUT /api/tasks/2/pause
...

# 维护完成后，恢复任务
PUT /api/tasks/1/resume
PUT /api/tasks/2/resume
```

#### 场景2：手动干预
```bash
# 发现某个任务配置错误，暂停后修复
PUT /api/tasks/123/pause
# ... 修复任务数据 ...
PUT /api/tasks/123/resume
```

#### 场景3：故障重试
```bash
# 任务失败后，无需等待自动重试，立即手动重试
POST /api/tasks/456/retry
```

### 状态流转图

```
PENDING ───pause──→ PAUSED
   ↑                   │
   └─────resume────────┘

FAILED/TIMEOUT ─retry─→ PENDING (executeTime = now)
```

---

## 统计报表

### 功能说明

提供多维度任务统计分析，帮助监控系统运行状态。

### API 列表

#### 1. 总体统计
```bash
GET http://localhost:8080/api/tasks/statistics/summary
```

**响应示例**：
```json
{
  "code": 200,
  "data": {
    "pendingCount": 15,
    "executingCount": 2,
    "successCount": 1023,
    "failedCount": 45,
    "cancelledCount": 8,
    "pausedCount": 3,
    "timeoutCount": 12,
    "totalCount": 1108,
    "successRate": 95.32,
    "avgExecutionDuration": 1245.67,
    "maxExecutionDuration": 8900,
    "minExecutionDuration": 120
  }
}
```

**字段说明**：
- `successRate`：成功率（百分比）
- `avgExecutionDuration`：平均执行时长（毫秒）
- `maxExecutionDuration`：最长执行时长（毫秒）
- `minExecutionDuration`：最短执行时长（毫秒）

#### 2. 每日统计
```bash
GET http://localhost:8080/api/tasks/statistics/daily?days=7
```

**参数**：
- `days`：统计天数（1-90，默认7）

**响应示例**：
```json
{
  "code": 200,
  "data": [
    {
      "date": "2025-11-08",
      "executedCount": 142,
      "successCount": 135,
      "failedCount": 5,
      "timeoutCount": 2,
      "successRate": 95.07
    },
    {
      "date": "2025-11-09",
      "executedCount": 156,
      "successCount": 150,
      "failedCount": 4,
      "timeoutCount": 2,
      "successRate": 96.15
    }
  ]
}
```

#### 3. 任务类型分布
```bash
GET http://localhost:8080/api/tasks/statistics/type-distribution
```

**响应示例**：
```json
{
  "code": 200,
  "data": [
    {
      "taskType": "LOG",
      "count": 450,
      "percentage": 45.0
    },
    {
      "taskType": "EMAIL",
      "count": 320,
      "percentage": 32.0
    },
    {
      "taskType": "WEBHOOK",
      "count": 230,
      "percentage": 23.0
    }
  ]
}
```

#### 4. 任务状态分布
```bash
GET http://localhost:8080/api/tasks/statistics/status-distribution
```

**响应示例**：
```json
{
  "code": 200,
  "data": {
    "PENDING": 15,
    "EXECUTING": 2,
    "SUCCESS": 1023,
    "FAILED": 45,
    "CANCELLED": 8,
    "PAUSED": 3,
    "TIMEOUT": 12
  }
}
```

#### 5. 任务模式分布
```bash
GET http://localhost:8080/api/tasks/statistics/mode-distribution
```

**响应示例**：
```json
{
  "code": 200,
  "data": {
    "ONCE": 856,
    "CRON": 252
  }
}
```

### 监控告警建议

基于统计数据设置告警规则：

| 指标 | 阈值 | 告警级别 | 说明 |
|-----|------|---------|------|
| 成功率 | < 90% | 警告 | 任务失败率过高 |
| 成功率 | < 80% | 严重 | 系统异常 |
| 待执行任务数 | > 100 | 警告 | 任务堆积 |
| 待执行任务数 | > 500 | 严重 | 严重堆积 |
| 执行中任务数 | == 线程池大小 | 警告 | 线程池满载 |
| 超时任务数 | > 10/小时 | 警告 | 任务执行慢 |

---

## 最佳实践

### 1. 合理设置超时时间

```java
// ✅ 推荐：根据任务特点设置合理超时
{
  "taskType": "LOG",
  "executionTimeout": 10  // 快速任务
}

{
  "taskType": "EMAIL",
  "executionTimeout": 30  // 网络IO任务
}

{
  "taskType": "REPORT",
  "executionTimeout": 300  // 复杂计算任务
}

// ❌ 避免：所有任务使用相同超时
{
  "executionTimeout": 300  // 对快速任务浪费资源
}
```

### 2. 优先级分级管理

```java
// ✅ 推荐：按业务重要性设置优先级
public enum TaskPriority {
    CRITICAL(10),    // 系统关键任务
    HIGH(8),         // 重要业务
    NORMAL(5),       // 常规任务
    LOW(3),          // 非紧急任务
    BACKGROUND(1);   // 后台任务
}

// ❌ 避免：所有任务相同优先级或随意设置
```

### 3. 优雅降级

```java
// ✅ 推荐：监控任务堆积，必要时暂停低优先级任务
if (pendingCount > 100) {
    // 暂停优先级 < 5 的任务
    List<ScheduledTask> lowPriorityTasks = 
        taskRepository.findByStatusAndPriorityLessThan(PENDING, 5);
    lowPriorityTasks.forEach(task -> 
        taskManagementService.pauseTask(task.getId())
    );
}

// 系统恢复后，重新恢复任务
```

### 4. 定期清理历史数据

```sql
-- 建议：定期清理执行日志（保留最近90天）
DELETE FROM task_execution_log 
WHERE created_at < DATE_SUB(NOW(), INTERVAL 90 DAY);

-- 建议：归档已完成任务（保留最近30天）
INSERT INTO task_archive 
SELECT * FROM scheduled_task 
WHERE status IN ('SUCCESS', 'FAILED', 'CANCELLED') 
AND updated_at < DATE_SUB(NOW(), INTERVAL 30 DAY);

DELETE FROM scheduled_task 
WHERE status IN ('SUCCESS', 'FAILED', 'CANCELLED') 
AND updated_at < DATE_SUB(NOW(), INTERVAL 30 DAY);
```

### 5. 监控和告警

```java
// ✅ 推荐：定期检查任务健康状态
@Scheduled(cron = "0 */5 * * * ?")  // 每5分钟检查一次
public void checkTaskHealth() {
    TaskStatistics stats = taskStatisticsService.getOverallStatistics();
    
    // 成功率告警
    if (stats.getSuccessRate() < 90) {
        alertService.sendAlert("任务成功率过低：" + stats.getSuccessRate());
    }
    
    // 堆积告警
    if (stats.getPendingCount() > 100) {
        alertService.sendAlert("任务堆积：" + stats.getPendingCount());
    }
    
    // 超时告警
    if (stats.getTimeoutCount() > 10) {
        alertService.sendAlert("超时任务过多：" + stats.getTimeoutCount());
    }
}
```

### 6. 任务幂等性设计

```java
// ✅ 推荐：设计幂等的任务执行器
@Component
public class IdempotentTaskExecutor implements TaskExecutor {
    
    @Override
    public void execute(ScheduledTask task) throws Exception {
        String idempotencyKey = task.getId() + "-" + task.getRetryCount();
        
        // 检查是否已执行
        if (executionCache.contains(idempotencyKey)) {
            log.info("任务已执行，跳过：{}", idempotencyKey);
            return;
        }
        
        try {
            // 执行任务
            doExecute(task);
            
            // 标记已执行
            executionCache.put(idempotencyKey, true);
        } catch (Exception e) {
            // 执行失败，移除标记，允许重试
            executionCache.remove(idempotencyKey);
            throw e;
        }
    }
}
```

---

## 问题排查

### 问题1：任务一直超时

**可能原因**：
- 超时时间设置过短
- 执行器逻辑耗时过长
- 数据库/网络延迟

**解决方案**：
1. 查看执行日志的平均耗时
2. 适当增加超时时间
3. 优化执行器逻辑
4. 检查外部依赖响应时间

### 问题2：高优先级任务不生效

**可能原因**：
- 使用了 Simple 调度器（优先级支持有限）
- 优先级设置超出范围

**解决方案**：
1. 切换到 Quartz 调度器
2. 确保优先级在 0-10 范围内
3. 查看调度器日志确认优先级设置

### 问题3：暂停任务无法恢复

**可能原因**：
- 任务状态不是 PAUSED
- 调度器已关闭

**解决方案**：
1. 检查任务当前状态
2. 确认调度器运行状态
3. 查看错误日志

---

## 性能优化建议

1. **合理配置线程池大小**
   ```yaml
   scheduled:
     task:
       core-pool-size: 20  # 根据CPU核心数调整
   ```

2. **使用数据库连接池**
   ```yaml
   spring:
     datasource:
       hikari:
         maximum-pool-size: 20
         minimum-idle: 10
   ```

3. **开启 Redis 分布式锁（集群环境）**
   ```yaml
   scheduled:
     task:
       lock-type: redis
   ```

4. **定期清理历史数据**
   - 设置定时任务清理90天前的执行日志
   - 归档已完成的任务

5. **监控关键指标**
   - 任务堆积数量
   - 执行成功率
   - 平均执行时长
   - 超时任务数量

---

完整文档请参考：
- [README.md](../README.md)
- [ARCHITECTURE.md](ARCHITECTURE.md)
- [SCHEDULER_GUIDE.md](SCHEDULER_GUIDE.md)
