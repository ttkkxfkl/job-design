# 前端报警配置界面 - API 需求分析

## 前端设计图功能分析

### 界面布局概览
```
┌─────────────────────────────────────────────────┐
│  报警配置界面                                    │
├─────────────────────────────────────────────────┤
│ 【状态标签】
│ ✓ 未关闭创建时间水表  ✓ xxx  ⚫ 11条报警
│
│ 【基础配置】 Section
│ • 报警级别 (选择: 红/橙/黄/蓝) ← AlertRule.level
│ • 适用机构 (下拉选择: 山西省)  ← 新字段: orgScope
│ • 是否启用 (Toggle: ON/OFF)     ← AlertRule.enabled
│
│ 【触发时间配置】 Section（可多个）
│ ┌──────────────────────────────────────────────┐
│ │ 红色报警 (触发条件1)
│ │ • 触发条件类型: [下拉选择]                    ← TriggerCondition.conditionType
│ │ • 计时器图标: [检测计划时间]                  ← TriggerCondition 详情
│ │ • 符号: > / ≥ / < / ≤ / =                    ← 新字段: operator
│ │ • 数值: 16 (输入框)                          ← 新字段: thresholdValue
│ │ • 单位: 小时 (下拉)                          ← 新字段: timeUnit
│ │ • 删除按钮 | 编辑按钮 (可选)
│ └──────────────────────────────────────────────┘
│
│ 类似 橙色报警、黄色报警、蓝色报警 ...
│
│ 【操作按钮】
│ [取消]  [保存]
└─────────────────────────────────────────────────┘
```

---

## 前端需要的接口清单

### 1. **获取报警规则配置信息** ✅ (部分覆盖)
**功能**: 编辑模式下加载现有规则数据

**URL**: 
```
GET /api/alert/rule-config/:ruleId
GET /api/alert/rule-config/:exceptionTypeId
```

**当前 Controller 覆盖状态**:
- ✅ GET /api/alert/rules/{exceptionTypeId} - 获取异常类型的所有规则
- ✅ GET /api/alert/rules/item/{id} - 获取单个规则
- ❌ **缺失**: 返回格式需要包含触发条件展开的详细信息

**需要的响应格式**:
```json
{
  "code": 0,
  "data": {
    "id": 1,
    "exceptionTypeId": 1,
    "level": "LEVEL_1",
    "orgScope": "山西省",
    "enabled": true,
    "triggerCondition": {
      "id": 10,
      "conditionType": "ABSOLUTE",
      "absoluteTime": "16:00:00",
      "displayName": "每天16:00"
    },
    "actionType": "LOG|EMAIL|SMS|WEBHOOK",
    "actionConfig": {...},
    "priority": 5
  }
}
```

---

### 2. **保存/更新报警规则** ✅ (部分覆盖)
**功能**: 新建或编辑规则时保存

**URL**:
```
POST /api/alert/rule-config
PUT  /api/alert/rule-config/:id
```

**当前 Controller 覆盖状态**:
- ✅ POST /api/alert/rule - 创建规则
- ✅ PUT /api/alert/rules/{id} - 更新规则
- ❌ **问题**: 缺失统一的保存接口，需要返回完整的规则链式关系

**前端发送的请求体格式**:
```json
{
  "exceptionTypeId": 1,
  "level": "LEVEL_1",
  "orgScope": "山西省",
  "enabled": true,
  "triggerConditionId": 10,
  "operator": ">",
  "thresholdValue": 16,
  "timeUnit": "HOUR",
  "actionType": "LOG",
  "actionConfig": {
    "logLevel": "WARN",
    "message": "异常告警"
  },
  "priority": 5
}
```

**需要做的修改**:
1. AlertRule 实体增加字段: `orgScope`, `operator`, `thresholdValue`, `timeUnit`
2. Controller 中 createAlertRule 和 updateRule 需要支持这些新字段的验证和保存

---

### 3. **获取触发条件列表** ❌ (需要新增)
**功能**: 填充"触发条件类型"下拉菜单

**URL**:
```
GET /api/alert/trigger-conditions
GET /api/alert/trigger-conditions?exceptionTypeId=1
```

**当前 Controller 状态**:
- ✅ POST /api/alert/trigger-condition - 创建触发条件
- ❌ **缺失**: 没有列表查询接口

**响应格式**:
```json
{
  "code": 0,
  "data": [
    {
      "id": 10,
      "conditionType": "ABSOLUTE",
      "absoluteTime": "16:00:00",
      "displayName": "每天16:00"
    },
    {
      "id": 11,
      "conditionType": "RELATIVE",
      "relativeEventType": "FIRST_BOREHOLE_START",
      "relativeDurationMinutes": 480,
      "displayName": "钻孔开始后480分钟"
    }
  ]
}
```

**需要做的修改**:
1. Controller 新增 GET /api/alert/trigger-conditions 接口
2. TriggerCondition 实体添加 `displayName` 字段用于前端展示

---

### 4. **获取机构列表** ❌ (需要新增)
**功能**: 填充"适用机构"下拉菜单

