# ExceptionEvent 业务ID字段更新说明

## 概述

为了更好地追溯报警来源，在 `ExceptionEvent` 实体类中新增了 `businessId` 和 `businessType` 两个字段，用于标识报警属于哪个业务数据产生。

## 修改内容

### 1. 实体类修改

**文件**: `src/main/java/com/example/scheduled/alert/entity/ExceptionEvent.java`

新增字段：
```java
/**
 * 业务数据ID（标识报警来源于哪条业务数据）
 */
private String businessId;

/**
 * 业务类型（标识业务数据的类型，如：SHIFT-班次, BOREHOLE-钻孔, OPERATION-操作等）
 */
private String businessType;
```

### 2. 数据库Schema修改

#### 2.1 初始Schema

**文件**: `src/main/resources/alert-schema.sql`

在 `exception_event` 表中添加：
```sql
business_id VARCHAR(100) COMMENT '业务数据ID（标识报警来源于哪条业务数据）',
business_type VARCHAR(50) COMMENT '业务类型（如：SHIFT-班次, BOREHOLE-钻孔, OPERATION-操作等）',
```

并添加索引：
```sql
INDEX idx_business_id (business_id),
INDEX idx_business_type (business_type)
```

#### 2.2 增量迁移脚本

**文件**: `src/main/resources/alert-migration-v2.sql`

为已有数据库添加字段的ALTER语句。

#### 2.3 专用迁移脚本（新增）

**文件**: `src/main/resources/alert-migration-v3-business-id.sql`

专门用于添加业务ID字段的独立迁移脚本，包含：
- 字段添加
- 索引创建
- 使用说明和示例

### 3. Repository层修改

**文件**: `src/main/java/com/example/scheduled/alert/repository/ExceptionEventRepository.java`

新增查询方法：
```java
/**
 * 根据业务ID查询异常事件
 */
@Select("SELECT * FROM exception_event WHERE business_id = #{businessId} ORDER BY detected_at DESC")
List<ExceptionEvent> findByBusinessId(String businessId);

/**
 * 根据业务ID和业务类型查询活跃异常事件
 */
@Select("SELECT * FROM exception_event WHERE business_id = #{businessId} AND business_type = #{businessType} " +
        "AND status = 'ACTIVE' ORDER BY detected_at DESC")
List<ExceptionEvent> findActiveEventsByBusinessIdAndType(String businessId, String businessType);
```

### 4. 文档更新

**文件**: `docs/ALERT_DB_SCHEMA.md`

更新了数据库表结构说明，添加了 `business_id` 和 `business_type` 字段的描述和示例。

**文件**: `src/main/resources/alert-init-example.sql`

添加了创建异常事件时包含业务ID字段的注释说明。

## 使用指南

### 创建异常事件时指定业务ID

```java
ExceptionEvent event = ExceptionEvent.builder()
    .exceptionTypeId(1L)
    .businessId("SHIFT_20251212_001")  // 班次ID
    .businessType("SHIFT")              // 业务类型：班次
    .detectedAt(LocalDateTime.now())
    .status("ACTIVE")
    .currentAlertLevel("NONE")
    .build();

exceptionEventRepository.insert(event);
```

### API调用示例

```bash
POST /api/alert/event
Content-Type: application/json

{
  "exceptionTypeId": 1,
  "businessId": "SHIFT_20251212_001",
  "businessType": "SHIFT",
  "detectionContext": {
    "shiftName": "早班",
    "teamId": "TEAM_A"
  }
}
```

### 查询示例

```java
// 1. 查询某个业务数据的所有报警
List<ExceptionEvent> events = exceptionEventRepository.findByBusinessId("SHIFT_20251212_001");

// 2. 查询某个班次的所有活跃报警
List<ExceptionEvent> activeEvents = exceptionEventRepository
    .findActiveEventsByBusinessIdAndType("SHIFT_20251212_001", "SHIFT");
```

### SQL查询示例

```sql
-- 1. 查询某个业务数据的所有报警
SELECT * FROM exception_event WHERE business_id = 'SHIFT_20251212_001';

-- 2. 查询某个业务类型的活跃报警
SELECT * FROM exception_event 
WHERE business_type = 'SHIFT' AND status = 'ACTIVE';

-- 3. 统计各业务类型的报警数量
SELECT business_type, COUNT(*) as alert_count 
FROM exception_event 
WHERE status = 'ACTIVE'
GROUP BY business_type;
```

## 业务类型枚举建议

以下是建议的业务类型枚举值：

