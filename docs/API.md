# 定时任务系统接口文档

面向前端开发的 RESTful API 接口规范。

---

## 通用约定

### Base URL
```
http://localhost:18083/api/tasks
```
生产环境请替换为实际域名。

### 鉴权
当前版本暂不需要鉴权；后续如集成登录态，将通过 `Authorization: Bearer <token>` 请求头传递。

### 统一响应格式
所有接口返回 JSON，结构如下：

```json
{
  "code": 200,           // 200 成功，其他为错误码
  "message": "success",  // 提示信息
  "data": {...}          // 业务数据，失败时可能为 null
}
```

### 枚举值定义

#### TaskType（任务类型）
| 枚举值 | 显示名称 | 说明 |
|--------|---------|------|
| `LOG` | 日志 | 记录一条日志内容 |
| `EMAIL` | 邮件 | 发送邮件通知 |
| `SMS` | 短信 | 发送短信通知 |
| `WEBHOOK` | 回调 | 调用外部 HTTP 接口 |
| `MQ` | 消息队列 | 向 MQ 发送消息（预留） |
| `PLAN` | 计划 | 执行预定义计划任务 |

**推荐**：通过 `GET /api/tasks/types` 动态获取类型列表及中文名。

#### ScheduleMode（调度模式）
| 枚举值 | 说明 |
|--------|------|
| `ONCE` | 一次性定时执行 |
| `CRON` | 周期性 Cron 调度 |

#### TaskStatus（任务状态）
| 枚举值 | 说明 |
|--------|------|
| `PENDING` | 待执行 |
| `EXECUTING` | 执行中 |
| `SUCCESS` | 成功 |
| `FAILED` | 失败 |
| `CANCELLED` | 已取消 |
| `PAUSED` | 已暂停 |
| `TIMEOUT` | 执行超时 |

---

## 1. 任务管理

### 1.1 创建一次性任务

**接口**：`POST /api/tasks/once`

**说明**：创建指定时间执行一次的任务（ONCE 模式）。

**请求体**：
```json
{
  "taskName": "发送晚报邮件",
  "taskType": "EMAIL",
  "executeTime": "2025-11-20 20:00:00",
  "taskData": {
    "recipient": "user@example.com",
    "subject": "每日晚报",
    "content": "今日要闻内容..."
  },
  "priority": 5,
  "executionTimeout": 300,
  "maxRetryCount": 3
}
```

**字段说明**：
| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `taskName` | string | 是 | 任务名称 |
| `taskType` | string | 是 | 任务类型枚举值（见上） |
| `executeTime` | string | 是 | 执行时间（`yyyy-MM-dd HH:mm:ss` 格式，必须是未来时间） |
| `taskData` | object | 否 | 任务数据（JSON，由执行器解析） |
| `priority` | int | 否 | 优先级 0-10，默认 5 |
| `executionTimeout` | long | 否 | 超时时间（秒），默认 300 |
| `maxRetryCount` | int | 否 | 最大重试次数，默认 3 |

**响应示例**（成功）：
```json
{
  "code": 200,
  "message": "一次性任务创建成功",
  "data": {
    "id": 1001,
    "taskName": "发送晚报邮件",
    "taskType": "EMAIL",
    "scheduleMode": "ONCE",
    "executeTime": "2025-11-20 20:00:00",
    "priority": 5,
    "executionTimeout": 300,
    "taskData": {...},
    "status": "PENDING",
    "retryCount": 0,
    "maxRetryCount": 3,
    "createdAt": "2025-11-20 12:34:56",
    "updatedAt": "2025-11-20 12:34:56"
  }
}
```

**错误示例**：
```json
{
  "code": 400,
  "message": "执行时间必须是将来的时间",
  "data": null
}
```

---

### 1.2 创建 Cron 周期任务

**接口**：`POST /api/tasks/cron`

**说明**：创建按 Cron 表达式周期执行的任务（CRON 模式）。

**请求体**：
```json
{
  "taskName": "每日备份任务",
  "taskType": "PLAN",
  "cronExpression": "0 0 2 * * ?",
  "taskData": {
    "backupPath": "/data/backup"
  },
  "priority": 8,
  "executionTimeout": 600
}
```

**字段说明**：
| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `taskName` | string | 是 | 任务名称 |
| `taskType` | string | 是 | 任务类型枚举值 |
| `cronExpression` | string | 是 | Cron 表达式（如 `0 0 2 * * ?`） |
| `taskData` | object | 否 | 任务数据 |
| `priority` | int | 否 | 优先级 0-10，默认 5 |
| `executionTimeout` | long | 否 | 超时时间（秒），默认 300 |

**响应示例**：
```json
{
  "code": 200,
  "message": "Cron 任务创建成功",
  "data": {
    "id": 1002,
    "taskName": "每日备份任务",
    "taskType": "PLAN",
    "scheduleMode": "CRON",
    "cronExpression": "0 0 2 * * ?",
    "priority": 8,
    "status": "PENDING",
    ...
  }
}
```

