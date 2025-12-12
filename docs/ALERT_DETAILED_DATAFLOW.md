================================================================================
        ALERT 系统完整数据流程详解 - 代码层面追踪
================================================================================

【场景设定】
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
时间线：08:00 ~ 10:30
- 08:00 钻孔系统检测到异常：3小时内没有井下入井记录
- 10:00 第一个钻孔开始，系统发布事件
- 10:15 用户手动解除报警

【数据库初始状态】
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
exception_type 表：
┌────┬──────────────────┬────────────────────────┬────────────────┐
│ id │ name             │ detection_logic_type   │ detection_config│
├────┼──────────────────┼────────────────────────┼────────────────┤
│ 1  │ 入井记录不足     │ RECORD_CHECK           │ {              │
│    │                  │                        │   "tableName": │
│    │                  │                        │   "work_log",  │
│    │                  │                        │   "duration":3h│
│    │                  │                        │ }              │
└────┴──────────────────┴────────────────────────┴────────────────┘

alert_rule 表：
┌────┬──────────────┬───────┬──────────────────────────────────┐
│ id │ level        │ enabled│ dependent_events                 │
├────┼──────────────┼───────┼──────────────────────────────────┤
│ 1  │ LEVEL_1      │ true  │ null (无依赖)                    │
│ 2  │ LEVEL_2      │ true  │ {                                │
│    │ (BLUE等级)   │       │   "events": [                    │
│    │              │       │     {                            │
│    │              │       │       "eventType":               │
│    │              │       │       "FIRST_BOREHOLE_START",    │
│    │              │       │       "delayMinutes": 120,       │
│    │              │       │       "required": true           │
│    │              │       │     }                            │
│    │              │       │   ],                             │
│    │              │       │   "logicalOperator": "AND"       │
│    │              │       │ }                                │
└────┴──────────────┴───────┴──────────────────────────────────┘

================================================================================
        【第一幕】08:00 - 异常检测到事件创建
================================================================================

【业务事件发生】
钻孔系统在 08:00 检测到异常：3小时内没有入井记录
  ↓
【代码调用链】
1. 钻孔系统服务层代码：
   ┌─────────────────────────────────────────────────────────┐
   │ // BoreholeService.java                                 │
   │ public void detectAndCreateAlert() {                    │
   │     if (hasNoDownholeRecordIn3Hours()) {                │
   │         // 检测到异常，创建异常事件                      │
   │         ExceptionEvent event = new ExceptionEvent();    │
   │         event.setExceptionTypeId(1L); // 入井记录不足    │
   │         event.setDetectedAt(LocalDateTime.now()); // 08:00│
   │         event.setStatus("ACTIVE");                      │
   │         event.setCurrentAlertLevel("NONE");             │
   │         event.setDetectionContext(new HashMap<>());     │
   │                                                          │
   │         exceptionEventRepository.insert(event);         │
   │         // 返回生成的事件 ID，假设为 #100               │
   │                                                          │
   │         // 调用告警服务初始化评估                        │
   │         alertEscalationService.scheduleInitialEvaluation│
   │             (event);                                    │
   │     }                                                    │
   │ }                                                        │
   └─────────────────────────────────────────────────────────┘

【数据库第一次更新】
exception_event 表中插入新行：
┌────┬─────────────────┬─────────────┬──────────────┬──────────────────┐
│ id │ exception_type_ │ detected_at  │ status       │ current_alert_   │
│    │ id              │              │              │ level            │
├────┼─────────────────┼─────────────┼──────────────┼──────────────────┤
│100 │ 1               │ 08:00:00    │ ACTIVE       │ NONE             │
│    │                 │              │              │                  │
│    │ detection_      │ pending_     │ resolved_at  │ recovery_flag    │
│    │ context         │ escalations  │              │                  │
├────┼─────────────────┼─────────────┼──────────────┼──────────────────┤
│    │ {}(empty)       │ {}(empty)   │ null         │ false            │
└────┴─────────────────┴─────────────┴──────────────┴──────────────────┘

================================================================================
        【第二幕】08:00 - 初始化评估和任务创建
================================================================================

【AlertEscalationService.scheduleInitialEvaluation() 执行】
┌─────────────────────────────────────────────────────────────────┐
│ public void scheduleInitialEvaluation(ExceptionEvent event) {   │
│     log.info("为异常事件 [{}] 创建初始评估任务", event.getId()); │
│     // event.getId() = 100                                      │
│                                                                  │
│     // 1️⃣ 查询该异常类型的所有启用的报警规则                     │
│     List<AlertRule> allRules = alertRuleRepository              │
│         .findEnabledRulesByExceptionType(1L);                   │
│     // 返回两个规则：                                             │
│     // - AlertRule(id=1, level=LEVEL_1, priority=1)             │
│     // - AlertRule(id=2, level=LEVEL_2, priority=2)             │
│     // 按等级从低到高排序                                        │
│                                                                  │
│     // 2️⃣ 只为最低等级规则创建评估任务                            │
│     AlertRule lowestRule = allRules.get(0);                     │
│     // lowestRule = AlertRule(id=1, level=LEVEL_1)              │
│                                                                  │
│     // 3️⃣ 创建评估任务                                           │
│     createEvaluationTask(event, lowestRule);                    │
│ }                                                                │
└─────────────────────────────────────────────────────────────────┘

  ↓