| 业务类型 | 说明 | 示例ID格式 |
|---------|------|-----------|
| SHIFT | 班次 | SHIFT_20251212_001 |
| BOREHOLE | 钻孔 | BOREHOLE_001 |
| OPERATION | 操作记录 | OP_20251212_1234 |
| EQUIPMENT | 设备 | EQ_PUMP_001 |
| WORKER | 工人 | WORKER_10001 |
| PROJECT | 项目 | PROJECT_2025_001 |

可根据实际业务需求扩展。

## 数据库迁移步骤

### 对于新建数据库

直接执行 `alert-schema.sql` 即可，已包含业务ID字段。

### 对于已有数据库

执行以下任一迁移脚本：

**方式1**: 执行 `alert-migration-v2.sql`（包含其他字段更新）
```bash
mysql -u root -p scheduled_task < src/main/resources/alert-migration-v2.sql
```

**方式2**: 仅执行业务ID相关更新
```bash
mysql -u root -p scheduled_task < src/main/resources/alert-migration-v3-business-id.sql
```

## 向后兼容性

- `businessId` 和 `businessType` 字段均为 **可选字段**（允许NULL）
- 已有的异常事件数据不受影响，这两个字段会自动为NULL
- API接口向后兼容，不传这两个字段不会报错
- 建议新创建的异常事件都带上业务ID信息

## 相关文件清单

### Java源码
- `src/main/java/com/example/scheduled/alert/entity/ExceptionEvent.java`
- `src/main/java/com/example/scheduled/alert/repository/ExceptionEventRepository.java`

### SQL脚本
- `src/main/resources/alert-schema.sql`
- `src/main/resources/alert-migration-v2.sql`
- `src/main/resources/alert-migration-v3-business-id.sql`（新增）
- `src/main/resources/alert-init-example.sql`

### 文档
- `docs/ALERT_DB_SCHEMA.md`
- `docs/BUSINESS_ID_UPDATE.md`（本文档）

## 注意事项

1. **索引性能**: 已为 `business_id` 和 `business_type` 添加索引，按业务ID查询性能良好
2. **字段长度**: `business_id` 设置为 VARCHAR(100)，足够存储大部分业务ID格式
3. **业务类型**: `business_type` 建议使用固定的枚举值，便于统计和分类
4. **关联查询**: 可以通过 `business_id` 将报警信息关联回业务系统的原始数据

## 示例场景

### 场景1: 班次入井记录不足报警

```java
// 检测到班次 SHIFT_20251212_001 入井记录不足
ExceptionEvent event = ExceptionEvent.builder()
    .exceptionTypeId(1L)  // 入井记录不足异常类型
    .businessId("SHIFT_20251212_001")
    .businessType("SHIFT")
    .detectedAt(LocalDateTime.now())
    .detectionContext(Map.of(
        "shiftName", "早班",
        "shiftDate", "2025-12-12",
        "teamId", "TEAM_A",
        "teamName", "A组"
    ))
    .status("ACTIVE")
    .currentAlertLevel("NONE")
    .build();
```

### 场景2: 钻孔操作超时报警

```java
ExceptionEvent event = ExceptionEvent.builder()
    .exceptionTypeId(2L)  // 钻孔操作超时异常类型
    .businessId("BOREHOLE_20251212_005")
    .businessType("BOREHOLE")
    .detectedAt(LocalDateTime.now())
    .detectionContext(Map.of(
        "boreholeName", "5号钻孔",
        "operatorId", "10001",
        "startTime", "2025-12-12T08:00:00"
    ))
    .status("ACTIVE")
    .currentAlertLevel("NONE")
    .build();
```

### 场景3: 查询某个班次的所有报警历史

```java
// 获取班次的所有报警（包括已解除的）
List<ExceptionEvent> allAlerts = exceptionEventRepository
    .findByBusinessId("SHIFT_20251212_001");

// 只获取活跃的报警
List<ExceptionEvent> activeAlerts = exceptionEventRepository
    .findActiveEventsByBusinessIdAndType("SHIFT_20251212_001", "SHIFT");

// 在业务系统中展示
System.out.println("班次 SHIFT_20251212_001 共有 " + allAlerts.size() + " 条报警记录");
System.out.println("其中活跃报警 " + activeAlerts.size() + " 条");
```

## 总结

通过添加 `businessId` 和 `businessType` 字段，报警系统现在可以：

1. ✅ **追溯来源**: 清楚地知道每个报警来自哪个业务数据
2. ✅ **关联查询**: 可以从业务系统查询相关的所有报警
3. ✅ **分类统计**: 按业务类型统计报警分布
4. ✅ **多维分析**: 支持按业务维度进行报警分析
5. ✅ **业务闭环**: 报警解除后可以更新业务数据状态

这使得报警系统与业务系统的集成更加紧密，提供了完整的报警生命周期追踪能力。