---

### 1.3 创建任务（兼容接口）

**接口**：`POST /api/tasks`

**说明**：根据是否包含 `cronExpression` 自动判断创建 ONCE 或 CRON 任务。

**请求体**：同 1.1 或 1.2，自动路由。

---

### 1.4 查询任务详情

**接口**：`GET /api/tasks/{id}`

**参数**：
- `id`（路径参数）：任务 ID

**响应示例**：
```json
{
  "code": 200,
  "message": "success",
  "data": {
    "id": 1001,
    "taskName": "发送晚报邮件",
    "taskType": "EMAIL",
    "scheduleMode": "ONCE",
    "executeTime": "2025-11-20 20:00:00",
    "status": "SUCCESS",
    "lastExecuteTime": "2025-11-20 20:00:02",
    "errorMessage": null,
    ...
  }
}
```

**错误**（任务不存在）：
```json
{
  "code": 404,
  "message": "任务不存在",
  "data": null
}
```

---

### 1.5 查询任务列表

**接口**：`GET /api/tasks`

**查询参数**：
- `status`（可选）：按状态筛选，如 `PENDING` / `SUCCESS` / `FAILED`

**示例**：
```
GET /api/tasks?status=PENDING
```

**响应示例**：
```json
{
  "code": 200,
  "message": "success",
  "data": [
    {
      "id": 1001,
      "taskName": "发送晚报邮件",
      "status": "PENDING",
      ...
    },
    {
      "id": 1002,
      "taskName": "每日备份任务",
      "status": "PENDING",
      ...
    }
  ]
}
```

---

### 1.6 取消任务

**接口**：`DELETE /api/tasks/{id}`

**说明**：取消指定任务，将任务状态改为 `CANCELLED` 并从调度器移除。

**响应示例**：
```json
{
  "code": 200,
  "message": "任务已取消",
  "data": true
}
```

**错误**：
```json
{
  "code": 500,
  "message": "任务取消失败或任务不存在",
  "data": null
}
```

---

### 1.7 暂停任务

**接口**：`PUT /api/tasks/{id}/pause`

**说明**：暂停任务，状态改为 `PAUSED`，可恢复。

**响应示例**：
```json
{
  "code": 200,
  "message": "任务已暂停",
  "data": true
}
```

---

### 1.8 恢复任务

**接口**：`PUT /api/tasks/{id}/resume`

**说明**：恢复已暂停的任务，状态回到 `PENDING`。

**响应示例**：
```json
{
  "code": 200,
  "message": "任务已恢复",
  "data": true
}
```

---

### 1.9 立即重试任务

**接口**：`POST /api/tasks/{id}/retry`

**说明**：手动触发任务立即重试（适用于失败/超时任务）。

**响应示例**：
```json
{
  "code": 200,
  "message": "任务已设置为立即重试",
  "data": true
}
```

---

### 1.10 查询任务执行日志

**接口**：`GET /api/tasks/{id}/logs`

**说明**：获取指定任务的执行历史记录。

**响应示例**：
```json
{
  "code": 200,
  "message": "success",
  "data": [
    {
      "id": 5001,
      "taskId": 1001,
      "executeTime": "2025-11-20 20:00:02",
      "status": "SUCCESS",
      "duration": 1234,
      "errorMessage": null,
      "createdAt": "2025-11-20 20:00:03"
    },
    {
      "id": 5002,
      "taskId": 1001,
      "executeTime": "2025-11-19 20:00:02",
      "status": "FAILED",
      "duration": 5678,
      "errorMessage": "连接超时",
      "createdAt": "2025-11-19 20:00:08"
    }
  ]
}
```

---

## 2. 元数据

### 2.1 获取任务类型列表

**接口**：`GET /api/tasks/types`

**说明**：返回所有已注册的任务类型及其显示名称、描述（从执行器注解动态收集）。

**响应示例**：
```json
{
  "code": 200,
  "message": "success",
  "data": [
    {
      "code": "EMAIL",
      "name": "邮件",
      "description": "发送邮件通知"
    },
    {
      "code": "LOG",
      "name": "日志",
      "description": "记录一条日志内容"
    },
    {
      "code": "PLAN",
      "name": "计划",
      "description": "执行预定义计划任务"
    },
    {
      "code": "SMS",
      "name": "短信",
      "description": "发送短信通知"
    },
    {
      "code": "WEBHOOK",
      "name": "回调",
      "description": "调用外部 HTTP 接口"
    }
  ]
}
```

**用途**：用于填充前端任务类型下拉框/选择器。

---

## 3. 统计与运行态

### 3.1 获取调度器状态

**接口**：`GET /api/tasks/scheduler/status`

**说明**：返回调度器运行状态、线程池、集群等信息。

**响应示例**：
```json
{
  "code": 200,
  "message": "success",
  "data": {
    "schedulerName": "QuartzTaskScheduler",
    "running": true,
    "threadPoolSize": 10,
    "activeThreads": 2,
    "clustered": true,
    "instanceId": "localhost1763613015745"
  }
}
```

