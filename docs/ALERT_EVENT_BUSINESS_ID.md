# AlertSystemEvent 业务ID字段更新说明

## 概述

为了更好地支持事件驱动的报警依赖管理，`AlertSystemEvent` 及其子类已经添加了 `businessId` 和 `businessType` 字段。这使得事件可以明确标识其归属的业务数据，从而实现精确的依赖匹配和更新。

## 核心改动

### 1. AlertSystemEvent 基类修改

**新增字段**：
```java
/**
 * 业务数据ID（必填）
 * 标识此事件归属于哪个业务数据，如班次ID、钻孔ID等
 */
private final String businessId;

/**
 * 业务类型（必填）
 * 标识业务数据的类型，如：SHIFT、BOREHOLE、OPERATION等
 */
private final String businessType;

/**
 * 异常事件ID（可选）
 * 如果事件是由某个已存在的异常事件触发的，则填充此字段
 * 大部分独立触发的事件此字段为 null
 */
private final Long exceptionEventId;
```

**新构造函数**：
```java
// 推荐使用的构造函数
public AlertSystemEvent(Object source, Long exceptionEventId, String eventType, 
                       String businessId, String businessType);

// 兼容旧代码的构造函数（已标记为 @Deprecated）
@Deprecated
public AlertSystemEvent(Object source, Long exceptionEventId, String eventType);
```

### 2. AlertDependencyManager 逻辑优化

#### 原逻辑问题：
- 假设所有事件都有 `exceptionEventId`
- 事件会更新所有活跃的异常事件，无法区分业务归属

#### 新逻辑：
- **情况1**：如果事件指定了 `exceptionEventId`
  - 只更新该特定异常事件的 `detection_context`
  - 只检查该异常事件的待机升级

- **情况2**：如果事件只有 `businessId`（常见场景）
  - 查询所有归属于该 `businessId` 的活跃异常事件
  - 更新这些异常事件的 `detection_context`
  - 检查这些异常事件的待机升级

这样确保了：**只有归属于同一业务的报警事件才会被此事件影响**。

## 使用场景

### 场景1：班次相关的业务事件

当班次开始、结束、或发生某个业务操作时，需要触发事件：

```java
// 假设班次开始了
String shiftId = "SHIFT_20251212_001";
String shiftType = "SHIFT";

// 发布班次开始事件
AlertSystemEvent event = new CustomShiftStartEvent(
    this,                          // source
    null,                          // exceptionEventId - 独立事件，没有关联的异常ID
    "SHIFT_START",                 // eventType
    shiftId,                       // businessId
    shiftType                      // businessType
);
applicationEventPublisher.publishEvent(event);
```

**效果**：
- 系统会查询所有 `businessId = "SHIFT_20251212_001"` 且 `businessType = "SHIFT"` 的活跃异常事件
- 更新这些异常事件的 `detection_context`，记录 `SHIFT_START_time`
- 检查这些异常事件的待机升级，如果依赖 `SHIFT_START` 的条件满足，触发升级

### 场景2：钻孔开始事件

```java
// 第一个钻孔开始了
String shiftId = "SHIFT_20251212_001";
String shiftType = "SHIFT";

// 发布钻孔开始事件
AlertSystemEvent event = new CustomBoreholeStartEvent(
    this,
    null,                          // 没有关联的异常ID
    "FIRST_BOREHOLE_START",       // 事件类型
    shiftId,                       // 归属于班次
    shiftType
);
applicationEventPublisher.publishEvent(event);
```

**效果**：
- 只更新归属于班次 `SHIFT_20251212_001` 的异常事件
- 如果该班次有"入井记录不足"的报警，且依赖 `FIRST_BOREHOLE_START` 事件
- 则会触发该报警的下一级升级

### 场景3：特定异常事件的内部事件

如果事件是由某个已知的异常事件内部触发的：

```java
// 针对特定异常事件的内部状态变更
Long exceptionEventId = 123L;
ExceptionEvent event = exceptionEventRepository.selectById(exceptionEventId);

AlertSystemEvent statusEvent = new CustomStatusChangeEvent(
    this,
    exceptionEventId,              // 指定异常ID
    "STATUS_CHANGED",
    event.getBusinessId(),         // 继承业务ID
    event.getBusinessType()
);
applicationEventPublisher.publishEvent(statusEvent);
```

## 实现自定义事件

### 方式1：直接继承 AlertSystemEvent（简单场景）