【AlertEscalationService.createEvaluationTask() 执行】
┌──────────────────────────────────────────────────────────────────┐
│ public void createEvaluationTask(ExceptionEvent event,           │
│                                  AlertRule rule) {               │
│     // event.id = 100                                            │
│     // rule.id = 1, rule.level = LEVEL_1                        │
│                                                                   │
│     // 1️⃣ 计算下次评估时间                                       │
│     TriggerStrategy strategy = triggerStrategyFactory            │
│         .createStrategy(rule.getTriggerCondition());             │
│     // TriggerCondition: absolute_time = "08:30"                │
│     // (假设规则配置为：检测到异常后 30 分钟评估)                 │
│                                                                   │
│     LocalDateTime nextEvaluationTime = strategy                  │
│         .calculateNextEvaluationTime(...);                       │
│     // nextEvaluationTime = 08:30 (30分钟后)                    │
│                                                                   │
│     // 2️⃣ 构造任务数据                                            │
│     Map<String, Object> taskData = new HashMap<>();             │
│     taskData.put("exceptionEventId", 100L);                     │
│     taskData.put("alertRuleId", 1L);                            │
│     taskData.put("evaluationType", "ALERT_EVALUATION");         │
│                                                                   │
│     // 3️⃣ 创建定时任务                                           │
│     String taskId = taskManagementService.createOnceTask(       │
│         "报警评估-异常[100]-规则[1]",                            │
│         ScheduledTask.TaskType.ALERT,                           │
│         LocalDateTime.of(2025,12,12,8,30),                      │
│         taskData,                                                │
│         1,           // retryCount                               │
│         1,           // priority                                 │
│         30L          // timeout                                  │
│     );                                                           │
│     // 返回 taskId，假设为 "task_001"                           │
│                                                                   │
│     // 4️⃣ 记录待机任务映射（用于后续取消）                        │
│     recordPendingTask(100L, "task_001");                        │
│     // PENDING_TASK_MAP.put(100L, ["task_001"])                 │
│ }                                                                │
└──────────────────────────────────────────────────────────────────┘

  ↓

【数据库第二次更新】
scheduled_task 表中插入新任务：
┌──────────┬──────────────────────┬─────────────┬──────────────┬─────┐
│ id       │ type                 │ scheduled_at│ status       │ ...│
├──────────┼──────────────────────┼─────────────┼──────────────┼─────┤
│ task_001 │ ALERT                │ 08:30:00   │ PENDING      │ ... │
│          │                      │              │              │     │
│          │ taskData: {          │              │              │     │
│          │   exceptionEventId:100│              │              │     │
│          │   alertRuleId: 1     │              │              │     │
│          │ }                    │              │              │     │
└──────────┴──────────────────────┴─────────────┴──────────────┴─────┘

================================================================================
        【第三幕】08:30 - 任务触发和 LEVEL_1 报警评估
================================================================================

【定时任务执行】
08:30:00 到达，任务调度系统执行 task_001
  ↓

【AlertExecutor.execute() 被调用】
┌───────────────────────────────────────────────────────────────────┐
│ public void execute(ScheduledTask task) throws Exception {        │
│     Map<String, Object> taskData = task.getTaskData();            │
│     // {exceptionEventId: 100, alertRuleId: 1}                    │
│                                                                    │
│     Long exceptionEventId = 100L;                                 │
│     Long alertRuleId = 1L;                                        │
│                                                                    │
│     log.info("开始执行报警评估任务: 异常[{}] 规则[{}]",            │
│              exceptionEventId, alertRuleId);                      │
│                                                                    │
│     // 1️⃣ 查询异常事件                                             │
│     ExceptionEvent event = exceptionEventRepository.selectById    │
│         (100L);                                                   │
│     // 查询结果：                                                   │
│     // id=100, status=ACTIVE, current_alert_level=NONE           │
│                                                                    │
│     // 2️⃣ 查询报警规则                                             │
│     AlertRule rule = alertRuleRepository.selectById(1L);          │
│     // rule: id=1, level=LEVEL_1, actionType=LOG                 │
│                                                                    │
│     // 3️⃣ 查询异常类型（获取检测配置）                             │
│     ExceptionType exceptionType = exceptionTypeRepository         │
│         .selectById(1L);                                          │
│     // detection_logic_type=RECORD_CHECK                          │
│     // detection_config={tableName: work_log, duration: 3h}      │
│                                                                    │
│     // 4️⃣ 执行业务检测（验证异常是否仍然存在）                     │
│     if (!isExceptionStillActive(exceptionType, event)) {          │
│         log.info("异常事件 [{}] 当前未满足业务检测逻辑，跳过");     │
│         return; // 异常已恢复，不需要触发报警                      │
│     }                                                              │
│     // 调用 RecordCheckDetector.detect()                          │
│     // 检查 work_log 表中 3 小时内是否有入井记录                   │
│     // 结果：false (确实没有记录)                                   │
│     // 异常仍然存在，继续评估                                       │
│                                                                    │
│     // 5️⃣ 执行时间条件评估                                         │
│     TriggerStrategy strategy = triggerStrategyFactory             │
│         .createStrategy(rule.getTriggerCondition());              │
│     // 触发条件：absolute_time = "08:30" (绝对时间)                │
│                                                                    │
│     if (!strategy.shouldTrigger(...)) {                           │
│         // 时间条件不满足，重新安排下一次评估                      │
│         LocalDateTime nextTime = strategy                         │
│             .calculateNextEvaluationTime(...);                    │
│         // nextTime = 09:00 (下一个评估时刻)                      │
│         // 重新创建任务...                                         │
│         return;                                                    │
│     }                                                              │
│     // 时间条件满足 ✓                                              │
│                                                                    │
│     // 6️⃣ 触发报警动作                                             │
│     executeAlertActions(event, rule);                            │
│     // 调用所有 AlertActionExecutor 实现类的 execute()            │
│     // -> LogAlertAction.execute(): 输出日志                      │
│     // -> EmailAlertAction.execute(): 发送邮件                    │
│     // -> SmsAlertAction.execute(): 发送短信                      │
│                                                                    │
│     // 7️⃣ 更新异常事件状态                                         │
│     event.setCurrentAlertLevel("LEVEL_1");                        │
│     event.setLastEscalatedAt(LocalDateTime.now()); // 08:30       │
│     exceptionEventRepository.updateById(event);                   │
│                                                                    │
│     // 8️⃣ 记录报警日志                                             │
│     alertEscalationService.logAlertEvent(event, rule,             │
│         "业务检测通过，时间条件满足");                              │
│     // 在 alert_event_log 表中插入：                               │
│     // {exception_event_id: 100, alert_rule_id: 1, triggered_at:  │
│     //  08:30, alert_level: LEVEL_1, event_type: ALERT_TRIGGERED}│
│                                                                    │
│     // 9️⃣ 检查是否需要升级到下一个等级                             │
│     alertEscalationService.scheduleNextLevelEvaluation            │
│         (event, rule);                                            │
│ }                                                                  │
└───────────────────────────────────────────────────────────────────┘

  ↓

