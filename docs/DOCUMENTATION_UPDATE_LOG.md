# 文档更新日志

**更新日期**: 2025年12月12日  
**更新原因**: 架构演进 - 从原型设计到生产就绪实现

---

## 更新概述

经过代码实现和架构优化，原有的设计文档已过时。本次更新同步了文档与实际代码实现，主要涉及以下方面：

---

## 主要变更

### 1. 数据库架构简化

**变更前**: 
- 5个表：`exception_type`, `trigger_condition`, `alert_rule`, `exception_event`, `alert_event_log`
- `trigger_condition` 独立表存储触发条件

**变更后**:
- 4个核心表 + 1个统计表
- **移除** `trigger_condition` 独立表
- **修改** `alert_rule` 表，添加 `trigger_condition` JSON 字段
- **修改** `alert_rule` 表，添加 `dependent_events` JSON 字段（依赖事件配置）
- **修改** `exception_event` 表，添加 `pending_escalations` JSON 字段（存储任务ID）
- **新增** `daily_task_statistics` 表（每日统计）

**优势**:
- 减少表间关联，提升查询性能
- JSON 字段提供更灵活的配置能力
- 便于版本控制和配置复制

---

### 2. 服务层架构增强

**新增服务类**:

| 服务类 | 职责 | 关键功能 |
|--------|------|---------|
| `AlertResolutionService` | 异常解除管理 | - 检测业务条件恢复<br>- 取消所有待机任务<br>- 状态转换管理<br>- 记录解除日志 |
| `AlertDependencyManager` | 依赖事件管理 | - 处理业务事件通知<br>- 更新 pending_escalations 状态<br>- 重新调度依赖的评估任务<br>- 计算延迟触发时间 |
| `AlertRecoveryService` | 系统启动恢复 | - 清理 Quartz 旧任务<br>- 重新调度待机任务<br>- 处理 RESOLVING 状态<br>- 防止持久化冲突 |

**原有服务增强**:

| 服务类 | 新增功能 |
|--------|---------|
| `AlertEscalationService` | - 持久化任务ID到 pending_escalations<br>- updatePendingEscalationsWithTaskId() 方法<br>- 支持重启后任务追踪 |
| `AlertExecutor` | - 幂等性检查（防止重复执行）<br>- isLevelAlreadyTriggered() 检查<br>- 状态检查（跳过已解除事件）|

---

### 3. 事件驱动架构

**新增 Spring 事件系统** (`alert/event/`):

```
AlertSystemEvent (基类)
├── AlertTriggeredEvent      # 告警触发时发布
├── AlertResolvedEvent       # 异常解除时发布
├── AlertRecoveredEvent      # 系统恢复完成时发布
└── DependencyEventOccurred  # 依赖事件发生时发布
```

**优势**:
- 解耦业务逻辑
- 便于扩展（监听器模式）
- 支持跨模块通信
- 便于审计和监控

---

### 4. 枚举类型标准化

**新增枚举包** (`alert/enums/`):

| 枚举类 | 值 | 用途 |
|--------|---|------|
| `ExceptionStatus` | ACTIVE, RESOLVING, RESOLVED | 异常事件生命周期 |
| `AlertEventType` | ALERT_TRIGGERED, ALERT_RESOLVED, TASK_CANCELLED | 日志事件类型 |

**原因**:
- 类型安全
- 避免魔法字符串
- 便于状态机实现

---

### 5. 触发策略重构

**变更前**:
- 3个独立策略：AbsoluteTimeTrigger, RelativeEventTrigger, HybridTrigger
- 基于 trigger_condition 表的数据

**变更后**:
- 2个简化策略：TimeTrigger, ConditionTrigger
- 基于 alert_rule.trigger_condition JSON
- 支持更复杂的条件表达式

**示例配置**:
```json
{
  "relation": "AND",
  "items": [
    {
      "operator": ">",
      "source": "业务事件",
      "field": "探水计划首次开始时间",
      "offsetValue": 30,
      "offsetUnit": "分钟"
    }
  ]
}
```

---

### 6. 关键问题修复

#### 问题1: Quartz 持久化冲突

**症状**: 服务重启后任务重复执行

**解决方案**:
1. **任务ID持久化**: 将 taskId 存储到 `pending_escalations` JSON
2. **启动清理**: `AlertRecoveryService` 清理 Quartz 旧任务
3. **幂等性检查**: `AlertExecutor` 检查是否已触发

**相关文档**: [ALERT_RECOVERY_CONFLICT_SOLUTION.md](ALERT_RECOVERY_CONFLICT_SOLUTION.md)

#### 问题2: 等级命名不一致

