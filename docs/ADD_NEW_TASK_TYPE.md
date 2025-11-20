# 添加新任务类型指南

## 概述

本系统使用基于注解的方式来管理任务类型元信息。通过在任务执行器上添加 `@TaskExecutorInfo` 注解，系统会自动扫描并收集这些信息，暴露给前端使用。

## 步骤

### 1. 在 `ScheduledTask.TaskType` 枚举中添加新类型

编辑 `src/main/java/com/example/scheduled/entity/ScheduledTask.java`：

```java
public enum TaskType {
    LOG,        // 日志打印
    EMAIL,      // 邮件通知
    SMS,        // 短信通知
    WEBHOOK,    // HTTP回调
    MQ,         // 消息队列
    PLAN,       // 计划任务
    NEW_TYPE    // 你的新类型
}
```

### 2. 创建任务执行器并添加注解

创建 `src/main/java/com/example/scheduled/executor/impl/NewTypeTaskExecutor.java`：

```java
package com.example.scheduled.executor.impl;

import com.example.scheduled.annotation.TaskExecutorInfo;
import com.example.scheduled.entity.ScheduledTask;
import com.example.scheduled.executor.TaskExecutor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@TaskExecutorInfo(
    taskType = ScheduledTask.TaskType.NEW_TYPE,  // 枚举值
    displayName = "新类型",                       // 前端显示名称（中文）
    description = "这是一个新的任务类型示例"       // 任务描述
)
public class NewTypeTaskExecutor implements TaskExecutor {

    @Override
    public boolean support(ScheduledTask.TaskType taskType) {
        return taskType == ScheduledTask.TaskType.NEW_TYPE;
    }

    @Override
    public void execute(ScheduledTask task) throws Exception {
        log.info("执行新类型任务：{}", task.getTaskName());
        // 你的业务逻辑
    }

    @Override
    public String getName() {
        return "新类型执行器";
    }
}
```

### 3. 重启应用

系统会在启动时自动扫描所有带 `@TaskExecutorInfo` 注解的执行器，并注册到任务类型列表中。

### 4. 验证

调用 API 接口查看新类型是否已注册：

```bash
curl http://localhost:18083/api/tasks/types
```

返回示例：

```json
{
  "code": 200,
  "message": "success",
  "data": [
    {
      "code": "NEW_TYPE",
      "name": "新类型",
      "description": "这是一个新的任务类型示例"
    },
    ...
  ]
}
```

## 注解说明

### @TaskExecutorInfo

| 属性 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `taskType` | `TaskType` | 是 | 枚举值，指定此执行器支持的任务类型 |
| `displayName` | `String` | 是 | 前端显示的名称（支持中文） |
| `description` | `String` | 否 | 任务类型的详细描述 |
| `priority` | `int` | 否 | 优先级（当同一类型有多个执行器时使用），默认 0 |

## 优势

1. **松耦合**：新增任务类型无需修改控制器代码
2. **自动发现**：Spring 自动扫描并注册所有标注注解的执行器
3. **易于维护**：任务类型的元信息与执行器代码放在一起
4. **类型安全**：编译期检查，避免拼写错误
5. **国际化友好**：可轻松扩展支持多语言

## 工作原理

1. `TaskTypeInfoService` 在 `@PostConstruct` 阶段扫描所有 `TaskExecutor` Bean
2. 提取每个执行器上的 `@TaskExecutorInfo` 注解信息
3. 将信息缓存到 `Map<TaskType, TaskTypeInfo>` 中
4. `TaskController` 的 `/api/tasks/types` 接口返回缓存的信息
5. 前端调用此接口获取任务类型列表用于下拉选择等场景

## 相关文件

- **注解定义**：`src/main/java/com/example/scheduled/annotation/TaskExecutorInfo.java`
- **DTO**：`src/main/java/com/example/scheduled/dto/TaskTypeInfo.java`
- **服务**：`src/main/java/com/example/scheduled/service/TaskTypeInfoService.java`
- **控制器**：`src/main/java/com/example/scheduled/controller/TaskController.java`
- **执行器示例**：`src/main/java/com/example/scheduled/executor/impl/*TaskExecutor.java`