【数据库第三次更新】
exception_event 表：
┌────┬──────────┬───────────────────┬─────────────────────────┐
│ id │ status   │ current_alert_    │ detection_context       │
│    │          │ level             │                         │
├────┼──────────┼───────────────────┼─────────────────────────┤
│100 │ ACTIVE   │ LEVEL_1           │ {} (仍然为空)           │
│    │          │ ← 从 NONE 更新    │                         │
└────┴──────────┴───────────────────┴─────────────────────────┘

alert_event_log 表：
┌────┬──────────────────┬──────────────────┬─────────────┬────────────┐
│ id │ exception_event_ │ alert_rule_id    │ triggered_  │ event_type │
│    │ id               │                  │ at          │            │
├────┼──────────────────┼──────────────────┼─────────────┼────────────┤
│ 1  │ 100              │ 1                │ 08:30:00   │ALERT_      │
│    │                  │                  │            │TRIGGERED   │
└────┴──────────────────┴──────────────────┴─────────────┴────────────┘

================================================================================
        【第四幕】08:30 - 检查升级和记录待机状态
================================================================================

【AlertEscalationService.scheduleNextLevelEvaluation() 执行】
┌──────────────────────────────────────────────────────────────────┐
│ public void scheduleNextLevelEvaluation(ExceptionEvent event,    │
│                                          AlertRule triggeredRule) │
│ {                                                                  │
│     // event.id = 100                                             │
│     // triggeredRule.id = 1, triggeredRule.level = LEVEL_1        │
│                                                                    │
│     log.info("为异常事件 [{}] 的下一等级创建评估任务，当前等级: {}",│
│              100, "LEVEL_1");                                     │
│                                                                    │
│     // 1️⃣ 查询该异常类型的所有规则                                 │
│     List<AlertRule> allRules = alertRuleRepository                │
│         .findEnabledRulesByExceptionType(1L);                     │
│     // 返回：[AlertRule(id=1, LEVEL_1), AlertRule(id=2, LEVEL_2)] │
│                                                                    │
│     // 2️⃣ 筛选出等级更高的规则                                     │
│     List<AlertRule> higherRules = allRules.stream()               │
│         .filter(rule -> isHigherLevel(rule.getLevel(), "LEVEL_1"))│
│         .toList();                                                 │
│     // 返回：[AlertRule(id=2, LEVEL_2)]                           │
│                                                                    │
│     // 3️⃣ 检查下一级是否有依赖事件                                 │
│     AlertRule nextRule = higherRules.get(0); // rule.id=2         │
│     // nextRule.dependentEvents = {                               │
│     //   "events": [{                                             │
│     //     "eventType": "FIRST_BOREHOLE_START",                   │
│     //     "delayMinutes": 120,                                   │
│     //     "required": true                                       │
│     //   }],                                                      │
│     //   "logicalOperator": "AND"                                 │
│     // }                                                           │
│                                                                    │
│     // 4️⃣ 有依赖事件，检查依赖是否已满足                            │
│     // 检查 detection_context 中是否有 FIRST_BOREHOLE_START_time  │
│     if (event.getDetectionContext() == null ||                    │
│         event.getDetectionContext().get(                          │
│            "FIRST_BOREHOLE_START_time") == null) {                │
│         // 依赖未发生，不能直接升级                                 │
│         log.info("LEVEL_2 依赖事件尚未发生，进入待机状态");         │
│                                                                    │
│         // 5️⃣ 记录待机状态到 pending_escalations                   │
│         if (event.getPendingEscalations() == null) {              │
│             event.setPendingEscalations(new HashMap<>());        │
│         }                                                          │
│                                                                    │
│         Map<String, Object> level2Status = new HashMap<>();       │
│         level2Status.put("status", "WAITING");                    │
│         level2Status.put("dependencies", nextRule                 │
│             .getDependentEvents().get("events"));                 │
│         level2Status.put("logicalOperator",                       │
│             nextRule.getDependentEvents().get("logicalOperator"));│
│         level2Status.put("createdAt", "08:30:00");                │
│                                                                    │
│         event.getPendingEscalations().put("LEVEL_2", level2Status);│
│         exceptionEventRepository.updateById(event);               │
│                                                                    │
│         // 发布 Spring Event（供监听者使用）                       │
│         // 注：此时没有外部事件，所以这里只是记录状态              │
│     } else {                                                       │
│         // 依赖已满足，直接创建评估任务                            │
│         createEvaluationTask(event, nextRule);                    │
│     }                                                              │
│ }                                                                  │
└──────────────────────────────────────────────────────────────────┘

  ↓

