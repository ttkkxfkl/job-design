# Alert 系统重要变更日志

**最后更新**: 2025年12月13日

---

## 变更1: 废弃 recovery_flag 字段 

**变更日期**: 2025年12月13日  
**影响范围**: AlertRecoveryService, 文档

### 问题描述
原设计中，系统恢复时设置 `recovery_flag = true`，但如果在调度后、任务执行前再次崩溃，由于 flag 已设置，下次启动将不会再恢复。

### 解决方案
- 不再依赖 `recovery_flag` 字段
- 改为基于 `pending_escalations` 实际状态（WAITING/READY）判断是否需要恢复
- 每次启动都检查所有 ACTIVE 事件的 pending_escalations 状态
- 只要有 WAITING/READY 状态就恢复，没有就跳过

### 代码变更
- `AlertRecoveryService.recoverAlertSystem()`: 移除 `recovery_flag = false` 查询条件
- `AlertRecoveryService.recoverSingleEvent()`: 删除 `event.setRecoveryFlag(true)` 设置
- 新增 `hasUnfinishedEscalations()` 方法判断是否需要恢复

### 数据库影响
- `recovery_flag` 字段保留（兼容性），但代码不再使用
- 索引保留，不影响性能

### 文档更新
- ✅ `ALERT_DB_SCHEMA.md`: 字段说明标记为【已废弃】
- ✅ `ALERT_DATA_FLOW.md`: 恢复流程图更新
- ✅ `ALERT_CODE_REVIEW_ISSUES.md`: 问题示例更新
- ✅ `AlertRecoveryService.java`: 类和方法注释更新

---

## 变更2: 依赖延迟调度逻辑优化

**变更日期**: 2025年12月13日  
**影响范围**: AlertDependencyManager

### 问题描述
原逻辑中，`checkDependenciesSatisfied()` 同时检查事件发生和时间延迟：
```java
if (eventTime != null && now >= eventTime + delay) {
    return true;
}
```
这导致：如果事件发生但时间未到，不会调度任务，依赖后续事件触发时再次检查。

### 解决方案
分离事件发生检查和时间延迟处理：

1. **`checkAllEventsOccurred()`**: 只检查事件是否发生，忽略延迟时间
2. **`calculateMaxRequiredTime()`**: 计算所有依赖中最晚的时间点
3. **调度决策**: 
   - 事件都发生 + 时间未到 → 创建延迟任务（scheduledTime）
   - 事件都发生 + 时间已到 → 立即执行

### 代码变更
- `checkDependenciesSatisfied()` → 拆分为 `checkAllEventsOccurred()` + `calculateMaxRequiredTime()`
- `checkSingleDependency()` → 重命名为 `checkEventOccurred()`，只检查事件存在
- `checkPendingEscalationsForEvent()`: 重构调度逻辑，分步处理

### 数据更新
- `pending_escalations` 中新增 `scheduledTime` 字段记录计划执行时间
- 状态更新为 READY 时同时写入 scheduledTime

### 优势
- 依赖满足后立即调度，无需等待后续事件
- 支持精确的延迟调度
- 系统重启后能根据 scheduledTime 恢复

---

## 变更3: 待解决问题

### P0: 第一个规则缺少依赖检查
**位置**: `AlertEscalationService.scheduleInitialEvaluation()`  
**问题**: 第一个规则直接创建任务，跳过依赖检查  
**影响**: 如果第一个规则配置了依赖，会被忽略  
**状态**: ⏳ 待修复

### P1: 事务传播可能导致数据不一致
**位置**: 多个服务方法  
**问题**: 部分方法使用 `@Transactional(propagation = REQUIRES_NEW)`  
**影响**: 可能导致部分操作回滚而部分提交  
**状态**: ⏳ 待优化

### P2: RelativeEventTrigger 事件时间解析
**位置**: `RelativeEventTrigger.getEventTime()`  
**问题**: detection_context 存储字符串，但方法期望 LocalDateTime 对象  
**影响**: 相对时间触发可能失效  
**状态**: ⏳ 待修复

---

## 兼容性说明

### 向后兼容
- `recovery_flag` 字段保留，旧数据不受影响
- 新逻辑能处理各种 pending_escalations 状态

### 升级建议
1. 部署新版本前，确保所有 ACTIVE 事件状态正常
2. 首次启动时，系统会根据新逻辑重新评估所有待恢复事件
3. 如有异常，可通过 `manualRecover()` 手动触发恢复

---

## 监控建议

### 关键指标
- 启动时发现的待恢复事件数量
- 恢复成功/失败的事件数量
- pending_escalations 中 WAITING/READY 状态的分布

### 日志关键字
- `"发现 {} 个待恢复的异常事件"`
- `"延迟调度等级 [{}] 评估任务于"`
- `"报警升级依赖事件已满足"`

---

**注意**: 本文档记录重要设计变更，便于后续维护和问题追溯。
