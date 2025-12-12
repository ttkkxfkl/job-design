# Alert Package Clean Code 重构总结

## 概述

本次重构针对 `alert` 包进行了全面的代码优化，主要包括：
1. **消灭魔数**：提取所有硬编码字符串和数字到统一的常量类
2. **代码格式化**：统一代码风格，优化导入语句
3. **提高可维护性**：集中管理常量，减少重复代码

## 重构内容

### 1. 创建常量管理类

**文件**: `AlertConstants.java`

创建了统一的常量管理类，包含以下嵌套类：

#### 1.1 ExceptionEventStatus（异常事件状态）
- `ACTIVE` - 活跃状态
- `RESOLVING` - 解除中
- `RESOLVED` - 已解除

#### 1.2 AlertLevels（报警等级）
- `NONE` - 无报警
- `LEVEL_1`, `LEVEL_2`, `LEVEL_3` - 数字等级
- `BLUE`, `YELLOW`, `RED` - 颜色等级
- `getPriority(String level)` - 获取等级优先级方法

#### 1.3 PendingEscalationStatus（待机升级状态）
- `WAITING` - 等待中
- `READY` - 就绪
- `SCHEDULED` - 已调度
- `COMPLETED` - 已完成

#### 1.4 AlertEventType（报警事件类型）
- `ALERT_TRIGGERED` - 报警触发
- `ALERT_ESCALATED` - 报警升级
- `ALERT_RESOLVED` - 报警解除
- `TASK_CANCELLED` - 任务取消
- `SYSTEM_RECOVERY` - 系统恢复
- `ALERT_RECOVERED` - 报警恢复

#### 1.5 BusinessType（业务类型）
- `SHIFT` - 班次
- `BOREHOLE` - 钻孔
- `OPERATION` - 作业
- `EQUIPMENT` - 设备
- `WORKER` - 工人
- `PROJECT` - 项目

#### 1.6 TriggerType（触发条件类型）
- `ABSOLUTE` - 绝对条件
- `RELATIVE` - 相对条件
- `HYBRID` - 混合条件

#### 1.7 LogicalOperator（逻辑操作符）
- `AND` - 与操作
- `OR` - 或操作

#### 1.8 ActionStatus（动作状态）
- `PENDING` - 待执行
- `SENT` - 已发送
- `FAILED` - 失败
- `COMPLETED` - 完成

#### 1.9 JsonFields（JSON字段名）
- `STATUS` - 状态字段
- `DEPENDENCIES` - 依赖字段
- `LOGICAL_OPERATOR` - 逻辑操作符字段
- `CREATED_AT` - 创建时间字段
- `SCHEDULED_AT` - 调度时间字段
- `TASK_ID` - 任务ID字段

#### 1.10 TimeFieldSuffix（时间字段后缀）
- `TIME` - 时间后缀（_time）

#### 1.11 Defaults（默认值）
- `DEFAULT_MAX_RETRY_COUNT = 1` - 默认最大重试次数
- `DEFAULT_EXECUTION_TIMEOUT = 30L` - 默认执行超时时间（秒）

### 2. 重构的文件列表

#### 2.1 Service层
- **AlertDependencyManager.java**
  - 替换所有JSON字段名为常量引用
  - 替换状态值为常量引用
  - 添加静态导入以简化代码

- **AlertEscalationService.java**
  - 替换默认值为 `DEFAULT_MAX_RETRY_COUNT`, `DEFAULT_EXECUTION_TIMEOUT`
  - 使用 `AlertLevels.getPriority()` 方法
  - 替换 `actionStatus` 为 `SENT` 常量

- **AlertRecoveryService.java**
  - 替换 `"WAITING"` 为 `WAITING` 常量
  - 更新 `AlertRecoveredEvent` 构造函数调用，添加 businessId 支持

- **AlertResolutionService.java**
  - 替换 `"ALERT_RESOLVED"` 为 `ALERT_RESOLVED` 常量
  - 替换 `"TASK_CANCELLED"` 为 `TASK_CANCELLED` 常量
  - 替换 `"COMPLETED"` 为 `COMPLETED` 常量
  - 更新 `AlertResolutionEvent` 构造函数调用