【数据库第四次更新】
exception_event 表的 pending_escalations 字段：
┌────┬──────────────────────────────────────────────────────────────┐
│id  │ pending_escalations (JSON)                                   │
├────┼──────────────────────────────────────────────────────────────┤
│100 │ {                                                            │
│    │   "LEVEL_2": {                                               │
│    │     "status": "WAITING",                                     │
│    │     "dependencies": [                                        │
│    │       {                                                      │
│    │         "eventType": "FIRST_BOREHOLE_START",                │
│    │         "delayMinutes": 120,                                │
│    │         "required": true                                    │
│    │       }                                                      │
│    │     ],                                                       │
│    │     "logicalOperator": "AND",                                │
│    │     "createdAt": "2025-12-12T08:30:00"                      │
│    │   }                                                          │
│    │ }                                                            │
└────┴──────────────────────────────────────────────────────────────┘

现在系统处于：
  ✓ LEVEL_1 已触发
  ⏳ LEVEL_2 在等待 FIRST_BOREHOLE_START 事件

================================================================================
        【第五幕】10:00 - 外部事件发生，触发依赖升级
================================================================================

【业务事件发生】
钻孔系统检测到：第一个钻孔开始
  ↓

【代码调用链】
1. 钻孔系统在 10:00 发布事件：
   ┌──────────────────────────────────────────────────────┐
   │ // BoreholService.java                               │
   │ public void startFirstBorehole() {                   │
   │     // ... 业务逻辑 ...                               │
   │                                                       │
   │     // 发布事件                                        │
   │     BoreholStartEvent event = new BoreholStartEvent( │
   │         this,                                        │
   │         100L,  // exceptionEventId (如果有的话)      │
   │         "FIRST_BOREHOLE_START"                       │
   │     );                                               │
   │     applicationEventPublisher.publishEvent(event);   │
   │     log.info("已发布钻孔开始事件");                     │
   │ }                                                     │
   └──────────────────────────────────────────────────────┘

  ↓

【Spring Event Bus 广播】
ApplicationEventPublisher 广播 BoreholStartEvent
  ↓ (所有 @EventListener 都会收到)

【AlertDependencyManager.onAlertSystemEvent() 被触发】
┌───────────────────────────────────────────────────────────────┐
│ @EventListener                                                │
│ @Transactional                                                │
│ public void onAlertSystemEvent(AlertSystemEvent event) {      │
│     // event 是 BoreholStartEvent                             │
│     // event.getEventType() = "FIRST_BOREHOLE_START"           │
│     // event.getExceptionEventId() = 100 (如果指定的话)        │
│                                                                │
│     log.info("监听到告警系统事件: eventType={}, exceptionEventId={}",│
│              "FIRST_BOREHOLE_START", event.getExceptionEventId());│
│                                                                │
│     // 1️⃣ 记录事件时间到 detection_context                    │
│     recordEventToContext(event);                              │
│                                                                │
│     // 2️⃣ 检查所有待机的升级任务                               │
│     checkAndTriggerPendingEscalations(event);                 │
│ }                                                              │
└───────────────────────────────────────────────────────────────┘

  ↓

【AlertDependencyManager.recordEventToContext() 执行】
┌────────────────────────────────────────────────────────────────┐
│ private void recordEventToContext(AlertSystemEvent event) {    │
│     // 1️⃣ 查询所有 ACTIVE 异常                                │
│     // (为了简单起见，这里只处理特定的 exceptionEventId)      │
│                                                                 │
│     // 2️⃣ 更新该异常的 detection_context                      │
│     ExceptionEvent exceptionEvent = exceptionEventRepository   │
│         .selectById(100L);                                     │
│                                                                 │
│     if (exceptionEvent != null) {                              │
│         // 初始化或获取现有的 detection_context                │
│         if (exceptionEvent.getDetectionContext() == null) {    │
│             exceptionEvent.setDetectionContext(new HashMap<>());│
│         }                                                       │
│                                                                 │
│         // 记录事件发生时间                                     │
│         exceptionEvent.getDetectionContext().put(              │
│             "FIRST_BOREHOLE_START_time",                       │
│             LocalDateTime.now().toString()                     │
│             // "2025-12-12T10:00:00"                           │
│         );                                                      │
│                                                                 │
│         exceptionEventRepository.updateById(exceptionEvent);   │
│         log.info("已更新事件上下文");                            │
│     }                                                           │
│ }                                                              │
└────────────────────────────────────────────────────────────────┘

  ↓ 继续同一个方法