---

### 3.2 统计待执行任务数量

**接口**：`GET /api/tasks/count/pending`

**说明**：返回状态为 `PENDING` 的任务总数。

**响应示例**：
```json
{
  "code": 200,
  "message": "success",
  "data": 42
}
```

---

### 3.3 获取任务总体统计

**接口**：`GET /api/tasks/statistics/summary`

**说明**：返回任务数量、成功率、失败率等汇总统计。

**响应示例**：
```json
{
  "code": 200,
  "message": "success",
  "data": {
    "totalTasks": 1200,
    "successCount": 1050,
    "failedCount": 120,
    "pendingCount": 30,
    "successRate": 87.5,
    "failedRate": 10.0
  }
}
```

---

### 3.4 获取每日任务统计

**接口**：`GET /api/tasks/statistics/daily`

**查询参数**：
- `days`（可选）：统计天数，默认 7，范围 1-90

**示例**：
```
GET /api/tasks/statistics/daily?days=7
```

**响应示例**：
```json
{
  "code": 200,
  "message": "success",
  "data": [
    {
      "date": "2025-11-20",
      "totalCount": 150,
      "successCount": 135,
      "failedCount": 15,
      "successRate": 90.0
    },
    {
      "date": "2025-11-19",
      "totalCount": 148,
      "successCount": 130,
      "failedCount": 18,
      "successRate": 87.8
    }
    // ... 最近 7 天
  ]
}
```

---

### 3.5 获取任务类型分布

**接口**：`GET /api/tasks/statistics/type-distribution`

**响应示例**：
```json
{
  "code": 200,
  "message": "success",
  "data": [
    {
      "taskType": "EMAIL",
      "count": 450
    },
    {
      "taskType": "LOG",
      "count": 320
    },
    {
      "taskType": "WEBHOOK",
      "count": 280
    },
    {
      "taskType": "PLAN",
      "count": 150
    }
  ]
}
```

---

### 3.6 获取任务模式分布

**接口**：`GET /api/tasks/statistics/mode-distribution`

**响应示例**：
```json
{
  "code": 200,
  "message": "success",
  "data": {
    "ONCE": 800,
    "CRON": 400
  }
}
```

---

### 3.7 获取任务状态分布

**接口**：`GET /api/tasks/statistics/status-distribution`

**响应示例**：
```json
{
  "code": 200,
  "message": "success",
  "data": {
    "PENDING": 30,
    "SUCCESS": 1050,
    "FAILED": 120,
    "CANCELLED": 15,
    "PAUSED": 5
  }
}
```

---

## 4. 错误码说明

| Code | 说明 |
|------|------|
| 200 | 成功 |
| 400 | 请求参数错误（如字段校验失败） |
| 404 | 资源不存在（如任务 ID 不存在） |
| 500 | 服务器内部错误 |

---

## 5. 使用示例（JavaScript）

### 创建一次性任务
```javascript
fetch('http://localhost:18083/api/tasks/once', {
  method: 'POST',
  headers: { 'Content-Type': 'application/json' },
  body: JSON.stringify({
    taskName: '发送晚报邮件',
    taskType: 'EMAIL',
    executeTime: '2025-11-20 20:00:00',
    taskData: {
      recipient: 'user@example.com',
      subject: '每日晚报',
      content: '今日要闻内容...'
    },
    priority: 5,
    executionTimeout: 300,
    maxRetryCount: 3
  })
})
  .then(res => res.json())
  .then(data => console.log(data));
```

### 获取任务类型列表
```javascript
fetch('http://localhost:18083/api/tasks/types')
  .then(res => res.json())
  .then(data => {
    // data.data 是类型数组
    const typeOptions = data.data.map(t => ({
      value: t.code,
      label: t.name,
      description: t.description
    }));
    console.log(typeOptions);
  });
```

### 查询任务列表（状态筛选）
```javascript
fetch('http://localhost:18083/api/tasks?status=PENDING')
  .then(res => res.json())
  .then(data => console.log(data.data)); // 任务数组
```

---

## 6. 注意事项

1. **时间格式**：所有时间字段使用 `yyyy-MM-dd HH:mm:ss` 格式（如 `2025-11-20 20:00:00`），服务器时区为系统默认（建议统一为 UTC+8）。
2. **Cron 表达式**：支持标准 Quartz Cron 语法（6 或 7 位），建议使用在线工具验证。
3. **任务数据**：`taskData` 为 JSON 对象，不同任务类型解析规则由各执行器实现，请参考执行器文档或联系后端。
4. **优先级**：0-10，数值越大优先级越高，同时到期时高优先级先执行。
5. **超时处理**：超时后任务会被标记为 `TIMEOUT`，可通过重试接口手动重试。
6. **分页**：当前版本暂未提供分页参数，大量任务场景下后续会补充 `page`/`size` 支持。

---

如有疑问或需要补充接口，请联系后端团队。
