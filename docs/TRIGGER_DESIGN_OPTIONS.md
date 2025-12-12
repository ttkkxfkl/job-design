# 报警触发时间设计 - 三种方案对比

## 概述

本文档说明了 `AbsoluteTimeTrigger` 中三种触发时间设计方案的实现思路和对比。代码已全部实现并保存在 [AbsoluteTimeTrigger.java](../src/main/java/com/example/scheduled/alert/trigger/strategy/AbsoluteTimeTrigger.java)，目前采用**方案3**，其他方案代码已注释保留，可根据业务需求后期切换。

---

## 当前方案：方案3（保持简单）

### 特点
✅ **优点**：
- 实现简单，易于维护
- 性能开销最小
- 逻辑清晰易懂
- 适用于大多数简单报警场景

❌ **限制**：
- 只支持**日级重复**（每天同一时刻）
- 不支持周/月级别的模式
- 不处理时区问题（依赖系统时区）
- 无法支持复杂的时间规则

### 支持的模式
```
absoluteTime = "16:00" => 每天 16:00 触发
```

### 实现代码
```java
// shouldTrigger - 判断当前时刻是否应该触发
if (currentTime.isAfter(triggerTime) || currentTime.equals(triggerTime)) {
    return true;
}

// calculateNextEvaluationTime - 计算下次评估时间
if (now.isBefore(todayTriggerTime)) {
    return todayTriggerTime;  // 返回今天的触发时间
} else {
    return todayTriggerTime.plusDays(1);  // 返回明天的触发时间
}
```

### 何时使用
- 大多数简单的报警场景
- 没有复杂时间规则的需求
- 需要快速上线，最小化复杂度

---

## 可选方案1：Cron 表达式（最灵活）

### 特点
✅ **优点**：
- 支持**任意复杂**的时间模式
- 业界标准，用户文档丰富
- 与 Quartz 调度器原生兼容
- 可以精细控制时间规则

❌ **劣势**：
- 实现复杂度高
- Cron 解析有性能开销
- 用户需要理解 Cron 语法
- 调试困难

### 支持的模式
```
"0 16 * * *"        => 每天 16:00
"0 16 * * 3"        => 每周三 16:00
"0 16 15 * *"       => 每月15号 16:00
"0 */2 * * *"       => 每2小时
"0 9,14,18 * * *"   => 每天 9:00、14:00、18:00
"0 0 1 * *"         => 每月1号 0:00（月初）
```

### 必需改动
1. **TriggerCondition** 添加字段：
   ```java
   private String cronExpression;  // Cron 表达式
   ```

2. **数据库迁移**：
   ```sql
   ALTER TABLE trigger_condition ADD COLUMN cron_expression VARCHAR(100);
   ```

3. **前端表单**：需要增加 Cron 表达式编辑器（可使用开源组件）

### 实现代码位置
见 `AbsoluteTimeTrigger.java` 中的 `shouldTrigger_CronBased()` 和 `calculateNextEvaluationTime_CronBased()` 方法（已注释）

### 何时使用
- 需要支持周/月/年级别的时间规则
- 用户需要高度灵活的时间配置
- 团队对 Cron 表达式有经验

### 切换步骤
1. 取消注释 `shouldTrigger_CronBased()` 和 `calculateNextEvaluationTime_CronBased()`
2. 修改 `TriggerStrategyFactory` 中的逻辑，优先读取 cronExpression
3. 更新数据库和前端表单
4. 添加 Cron 表达式验证逻辑

---

## 可选方案2：扩展设计（支持周/月级别）

### 特点
✅ **优点**：
- 支持日/周/月级别（覆盖 90% 的需求）
- 比 Cron 简单易理解
- 不需要用户学习 Cron 语法
- 实现复杂度中等

❌ **劣势**：
- 不支持"每2小时"这样的复杂模式
- 需要添加多个新字段
- 实现逻辑比方案3复杂

### 支持的模式
```
frequency="DAILY", absoluteTime="16:00"
  => 每天 16:00

frequency="WEEKLY", dayOfWeek="WEDNESDAY", absoluteTime="16:00"
  => 每周三 16:00

frequency="MONTHLY", dayOfMonth=15, absoluteTime="16:00"
  => 每月15号 16:00
```

### 必需改动
1. **TriggerCondition** 添加字段：
   ```java
   private String frequency;        // DAILY, WEEKLY, MONTHLY
   private DayOfWeek dayOfWeek;    // 周几（WEEKLY时需要）
   private Integer dayOfMonth;      // 几号（MONTHLY时需要）
   ```

2. **数据库迁移**：
   ```sql
   ALTER TABLE trigger_condition 
   ADD COLUMN frequency VARCHAR(20),
   ADD COLUMN day_of_week VARCHAR(20),
   ADD COLUMN day_of_month INT;
   ```

3. **前端表单**：
   - 频率选择下拉框
   - 星期几/几号的条件显示

### 实现代码位置
见 `AbsoluteTimeTrigger.java` 中的 `shouldTrigger_FrequencyBased()` 和 `calculateNextEvaluationTime_FrequencyBased()` 方法（已注释）

