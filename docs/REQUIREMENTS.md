# 定时任务系统（PRD/需求文档）

本文档用于与产品经理协作对齐目标、范围与待确认项，作为进一步需求澄清与迭代的基础。

## 1. 背景与目标
- 背景：现有系统提供一次性与周期性（Cron）定时任务能力，支持多种任务类型（日志、邮件、短信、Webhook、计划、消息队列预留），并可水平扩展（Quartz 集群）。
- 目标：
  - 为内部业务/运营场景提供可靠、可观测、便于管理的任务调度平台。
  - 简化前后端对接（统一任务类型元数据、标准接口）。
  - 支持扩展更多任务类型与执行策略。

## 2. 术语
- 任务（Task）：一次执行单元，包含类型、数据、调度方式等。
- 任务类型（TaskType）：如 LOG/EMAIL/SMS/WEBHOOK/PLAN/MQ 等。
- 调度模式（ScheduleMode）：ONCE（一次性）、CRON（周期性）。
- Quartz：持久化分布式调度框架，系统采用 LocalDataSourceJobStore + MySQL 集群表。

## 3. 使用角色与用户故事
- 角色
  - 运营/业务同学：通过前端创建、管理任务，查看执行结果与统计。
  - 平台管理员：配置系统策略，查看运行状态，处理异常任务。
- 用户故事（示例）
  - 我可以创建一次性任务在某时刻执行。
  - 我可以创建 Cron 任务周期执行，并随时暂停/恢复/取消。
  - 我可以查看任务执行历史日志与统计，快速定位失败原因。
  - 我可以在任务类型下拉中看到中文名称与说明。

## 4. 范围（当前能力）
- 任务管理
  - 创建一次性任务（ONCE）：指定执行时间、类型、任务数据、优先级、超时、最大重试次数。
  - 创建 Cron 任务（CRON）：指定 Cron 表达式及同上属性。
  - 查询任务详情、列表（可按状态筛选）。
  - 操作：取消、暂停、恢复、立即重试。
- 元数据
  - 任务类型列表：从执行器注解自动发现，提供 code、name（中文）、description。
- 统计与运行态
  - 调度器状态（Quartz/线程池/集群信息）。
  - 待执行任务数量。
  - 总体统计、每日统计、类型/模式/状态分布。
- 可扩展性
  - 基于注解的执行器发现：新增执行器 += 新类型自动出现在前端类型列表。
  - Quartz 集群化：多实例下保障触发一致性。

## 5. 非功能性要求
- 可用性：支持水平扩展（Quartz 集群），单实例宕机不影响整体。
- 可靠性：任务状态持久化；失败有重试次数与错误记录。
- 性能：常规万级任务量，秒级调度响应；高并发下可按线程池配置扩容。
- 兼容性：JDK 17+，Spring Boot 3.2，MySQL 8。
- 可观测性：日志打印、执行历史、基础统计；后续补充指标/告警。

## 6. 数据模型（高层）
- 表：`scheduled_task`
  - 关键字段：id、task_name、task_type、schedule_mode、execute_time、cron_expression、priority(0-10)、execution_timeout、task_data(JSON)、status、retry_count、max_retry_count、last_execute_time、error_message、created_at、updated_at。
- 表：Quartz `QRTZ_*`（由 Quartz 管理）
- 执行日志：`task_execution_log`（实体已用，表结构对齐后续落地或复用现有）

## 7. 关键接口（后端）
- 任务创建
  - POST `/api/tasks/once`：创建一次性任务
  - POST `/api/tasks/cron`：创建 Cron 任务（需 `cronExpression`）
  - POST `/api/tasks`：兼容接口（有/无 cron 自动判断）
- 任务查询
  - GET `/api/tasks/{id}`：任务详情
  - GET `/api/tasks`：任务列表（可选 `status`）
- 任务操作
  - DELETE `/api/tasks/{id}`：取消
  - PUT `/api/tasks/{id}/pause`：暂停
  - PUT `/api/tasks/{id}/resume`：恢复
  - POST `/api/tasks/{id}/retry`：立即重试