```java
package com.example.scheduled.alert.event;

import lombok.Getter;

@Getter
public class ShiftStartEvent extends AlertSystemEvent {
    
    private final String shiftName;
    private final LocalDateTime startTime;
    
    public ShiftStartEvent(
            Object source,
            String businessId,
            String businessType,
            String shiftName,
            LocalDateTime startTime) {
        super(source, null, "SHIFT_START", businessId, businessType);
        this.shiftName = shiftName;
        this.startTime = startTime;
    }
}
```

### 方式2：创建通用事件发布工具类

```java
@Service
@RequiredArgsConstructor
public class AlertEventPublisher {
    
    private final ApplicationEventPublisher eventPublisher;
    
    /**
     * 发布业务事件
     * 
     * @param eventType 事件类型（如：SHIFT_START、FIRST_BOREHOLE_START）
     * @param businessId 业务数据ID
     * @param businessType 业务类型（如：SHIFT、BOREHOLE）
     */
    public void publishBusinessEvent(String eventType, String businessId, String businessType) {
        AlertSystemEvent event = new GenericAlertEvent(
            this,
            null,           // 没有关联的异常ID
            eventType,
            businessId,
            businessType
        );
        eventPublisher.publishEvent(event);
        log.info("已发布业务事件: eventType={}, businessId={}, businessType={}", 
            eventType, businessId, businessType);
    }
    
    /**
     * 发布针对特定异常事件的事件
     */
    public void publishExceptionEvent(Long exceptionEventId, String eventType, 
                                     String businessId, String businessType) {
        AlertSystemEvent event = new GenericAlertEvent(
            this,
            exceptionEventId,
            eventType,
            businessId,
            businessType
        );
        eventPublisher.publishEvent(event);
    }
}

// 通用事件类
class GenericAlertEvent extends AlertSystemEvent {
    public GenericAlertEvent(Object source, Long exceptionEventId, String eventType,
                           String businessId, String businessType) {
        super(source, exceptionEventId, eventType, businessId, businessType);
    }
}
```

## 业务系统集成示例

### 示例1：班次管理系统

```java
@Service
@RequiredArgsConstructor
public class ShiftService {
    
    private final AlertEventPublisher alertEventPublisher;
    
    /**
     * 开始班次
     */
    public void startShift(String shiftId) {
        // ... 业务逻辑
        
        // 发布班次开始事件，触发相关报警检查
        alertEventPublisher.publishBusinessEvent(
            "SHIFT_START",
            shiftId,
            "SHIFT"
        );
    }
    
    /**
     * 结束班次
     */
    public void endShift(String shiftId) {
        // ... 业务逻辑
        
        // 发布班次结束事件
        alertEventPublisher.publishBusinessEvent(
            "SHIFT_END",
            shiftId,
            "SHIFT"
        );
    }
}
```

### 示例2：钻孔系统

```java
@Service
@RequiredArgsConstructor
public class BoreholeService {
    
    private final AlertEventPublisher alertEventPublisher;
    
    /**
     * 开始钻孔作业
     */
    public void startBorehole(String shiftId, String boreholeId) {
        // ... 业务逻辑
        
        // 检查是否是该班次的第一个钻孔
        boolean isFirstBorehole = checkIfFirstBorehole(shiftId);
        
        if (isFirstBorehole) {
            // 发布"第一个钻孔开始"事件，归属于班次
            alertEventPublisher.publishBusinessEvent(
                "FIRST_BOREHOLE_START",
                shiftId,      // 注意：这里用班次ID，因为报警是针对班次的
                "SHIFT"
            );
        }
        
        // 也可以发布钻孔自己的事件
        alertEventPublisher.publishBusinessEvent(
            "BOREHOLE_START",
            boreholeId,
            "BOREHOLE"
        );
    }
}
```

## 报警配置示例

假设配置了"入井记录不足"的三级报警：

### LEVEL_1（蓝色）- 固定时间触发
```json
{
  "exceptionTypeId": 1,
  "level": "LEVEL_1",
  "triggerCondition": {
    "type": "ABSOLUTE",
    "time": "16:00"
  },
  "dependentEvents": null
}
```

### LEVEL_2（黄色）- 依赖"第一个钻孔开始"事件
```json
{
  "exceptionTypeId": 1,
  "level": "LEVEL_2",
  "triggerCondition": {
    "type": "RELATIVE",
    "eventType": "FIRST_BOREHOLE_START",
    "offsetMinutes": 120
  },
  "dependentEvents": {
    "logicalOperator": "AND",
    "events": [
      {
        "eventType": "FIRST_BOREHOLE_START",
        "delayMinutes": 120,
        "required": true
      }
    ]
  }
}
```

### 工作流程