**URL**:
```
GET /api/alert/organizations
GET /api/alert/orgs
```

**当前 Controller 状态**:
- ❌ **缺失**: 没有机构管理接口

**响应格式**:
```json
{
  "code": 0,
  "data": [
    {
      "id": 1,
      "name": "山西省",
      "code": "SX"
    },
    {
      "id": 2,
      "name": "陕西省",
      "code": "SN"
    }
  ]
}
```

**需要做的修改**:
1. 新增 Organization 实体和 OrganizationRepository
2. 新增 OrganizationController 或在 AlertRuleController 中新增相关端点

---

### 5. **删除报警规则** ✅ (已覆盖)
**功能**: 删除规则

**URL**:
```
DELETE /api/alert/rules/{id}
```

**当前 Controller 覆盖状态**:
- ✅ DELETE /api/alert/rules/{id} - 已实现

---

### 6. **启用/禁用规则** ✅ (已覆盖)
**功能**: 切换规则的启用状态

**URL**:
```
PUT /api/alert/rules/{id}/enabled
```

**当前 Controller 覆盖状态**:
- ✅ PUT /api/alert/rules/{id}/enabled - 已实现

---

### 7. **获取时间单位列表** ❌ (需要新增或返回常量)
**功能**: 填充"单位"下拉菜单（小时/分钟/天等）

**URL**:
```
GET /api/alert/time-units
```

**响应格式**:
```json
{
  "code": 0,
  "data": [
    { "value": "MINUTE", "label": "分钟" },
    { "value": "HOUR", "label": "小时" },
    { "value": "DAY", "label": "天" }
  ]
}
```

**需要做的修改**:
1. 创建 TimeUnit 枚举常量
2. Controller 新增 GET /api/alert/time-units 接口（可选，也可在初始化时返回）

---

### 8. **获取操作符列表** ❌ (需要新增或返回常量)
**功能**: 填充"符号"下拉菜单（>、≥、<、≤、=）

**URL**:
```
GET /api/alert/operators
```

**响应格式**:
```json
{
  "code": 0,
  "data": [
    { "value": ">", "label": "大于 >" },
    { "value": ">=", "label": "大于等于 ≥" },
    { "value": "<", "label": "小于 <" },
    { "value": "<=", "label": "小于等于 ≤" },
    { "value": "=", "label": "等于 =" }
  ]
}
```

**需要做的修改**:
1. 创建 Operator 枚举常量
2. Controller 新增 GET /api/alert/operators 接口

---

## 总结：需要新增的功能

| # | 接口 | 当前状态 | 优先级 | 工作量 |
|---|------|--------|-------|--------|
| 1 | GET /api/alert/trigger-conditions | ❌ 缺失 | **高** | 小 |
| 2 | GET /api/alert/organizations | ❌ 缺失 | **高** | 中 |
| 3 | GET /api/alert/time-units | ❌ 缺失 | 中 | 小 |
| 4 | GET /api/alert/operators | ❌ 缺失 | 中 | 小 |
| 5 | 扩展 AlertRule 实体字段 | ⚠️ 部分 | **高** | 中 |
| 6 | 优化规则响应格式 | ⚠️ 部分 | 中 | 小 |
| 7 | 验证 orgScope 字段 | ❌ 缺失 | 中 | 小 |

---

## 实现计划

### Phase 1: 核心字段扩展（优先级高）
- [ ] AlertRule 实体添加: `orgScope`, `operator`, `thresholdValue`, `timeUnit`
- [ ] TriggerCondition 实体添加: `displayName`
- [ ] 新增 Organization 实体和 Repository

### Phase 2: 必需接口（优先级高）
- [ ] GET /api/alert/trigger-conditions - 触发条件列表
- [ ] GET /api/alert/organizations - 机构列表
- [ ] GET /api/alert/time-units - 时间单位列表
- [ ] GET /api/alert/operators - 操作符列表

### Phase 3: 验证和优化（优先级中）
- [ ] Controller 验证新字段逻辑
- [ ] 更新现有的 create/update 接口对新字段的处理
- [ ] 添加单元测试

---

## 前端-后端数据对应关系

| 前端字段 | 后端字段 | 实体 | 类型 |
|---------|---------|------|------|
| 报警级别 | level | AlertRule | Enum(LEVEL_1/2/3) |
| 适用机构 | orgScope | AlertRule | String |
| 是否启用 | enabled | AlertRule | Boolean |
| 触发条件类型 | conditionType | TriggerCondition | Enum |
| 触发条件详情 | absoluteTime/relativeEventType | TriggerCondition | String |
| 比较符 | operator | AlertRule | String(>/>=/</<=/=) |
| 阈值数值 | thresholdValue | AlertRule | Long/Double |
| 时间单位 | timeUnit | AlertRule | Enum(MINUTE/HOUR/DAY) |
| 动作类型 | actionType | AlertRule | Enum(LOG/EMAIL/SMS/WEBHOOK) |
| 动作配置 | actionConfig | AlertRule | JSON |