【AlertDependencyManager.checkAndTriggerPendingEscalations() 执行】
┌──────────────────────────────────────────────────────────────────┐
│ private void checkAndTriggerPendingEscalations(...) {             │
│     // 1️⃣ 查询所有 ACTIVE 异常                                   │
│     List<ExceptionEvent> activeEvents = exceptionEventRepository  │
│         .selectList(                                              │
│             new LambdaQueryWrapper<ExceptionEvent>()             │
│                 .eq(ExceptionEvent::getStatus, "ACTIVE")         │
│                 .isNotNull(ExceptionEvent::getPendingEscalations)│
│         );                                                        │
│     // 返回：[ExceptionEvent(id=100, status=ACTIVE, ...)]        │
│                                                                   │
│     // 2️⃣ 对每个异常检查待机升级                                 │
│     for (ExceptionEvent exceptionEvent : activeEvents) {         │
│         checkPendingEscalationsForEvent(exceptionEvent, event);  │
│         // exceptionEvent.id = 100                               │
│         // event = BoreholStartEvent                             │
│     }                                                            │
│ }                                                                │
└──────────────────────────────────────────────────────────────────┘

  ↓

【AlertDependencyManager.checkPendingEscalationsForEvent() 执行】
┌──────────────────────────────────────────────────────────────────┐
│ private void checkPendingEscalationsForEvent(                    │
│         ExceptionEvent exceptionEvent,                           │
│         AlertSystemEvent triggeringEvent) {                      │
│     // exceptionEvent.id = 100                                   │
│     // exceptionEvent.pending_escalations = {...}                │
│                                                                   │
│     if (exceptionEvent.getPendingEscalations() == null ||        │
│         exceptionEvent.getPendingEscalations().isEmpty()) {      │
│         return; // 没有待机任务                                  │
│     }                                                             │
│                                                                   │
│     // 遍历每个待机的等级                                         │
│     for (Map.Entry<String, Object> entry :                      │
│              exceptionEvent.getPendingEscalations().entrySet()) {│
│         String levelName = entry.getKey(); // "LEVEL_2"         │
│         Object levelData = entry.getValue(); // {...}            │
│                                                                   │
│         Map<String, Object> levelStatus =                       │
│             (Map<String, Object>) levelData;                    │
│         String status = (String) levelStatus.get("status");     │
│         // status = "WAITING"                                    │
│                                                                   │
│         // 只处理 WAITING 状态的升级                              │
│         if (!"WAITING".equals(status)) {                         │
│             continue;                                            │
│         }                                                         │
│                                                                   │
│         // 检查该等级的依赖是否满足                               │
│         if (checkDependenciesSatisfied(levelStatus, exceptionEvent)) {│
│             // ✓ 依赖满足！                                       │
│             log.info("报警升级依赖满足: LEVEL_2");                 │
│                                                                   │
│             // 更新状态为 READY                                   │
│             levelStatus.put("status", "READY");                  │
│             levelStatus.put("readyAt", LocalDateTime.now().toString());│
│             exceptionEventRepository.updateById(exceptionEvent); │
│                                                                   │
│             // 创建 LEVEL_2 的评估任务                            │
│             alertEscalationService.scheduleEscalationEvaluation( │
│                 exceptionEvent.getId(), levelName);              │
│             // 方法调用：scheduleEscalationEvaluation(100, "LEVEL_2")│
│         }                                                         │
│     }                                                             │
│ }                                                                │
└──────────────────────────────────────────────────────────────────┘

  ↓

【关键方法：checkDependenciesSatisfied() 详解】
┌──────────────────────────────────────────────────────────────────┐
│ private boolean checkDependenciesSatisfied(                      │
│         Map<String, Object> levelStatus,                        │
│         ExceptionEvent event) {                                  │
│     // levelStatus = {                                           │
│     //   "status": "WAITING",                                    │
│     //   "dependencies": [{                                      │
│     //     "eventType": "FIRST_BOREHOLE_START",                  │
│     //     "delayMinutes": 120,                                  │
│     //     "required": true                                      │
│     //   }],                                                     │
│     //   "logicalOperator": "AND"                                │
│     // }                                                         │
│     // event.detection_context = {                              │
│     //   "FIRST_BOREHOLE_START_time": "2025-12-12T10:00:00"    │
│     // }                                                         │
│                                                                   │
│     List<Object> dependencies = (List) levelStatus.get(...);     │
│     String logicalOperator = "AND";                              │
│                                                                   │
│     boolean allSatisfied = true;                                 │
│     for (Object depObj : dependencies) {                         │
│         Map<String, Object> dependency =                        │
│             (Map<String, Object>) depObj;                       │
│         // dependency = {                                        │
│         //   "eventType": "FIRST_BOREHOLE_START",                │
│         //   "delayMinutes": 120,                                │
│         //   "required": true                                    │
│         // }                                                     │
│                                                                   │
│         boolean isSatisfied = checkSingleDependency(             │
│             dependency, event);                                  │
│         // 调用 checkSingleDependency...                         │
│     }                                                             │
│     return allSatisfied;  // 返回 true ✓                        │
│ }                                                                │
└──────────────────────────────────────────────────────────────────┘

  ↓