- 任务日志
  - GET `/api/tasks/{id}/logs`
- 运行态与统计
  - GET `/api/tasks/scheduler/status`
  - GET `/api/tasks/count/pending`
  - GET `/api/tasks/statistics/summary`
  - GET `/api/tasks/statistics/daily?days=7`
  - GET `/api/tasks/statistics/type-distribution`
  - GET `/api/tasks/statistics/mode-distribution`
  - GET `/api/tasks/statistics/status-distribution`
- 元数据
  - GET `/api/tasks/types`：任务类型元数据（注解驱动）

## 8. 任务类型与元数据（面向前端）
- 通过注解 `@TaskExecutorInfo(taskType, displayName, description, priority)` 自动收集。
- 当前内置：
  - EMAIL（邮件）、SMS（短信）、WEBHOOK（回调）、LOG（日志）、PLAN（计划）、MQ（预留）。
- 扩展方式：新增执行器 + 标注注解 +（可选）枚举补充，即可自动出现在 `/api/tasks/types`。

## 9. 权限与安全（待定）
- 鉴权模型：是否需要登录态与权限控制（角色/租户/项目隔离）。
- 操作审计：任务创建/修改/操作日志追踪。
- 数据安全：任务数据中的敏感字段脱敏/加密（如邮箱、手机号、Token）。

## 10. 监控与告警（建议）
- 指标（建议后续引入）：
  - 任务触发总数/成功率/失败率/平均延迟/执行时间分布/超时次数/重试次数。
- 告警策略：
  - 连续失败阈值、超时告警、堆积任务阈值、调度线程池耗尽。

## 11. 里程碑（建议）
- M1 基线版（已具备）：任务全生命周期、注解型类型元数据、统计与状态接口。
- M2 易用性：
  - 前端创建流程优化（校验、预览、模板化）；
  - 类型表单 schema（按类型动态渲染字段）。
- M3 可观测：指标上报、Grafana 看板、失败通知策略（邮件/企业微信等）。
- M4 企业级：权限/审计、多租户隔离、任务配额/限流。

## 12. 风险与假设
- 假设：单条任务数据体量中等（JSON < 64KB），可直接存储于 MySQL。
- 风险：
  - Cron 表达式不当导致频繁触发；
  - 外部依赖（Webhook/邮件/短信）不可用导致失败堆积；
  - 大量超时任务占用线程；
  - 无权限隔离时的越权操作风险。

## 13. 待产品确认清单（重点）
1. 账号权限：是否需要登录、角色模型、租户/项目隔离？
2. 任务类型表单：各类型任务需要的字段规范（必填、校验规则、字段提示）。
3. 重试策略：固定间隔/指数退避？最大重试上限默认值？失败后是否告警？
4. 优先级定义：当前 0-10，含义与默认值需要对齐。
5. 超时策略：默认超时时间？超时后是否中断/标记？
6. 并发/幂等：同一任务是否允许并发执行？是否需要幂等键？
7. Cron 约束：允许的触发频次上限？时区/夏令时策略？
8. Webhook 安全：是否需要签名/白名单/超时时间/重定向限制？
9. 通知通道：失败/超时/停用等场景需要通知谁、以什么方式？
10. 数据保留：任务与执行日志的保留周期与清理策略。
11. 指标与告警：首批需要的核心指标与阈值。
12. 多语言：系统是否需要国际化（目前类型名已提供中文）。

## 14. 附录：技术栈与运行环境
- Java 17、Spring Boot 3.2、MyBatis-Plus 3.5.5、Quartz 2.3.2（LocalDataSourceJobStore，MySQL 持久化，支持集群）。
- 端口：默认 `18083`（可在 `application.yml` 覆盖）。
- 配置：`spring.datasource.*`，`spring.quartz.*`（表前缀、集群启用、线程数等）。

---
如需我补充原型流程图/字段校验表或接口契约细节（示例请求/响应），告诉我优先级，我可以继续完善。