### 何时使用
- 需要支持周/月级别的规则
- 用户习惯用"每周三"而不是 Cron
- 想避免 Cron 的学习曲线

### 切换步骤
1. 取消注释 `shouldTrigger_FrequencyBased()` 和 `calculateNextEvaluationTime_FrequencyBased()`
2. 在 `TriggerCondition` 添加新字段
3. 执行数据库迁移
4. 更新前端表单和验证逻辑
5. 更新 `TriggerStrategyFactory` 的创建逻辑

---

## 方案对比表

| 特性 | 方案1（Cron） | 方案2（频率） | 方案3（当前） |
|------|-------------|-----------|----------|
| **实现复杂度** | 高 | 中 | 低 ✅ |
| **性能** | 中等 | 好 | 优秀 ✅ |
| **学习曲线** | 陡峭 | 平缓 | 无 ✅ |
| **支持日级** | ✅ | ✅ | ✅ |
| **支持周级** | ✅ | ✅ | ❌ |
| **支持月级** | ✅ | ✅ | ❌ |
| **支持年级** | ✅ | ❌ | ❌ |
| **支持"每N小时"** | ✅ | ❌ | ❌ |
| **开发工作量** | 大 | 中 | 小 |
| **数据库改动** | 1个字段 | 3个字段 | 0个字段 ✅ |
| **Quartz 兼容** | 原生兼容 | 需要适配 | 无关 ✅ |

---

## 实现细节

### 方案1 关键点
- 使用 `org.quartz.CronExpression` 解析表达式
- 精度精确到**分钟**（Cron的标准）
- 需要处理异常和验证

### 方案2 关键点
- 频率分流逻辑（switch 语句）
- 星期几计算（modulo 7）
- 月初月末处理（可能需要特殊逻辑）

### 方案3 关键点
- 简单的 LocalTime 比较
- 按天递进的简单逻辑
- 无特殊边界情况

---

## 建议

### 短期（现在）
**继续使用方案3**。它简单、高效，满足目前需求，降低系统复杂度。

### 中期（1-2个季度）
如果用户需求出现"每周XX"或"每月XX"，升级到**方案2**。改动相对小，实现难度也不高。

### 长期（需要时）
如果需要完全灵活的时间配置，升级到**方案1（Cron）**。但需要：
1. 完整的需求分析
2. 前端 Cron 编辑器
3. 充分的用户培训

---

## 代码位置

所有三种方案的实现都在同一个文件中，便于对比和切换：

**文件**: [AbsoluteTimeTrigger.java](../src/main/java/com/example/scheduled/alert/trigger/strategy/AbsoluteTimeTrigger.java)

```
第 18-40 行   => shouldTrigger()（方案3，当前）
第 42-55 行   => calculateNextEvaluationTime()（方案3，当前）
第 61-106 行  => shouldTrigger_CronBased()（方案1，已注释）
第 108-130 行 => calculateNextEvaluationTime_CronBased()（方案1，已注释）
第 138-200 行 => shouldTrigger_FrequencyBased()（方案2，已注释）
第 202-243 行 => calculateNextEvaluationTime_FrequencyBased()（方案2，已注释）
```

---

## 切换指南

### 从方案3切换到方案2
1. ✅ 代码已实现
2. 在 `TriggerCondition` 实体中添加 `frequency`、`dayOfWeek`、`dayOfMonth` 字段
3. 执行数据库迁移
4. 在 `shouldTrigger()` 中调用 `shouldTrigger_FrequencyBased()`
5. 在 `calculateNextEvaluationTime()` 中调用 `calculateNextEvaluationTime_FrequencyBased()`
6. 测试各种频率组合

### 从方案3切换到方案1
1. ✅ 代码已实现
2. 在 `TriggerCondition` 实体中添加 `cronExpression` 字段
3. 执行数据库迁移
4. 在 `shouldTrigger()` 中调用 `shouldTrigger_CronBased()`
5. 在 `calculateNextEvaluationTime()` 中调用 `calculateNextEvaluationTime_CronBased()`
6. 添加 Cron 表达式验证
7. 前端集成 Cron 编辑器组件
8. 充分测试各种 Cron 表达式

---

## 常见问题

**Q: 为什么当前选择方案3？**
A: 因为简单易维护，KISS 原则（Keep It Simple, Stupid）。大多数报警场景只需要"每天固定时刻"。

**Q: 能否三种方案共存？**
A: 可以，但会增加复杂度。建议一次只支持一种方案，避免混乱。

**Q: 方案1中Cron的开销有多大？**
A: 每次评估需要解析和计算，比方案3多 0.5-2ms。如果评估频繁，可以考虑缓存。

**Q: 方案2中月底日期怎么处理？**
A: 代码中未考虑，建议添加 `try-catch` 处理 `DateTimeException`。

---

## 相关文档

- [TriggerStrategy.md](./TriggerStrategy.md) - 触发策略接口说明
- [AlertConstants.md](./ALERT_SYSTEM_GUIDE.md) - 常量管理
- [ALERT_SYSTEM_GUIDE.md](./ALERT_SYSTEM_GUIDE.md) - 报警系统完整指南