【关键方法：checkSingleDependency() 详解】
┌──────────────────────────────────────────────────────────────────┐
│ private boolean checkSingleDependency(                           │
│         Map<String, Object> dependency,                         │
│         ExceptionEvent event) {                                  │
│     // dependency = {                                            │
│     //   "eventType": "FIRST_BOREHOLE_START",                    │
│     //   "delayMinutes": 120,                                    │
│     //   "required": true                                        │
│     // }                                                         │
│                                                                   │
│     String eventType = "FIRST_BOREHOLE_START";                   │
│     int delayMinutes = 120;                                      │
│     Boolean required = true;                                     │
│                                                                   │
│     // 1️⃣ 检查 detection_context 中是否有该事件记录                │
│     String eventTimeKey = "FIRST_BOREHOLE_START_time";           │
│     Object eventTimeObj = event.getDetectionContext()            │
│         .get(eventTimeKey);                                      │
│     // eventTimeObj = "2025-12-12T10:00:00"                     │
│                                                                   │
│     if (eventTimeObj == null) {                                  │
│         return false; // 事件未发生                              │
│     }                                                             │
│                                                                   │
│     // 2️⃣ 检查时间延迟条件                                        │
│     LocalDateTime eventTime = LocalDateTime.parse(               │
│         (String) eventTimeObj);                                  │
│     // eventTime = 10:00:00                                      │
│                                                                   │
│     LocalDateTime requiredTime = eventTime.plusMinutes(120);     │
│     // requiredTime = 10:00 + 120分钟 = 12:00                   │
│                                                                   │
│     LocalDateTime now = LocalDateTime.now();                     │
│     // now = 10:00:00 (事件刚发生)                                │
│                                                                   │
│     // 3️⃣ 检查是否达到了要求的时间                               │
│     boolean timeConditionMet = now.isAfter(requiredTime) ||      │
│                                 now.isEqual(requiredTime);       │
│     // timeConditionMet = 10:00 >= 12:00 ? false ❌              │
│                                                                   │
│     // 因为 delayMinutes=120，所以：                              │
│     // - 事件在 10:00 发生                                        │
│     // - 但需要等待 120 分钟（到 12:00）才能升级                  │
│     // - 现在是 10:00，条件未满足                                 │
│                                                                   │
│     log.debug("依赖时间检查: eventType={}, eventTime={}, " +     │
│               "requiredTime={}, now={}, satisfied={}",           │
│               eventType, eventTime, requiredTime, now,           │
│               timeConditionMet);                                 │
│                                                                   │
│     return timeConditionMet; // 返回 false                      │
│ }                                                                │
└──────────────────────────────────────────────────────────────────┘

【重要发现】
⚠️ 虽然事件已发生，但由于 delayMinutes=120，条件还未满足！
   系统会继续等待，直到 12:00 时才会触发 LEVEL_2

【数据库第五次更新】
exception_event 表：
┌────┬──────────────────────────────────────────────────┐
│id  │ detection_context (JSON)                         │
├────┼──────────────────────────────────────────────────┤
│100 │ {                                                 │
│    │   "FIRST_BOREHOLE_START_time":                    │
│    │   "2025-12-12T10:00:00"  ← 事件时间已记录        │
│    │ }                                                 │
└────┴──────────────────────────────────────────────────┘

exception_event 表的 pending_escalations：
┌────┬──────────────────────────────────────────────┐
│id  │ pending_escalations (JSON)                   │
├────┼──────────────────────────────────────────────┤
│100 │ {                                             │
│    │   "LEVEL_2": {                                │
│    │     "status": "READY",  ← 已更新为待就绪      │
│    │     "readyAt": "2025-12-12T10:00:00"         │
│    │     ...                                      │
│    │   }                                          │
│    │ }                                            │
└────┴──────────────────────────────────────────────┘

================================================================================
        【第六幕】10:15 - 用户手动解除报警
================================================================================

【用户操作】
前端用户点击"解除报警"按钮
  ↓

【REST API 调用】
POST /api/alert/resolution/manual-resolve
Parameters:
  exceptionEventId: 100
  reason: "已确认异常已解决"

  ↓

【AlertResolutionController.manualResolveAlert() 被调用】
┌──────────────────────────────────────────────────────────────┐
│ @PostMapping("/manual-resolve")                              │
│ public ApiResponse<?> manualResolveAlert(                    │
│         @RequestParam Long exceptionEventId,                 │
│         @RequestParam String reason) {                       │
│                                                               │
│     log.info("执行手动报警解除: exceptionEventId={}, reason={}",│
│              100, "已确认异常已解决");                         │
│                                                               │
│     boolean success = alertResolutionService                 │
│         .manualResolveAlert(100L, "已确认异常已解决");         │
│     return ApiResponse.success("报警已手动解除", 100L);       │
│ }                                                             │
└──────────────────────────────────────────────────────────────┘

  ↓