**变更**: BLUE/YELLOW/RED → LEVEL_1/LEVEL_2/LEVEL_3

**原因**:
- 更通用，易扩展
- 避免颜色语义混淆
- 支持动态等级数量

---

## 已更新的文档

### ✅ 完全更新
- [ALERT_README.md](ALERT_README.md) - 核心概念、包结构、数据库表
- [ALERT_DB_SCHEMA.md](ALERT_DB_SCHEMA.md) - 完整数据库设计，含可视化表格
- [ALERT_DATAFLOW_DIAGRAM.md](ALERT_DATAFLOW_DIAGRAM.md) - 9个 Mermaid 数据流图
- [ALERT_RECOVERY_CONFLICT_SOLUTION.md](ALERT_RECOVERY_CONFLICT_SOLUTION.md) - 新增

### ⚠️ 部分过时（建议参考最新代码）
- [ALERT_GENERATION_COMPLETE.md](ALERT_GENERATION_COMPLETE.md) - 文件清单部分过时
- [ALERT_SUMMARY.md](ALERT_SUMMARY.md) - 实体列表需更新

### ℹ️ 仍然有效
- [ALERT_DETAILED_DATAFLOW.md](ALERT_DETAILED_DATAFLOW.md) - 详细执行流程
- [ALERT_INDEX.md](ALERT_INDEX.md) - 文档导航
- [ALERT_CHECKLIST.md](ALERT_CHECKLIST.md) - 部署检查清单

---

## 迁移指南

### 从旧架构迁移

如果你使用了早期版本的设计：

1. **数据库迁移**:
   ```sql
   -- 将 trigger_condition 数据合并到 alert_rule
   UPDATE alert_rule ar
   SET trigger_condition = (
     SELECT JSON_OBJECT(
       'relation', 'AND',
       'items', JSON_ARRAY(...)
     )
     FROM trigger_condition tc
     WHERE tc.id = ar.trigger_condition_id
   );
   
   -- 添加新字段
   ALTER TABLE alert_rule ADD COLUMN dependent_events JSON;
   ALTER TABLE exception_event ADD COLUMN pending_escalations JSON;
   ALTER TABLE exception_event ADD COLUMN recovery_flag BOOLEAN DEFAULT FALSE;
   
   -- 删除旧表（可选）
   -- DROP TABLE trigger_condition;
   ```

2. **代码更新**:
   - 移除 `TriggerConditionRepository` 引用
   - 更新 `AlertRule` entity 使用 JSON 字段
   - 使用 `AlertResolutionService` 处理异常解除
   - 实现 `AlertRecoveryService` 的启动恢复

3. **配置更新**:
   - 触发条件从独立配置改为规则内嵌
   - 依赖事件配置添加到 alert_rule

---

## 最佳实践

### 1. 查看最新代码作为权威参考

文档可能滞后，代码是最准确的：
```bash
# 查看实际实体结构
grep -r "class.*Entity" src/main/java/com/example/scheduled/alert/entity/

# 查看服务类
ls src/main/java/com/example/scheduled/alert/service/

# 查看最新数据库Schema
cat src/main/resources/schema.sql | grep "CREATE TABLE"
```

### 2. 使用数据库图表

最新的数据库设计请参考:
- [ALERT_DB_SCHEMA.md](ALERT_DB_SCHEMA.md) - 包含完整DDL和可视化表格
- [ALERT_DATAFLOW_DIAGRAM.md](ALERT_DATAFLOW_DIAGRAM.md) - 包含ER图

### 3. 理解核心数据流

```
异常检测 → 创建事件 → 初始评估任务
                          ↓
                    任务执行（AlertExecutor）
                          ↓
                    ┌─────┴─────┐
             条件满足            条件不满足
                ↓                    ↓
         触发告警动作           等待下次评估
                ↓
         检查依赖事件
                ↓
         创建下级任务 / 解除异常
```

---

## 待办事项

### 高优先级
- [ ] 更新 ALERT_GENERATION_COMPLETE.md 文件清单
- [ ] 补充 API 文档（Swagger/OpenAPI）
- [ ] 添加单元测试文档

### 中优先级  
- [ ] 创建架构决策记录（ADR）
- [ ] 补充性能测试结果
- [ ] 添加故障排查指南

### 低优先级
- [ ] 视频教程/演示
- [ ] 国际化文档（英文版）

---

## 反馈与问题

如发现文档错误或代码不一致，请：
1. 优先查看源代码确认实际实现
2. 查看 Git 提交历史了解变更原因
3. 参考测试用例了解使用方式

---

**文档维护者**: AI Assistant  
**最后验证日期**: 2025-12-12  
**代码库版本**: 1.0.0