#### 2.2 Executor层
- **AlertExecutor.java**
  - 替换 `"ACTIVE"` 为 `ACTIVE` 常量
  - 替换 `"ALERT_TRIGGERED"` 为 `ALERT_TRIGGERED` 常量

- **LogAlertAction.java**
  - 重构 switch 语句，使用等级常量（BLUE, YELLOW, RED, LEVEL_1, LEVEL_2, LEVEL_3）

#### 2.3 Controller层
- **AlertRuleController.java**
  - 使用 `ExceptionEventStatus.ACTIVE` 替代字符串 "ACTIVE"
  - 使用 `AlertLevels.NONE` 替代字符串 "NONE"

#### 2.4 Entity层
- **AlertRule.java**
  - `getLevelPriority()` 方法委托给 `AlertConstants.AlertLevels.getPriority()`

#### 2.5 Event层
- **AlertRecoveredEvent.java**
  - 替换 `"ALERT_RECOVERED"` 为 `ALERT_RECOVERED` 常量
  - 更新构造函数以支持 businessId

- **AlertResolutionEvent.java**
  - 替换 `"ALERT_RESOLVED"` 为 `ALERT_RESOLVED` 常量
  - 更新构造函数以支持 businessId

## 改进效果

### 1. 可维护性提升
- **集中管理**: 所有魔数和字符串常量集中在 `AlertConstants` 中
- **易于修改**: 修改常量值只需更新一处
- **类型安全**: 使用常量而非字符串，减少拼写错误

### 2. 代码可读性提升
- **语义化**: `WAITING` 比 `"WAITING"` 更清晰
- **分组管理**: 相关常量归类到嵌套类中
- **静态导入**: 简化代码，提高可读性

### 3. 编译时检查
- **类型检查**: 编译器可以检查常量引用是否正确
- **自动补全**: IDE 可以提供更好的自动补全支持
- **重构支持**: IDE 可以安全地重命名常量

## 示例对比

### 修改前
```java
if ("ACTIVE".equals(event.getStatus())) {
    levelData.put("status", "WAITING");
    if ("AND".equals(logicalOperator)) {
        // ...
    }
}
```

### 修改后
```java
import static com.example.scheduled.alert.constant.AlertConstants.ExceptionEventStatus.ACTIVE;
import static com.example.scheduled.alert.constant.AlertConstants.PendingEscalationStatus.WAITING;
import static com.example.scheduled.alert.constant.AlertConstants.LogicalOperator.AND;

if (ACTIVE.equals(event.getStatus())) {
    levelData.put(STATUS, WAITING);
    if (AND.equals(logicalOperator)) {
        // ...
    }
}
```

## 注意事项

1. **静态导入的使用**
   - 使用静态导入可以简化代码
   - 但要注意避免导入过多导致命名冲突
   - 建议导入具体的常量而非使用通配符 `.*`

2. **常量的组织**
   - 相关常量归类到同一个嵌套类
   - 使用私有构造函数防止实例化
   - 添加清晰的 Javadoc 注释

3. **向后兼容**
   - Event 类保留了旧的构造函数并标记为 `@Deprecated`
   - 逐步迁移到新的构造函数

## 后续优化建议

1. **枚举替代字符串常量**
   - 考虑将部分字符串常量转换为枚举类型
   - 例如：`AlertLevel`, `BusinessType` 等

2. **配置文件外部化**
   - 将默认值移到配置文件
   - 支持运行时配置修改

3. **国际化支持**
   - 为常量添加国际化消息键
   - 支持多语言环境

4. **代码覆盖率**
   - 为常量类添加单元测试
   - 确保所有常量都被正确使用

## 相关文档

- [BUSINESS_ID_UPDATE.md](BUSINESS_ID_UPDATE.md) - businessId 功能更新
- [ALERT_EVENT_BUSINESS_ID.md](ALERT_EVENT_BUSINESS_ID.md) - 事件系统 businessId 支持
- [ALERT_SYSTEM_GUIDE.md](ALERT_SYSTEM_GUIDE.md) - 报警系统使用指南