【AlertResolutionService.manualResolveAlert() 调用】
┌──────────────────────────────────────────────────────────────┐
│ public boolean manualResolveAlert(Long exceptionEventId,     │
│                                    String resolutionReason) {│
│     log.info("执行手动报警解除: exceptionEventId={}", 100);    │
│     return resolveAlert(exceptionEventId,                    │
│         ResolutionSource.MANUAL_RESOLUTION, resolutionReason);│
│ }                                                             │
└──────────────────────────────────────────────────────────────┘

  ↓

【AlertResolutionService.resolveAlert() 详解 - 核心方法】
┌──────────────────────────────────────────────────────────────────┐
│ @Transactional(rollbackFor = Exception.class)                    │
│ public boolean resolveAlert(Long exceptionEventId,               │
│                    ResolutionSource resolutionSource,            │
│                    String resolutionReason) {                    │
│     log.info("开始解除报警: exceptionEventId={}, source={}, " +  │
│              "reason={}", 100, "MANUAL_RESOLUTION",              │
│              "已确认异常已解决");                                  │
│                                                                   │
│     // 1️⃣ 查询异常事件                                             │
│     ExceptionEvent exceptionEvent = exceptionEventRepository     │
│         .selectById(100L);                                       │
│     // 查询结果：                                                   │
│     // status=ACTIVE, current_alert_level=LEVEL_1,               │
│     // pending_escalations={LEVEL_2: {status: READY, ...}}       │
│                                                                   │
│     if (exceptionEvent == null) {                                │
│         log.warn("异常事件不存在: exceptionEventId={}", 100);      │
│         return false;                                            │
│     }                                                             │
│                                                                   │
│     // 2️⃣ 检查当前状态                                             │
│     if ("RESOLVED".equals(exceptionEvent.getStatus())) {         │
│         log.warn("异常事件已处于RESOLVED状态，无需重复解除");      │
│         return true;                                             │
│     }                                                             │
│                                                                   │
│     // 3️⃣ 转换为 RESOLVING 状态（防止中途崩溃）                   │
│     exceptionEvent.setStatus("RESOLVING");                       │
│     exceptionEventRepository.updateById(exceptionEvent);         │
│     log.info("异常事件状态转换为RESOLVING: exceptionEventId={}",  │
│              100);                                               │
│                                                                   │
│     // 4️⃣ 查询并取消所有待机任务                                   │
│     int cancelledTaskCount = cancelAllPendingTasks(100L);        │
│     // 执行 cancelAllPendingTasks()                               │
│                                                                   │
│     // 5️⃣ 记录解除事件日志                                         │
│     recordResolutionLog(100L,                                    │
│         ResolutionSource.MANUAL_RESOLUTION,                      │
│         "已确认异常已解决");                                       │
│                                                                   │
│     // 6️⃣ 最终转换为 RESOLVED 状态                                │
│     exceptionEvent.setStatus("RESOLVED");                        │
│     exceptionEvent.setResolvedAt(LocalDateTime.now()); // 10:15  │
│     exceptionEvent.setResolutionReason("已确认异常已解决");       │
│     exceptionEvent.setResolutionSource("MANUAL_RESOLUTION");     │
│     exceptionEventRepository.updateById(exceptionEvent);         │
│     log.info("异常事件状态转换为RESOLVED: exceptionEventId={}", 100);│
│                                                                   │
│     // 7️⃣ 发布报警解除事件                                         │
│     eventPublisher.publishEvent(new AlertResolutionEvent(        │
│         this,                                                    │
│         100L,                                                    │
│         ResolutionSource.MANUAL_RESOLUTION,                      │
│         "已确认异常已解决"                                         │
│     ));                                                           │
│     log.info("报警解除完成: exceptionEventId={}", 100);            │
│     return true;                                                 │
│ }                                                                │
└──────────────────────────────────────────────────────────────────┘

  ↓

【AlertResolutionService.cancelAllPendingTasks() 详解】
┌──────────────────────────────────────────────────────────────────┐
│ private int cancelAllPendingTasks(Long exceptionEventId) {        │
│     try {                                                         │
│         // 1️⃣ 从映射中获取所有待机任务 ID                           │
│         List<String> pendingTaskIds =                            │
│             alertEscalationService.getPendingTasks(100L);         │
│         // 返回：["task_001"]                                     │
│         // (LEVEL_2 的评估任务还没有创建，因为依赖延迟未满足)      │
│                                                                   │
│         int cancelledCount = 0;                                  │
│         for (String taskId : pendingTaskIds) {                   │
│             try {                                                │
│                 // 2️⃣ 取消任务                                     │
│                 taskManagementService.cancelTask(                │
│                     Long.parseLong(taskId));                     │
│                 // 调用：taskManagementService.cancelTask("task_001")│
│                 // 如果该任务尚未执行，则取消成功                  │
│                                                                   │
│                 log.info("已取消任务: exceptionEventId={}, " +    │
│                          "taskId={}", 100, "task_001");          │
│                 cancelledCount++;                                │
│                                                                   │
│                 // 3️⃣ 记录任务取消日志                             │
│                 recordTaskCancelledLog(100L, "task_001");        │
│             } catch (Exception e) {                              │
│                 log.error("取消任务失败: exceptionEventId={}, " + │
│                           "taskId={}", 100, "task_001", e);      │
│             }                                                     │
│         }                                                         │
│                                                                   │
│         // 4️⃣ 清除待机任务记录                                     │
│         alertEscalationService.clearPendingTasks(100L);           │
│         // PENDING_TASK_MAP.remove(100L)                         │
│                                                                   │
│         log.info("待机任务取消完成: exceptionEventId={}, " +       │
│                  "cancelledCount={}", 100, 1);                   │
│         return 1;  // 取消了 1 个任务                             │
│     } catch (Exception e) {                                      │
│         log.error("取消待机任务时出现异常: exceptionEventId={}", 100, e);│
│         throw new RuntimeException("取消待机任务失败", e);         │
│     }                                                             │
│ }                                                                │
└──────────────────────────────────────────────────────────────────┘