1. **14:00** - 系统检测到班次 `SHIFT_20251212_001` 入井记录不足
   ```java
   ExceptionEvent event = ExceptionEvent.builder()
       .exceptionTypeId(1L)
       .businessId("SHIFT_20251212_001")
       .businessType("SHIFT")
       .detectedAt(LocalDateTime.now())
       .status("ACTIVE")
       .build();
   ```

2. **16:00** - LEVEL_1 报警触发（固定时间）
   - 发送日志报警
   - 创建 LEVEL_2 的待机任务（等待 `FIRST_BOREHOLE_START` 事件）

3. **18:00** - 第一个钻孔开始
   ```java
   alertEventPublisher.publishBusinessEvent(
       "FIRST_BOREHOLE_START",
       "SHIFT_20251212_001",  // 归属于该班次
       "SHIFT"
   );
   ```
   
4. **事件处理**
   - `AlertDependencyManager` 收到事件
   - 查询 `businessId = "SHIFT_20251212_001"` 的所有活跃异常
   - 找到上述"入井记录不足"异常
   - 更新其 `detection_context`，记录 `FIRST_BOREHOLE_START_time = 18:00`
   - 检查 LEVEL_2 的依赖条件
   - 计算触发时间：18:00 + 120分钟 = 20:00
   - 调度 LEVEL_2 评估任务在 20:00 执行

5. **20:00** - LEVEL_2 报警触发
   - 发送邮件报警
   - 创建 LEVEL_3 的待机任务

## 关键优势

### 1. 精确匹配
只有归属于相同 `businessId` 的报警事件才会被更新，避免了误更新。

### 2. 业务隔离
不同业务数据（不同班次、不同钻孔）的报警相互独立，互不干扰。

### 3. 灵活扩展
可以轻松添加新的事件类型，只需定义 `eventType` 和在规则中配置依赖即可。

### 4. 向后兼容
保留了旧的构造函数（标记为 @Deprecated），现有代码仍能工作。

## 注意事项

1. **必须指定 businessId**：新发布的事件应该始终包含 `businessId` 和 `businessType`
2. **事件类型命名**：建议使用大写下划线格式，如 `SHIFT_START`、`FIRST_BOREHOLE_START`
3. **业务类型枚举**：建议在代码中定义常量，保持一致性
   ```java
   public class BusinessTypes {
       public static final String SHIFT = "SHIFT";
       public static final String BOREHOLE = "BOREHOLE";
       public static final String OPERATION = "OPERATION";
   }
   ```

## 迁移指南

### 对于现有事件发布代码

**旧代码**：
```java
AlertSystemEvent event = new CustomEvent(this, exceptionEventId, "EVENT_TYPE");
```

**新代码**：
```java
AlertSystemEvent event = new CustomEvent(
    this, 
    null,              // 大部分情况下为 null
    "EVENT_TYPE",
    businessId,        // 必须提供
    businessType       // 必须提供
);
```

### 对于自定义事件类

需要更新构造函数，添加 `businessId` 和 `businessType` 参数，并传递给父类。

参考 [AlertRecoveredEvent.java](../src/main/java/com/example/scheduled/alert/event/AlertRecoveredEvent.java) 和 [AlertResolutionEvent.java](../src/main/java/com/example/scheduled/alert/event/AlertResolutionEvent.java) 的实现。

## 相关文件

- [AlertSystemEvent.java](../src/main/java/com/example/scheduled/alert/event/AlertSystemEvent.java) - 基类
- [AlertRecoveredEvent.java](../src/main/java/com/example/scheduled/alert/event/AlertRecoveredEvent.java) - 示例子类
- [AlertResolutionEvent.java](../src/main/java/com/example/scheduled/alert/event/AlertResolutionEvent.java) - 示例子类
- [AlertDependencyManager.java](../src/main/java/com/example/scheduled/alert/service/AlertDependencyManager.java) - 事件处理逻辑
- [ExceptionEvent.java](../src/main/java/com/example/scheduled/alert/entity/ExceptionEvent.java) - 异常事件实体

## 总结

通过添加 `businessId` 字段，事件驱动的报警系统现在能够：

✅ **精确定位** - 事件只影响归属于相同业务的报警  
✅ **业务隔离** - 不同业务数据的报警互不干扰  
✅ **灵活扩展** - 轻松添加新的业务事件类型  
✅ **清晰追踪** - 明确知道每个事件和报警的业务归属  
✅ **向后兼容** - 保留旧接口，平滑迁移  

这使得报警系统能够更好地与业务系统集成，提供精确、可靠的报警服务。