【数据库第六次更新】
exception_event 表 - 最终状态：
┌────┬────────┬────────────────────┬──────────────────┬──────────────┐
│id  │ status │ current_alert_level│ resolved_at      │ resolution_  │
│    │        │                    │                  │ reason       │
├────┼────────┼────────────────────┼──────────────────┼──────────────┤
│100 │RESOLVED│ LEVEL_1            │ 2025-12-12       │ 已确认异常    │
│    │        │                    │ 10:15:00         │ 已解决        │
│    │        │                    │                  │              │
│    │resolution_source│ recovery_flag│ pending_escalations          │
├────┼─────────────────┼──────────┼──────────────────────────────┤
│    │ MANUAL_RESOLUTION│ false    │ {} (已清空)                  │
└────┴─────────────────┴──────────┴──────────────────────────────┘

alert_event_log 表 - 新增解除记录：
┌────┬──────────┬──────────┬──────────────┬────────────┬─────────────┐
│id  │exception_│alert_rule│triggered_at  │alert_level │event_type   │
│    │event_id  │_id       │              │            │             │
├────┼──────────┼──────────┼──────────────┼────────────┼─────────────┤
│ 1  │ 100      │ 1        │ 08:30:00    │ LEVEL_1    │ALERT_TRIGGERED│
├────┼──────────┼──────────┼──────────────┼────────────┼─────────────┤
│ 2  │ 100      │ null     │ 10:15:00    │ RESOLVED   │ALERT_RESOLVED│ ← 新增
│    │          │          │              │            │             │
│ trigger_reason (ALERT_RESOLVED):                                    │
│ "解除原因: 已确认异常已解决 (手动解除)"                             │
└────┴──────────┴──────────┴──────────────┴────────────┴─────────────┘

scheduled_task 表 - task_001 状态：
┌──────────┬────────┬──────────────┐
│ id       │ status │ updated_at   │
├──────────┼────────┼──────────────┤
│ task_001 │CANCELLED│ 10:15:00    │ ← 已取消
└──────────┴────────┴──────────────┘

================================================================================
        【完整流程总结】
================================================================================

时间线回顾：
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

08:00 异常检测
    ↓
    boreholeService.detectAndCreateAlert()
    → exceptionEventRepository.insert() 
    → alertEscalationService.scheduleInitialEvaluation()
    
08:00 创建初始任务
    ↓
    createEvaluationTask(LEVEL_1)
    → taskManagementService.createOnceTask("task_001", 08:30)
    → recordPendingTask(100, "task_001")

08:30 LEVEL_1 触发
    ↓
    alertExecutor.execute()
    → isExceptionStillActive() ✓
    → strategy.shouldTrigger() ✓
    → executeAlertActions() [LOG, EMAIL, SMS]
    → logAlertEvent()
    → scheduleNextLevelEvaluation()

08:30 检查 LEVEL_2 升级
    ↓
    发现有依赖事件
    → detection_context 中没有 FIRST_BOREHOLE_START_time
    → 记录待机状态：pending_escalations[LEVEL_2] = WAITING

10:00 钻孔事件发生
    ↓
    boreholService.startFirstBorehole()
    → applicationEventPublisher.publishEvent(BoreholStartEvent)

10:00 Spring 事件处理
    ↓
    AlertDependencyManager.onAlertSystemEvent()
    → recordEventToContext() [更新 detection_context]
    → checkAndTriggerPendingEscalations()
    → checkDependenciesSatisfied() ❌ (延迟 120 分钟未满足)
    → 继续等待...

10:15 用户解除报警
    ↓
    AlertResolutionController.manualResolveAlert()
    → alertResolutionService.resolveAlert()

10:15 解除流程（原子操作）
    ↓
    1. ACTIVE → RESOLVING (防护)
    2. cancelAllPendingTasks() [取消 task_001]
    3. recordTaskCancelledLog()
    4. RESOLVING → RESOLVED
    5. 更新 resolved_at, resolution_reason, resolution_source
    6. publishEvent(AlertResolutionEvent)
    7. 记录解除事件日志

最终状态：
    ✓ ExceptionEvent: RESOLVED
    ✓ PendingEscalations: 已清空
    ✓ LEVEL_1 报警已触发（记录保留）
    ✓ LEVEL_2 升级被中止
    ✓ 所有任务已取消
    ✓ 完整的审计日志记录

================================================================================
