package com.example.scheduled.alert.constant;

/**
 * 报警系统常量类
 * 集中管理所有魔数和字符串常量
 */
public final class AlertConstants {

    private AlertConstants() {
        throw new AssertionError("Cannot instantiate constants class");
    }

    /**
     * 异常事件状态常量
     */
    public static final class ExceptionEventStatus {
        public static final String ACTIVE = "ACTIVE";
        public static final String RESOLVING = "RESOLVING";
        public static final String RESOLVED = "RESOLVED";

        private ExceptionEventStatus() {
            throw new AssertionError("Cannot instantiate constants class");
        }
    }

    /**
     * 报警等级常量
     */
    public static final class AlertLevels {
        public static final String NONE = "NONE";
        public static final String LEVEL_1 = "LEVEL_1";
        public static final String LEVEL_2 = "LEVEL_2";
        public static final String LEVEL_3 = "LEVEL_3";
        public static final String BLUE = "BLUE";
        public static final String YELLOW = "YELLOW";
        public static final String RED = "RED";

        private AlertLevels() {
            throw new AssertionError("Cannot instantiate constants class");
        }

        /**
         * 获取报警等级的数字优先级
         *
         * @param level 报警等级
         * @return 优先级数字，越大优先级越高
         */
        public static int getPriority(String level) {
            return switch (level) {
                case BLUE, LEVEL_1 -> 1;
                case YELLOW, LEVEL_2 -> 2;
                case RED, LEVEL_3 -> 3;
                default -> 0;
            };
        }

        /**
         * 判断 level1 是否高于 level2
         *
         * @param level1 等级1
         * @param level2 等级2
         * @return 如果 level1 优先级更高，返回 true
         */
        public static boolean isHigherThan(String level1, String level2) {
            return getPriority(level1) > getPriority(level2);
        }
    }

    /**
     * 待机升级状态常量
     */
    public static final class PendingEscalationStatus {
        public static final String WAITING = "WAITING";
        public static final String READY = "READY";
        public static final String SCHEDULED = "SCHEDULED";
        public static final String COMPLETED = "COMPLETED";

        private PendingEscalationStatus() {
            throw new AssertionError("Cannot instantiate constants class");
        }
    }

    /**
     * 报警事件类型常量
     */
    public static final class AlertEventType {
        public static final String ALERT_TRIGGERED = "ALERT_TRIGGERED";
        public static final String ALERT_ESCALATED = "ALERT_ESCALATED";
        public static final String ALERT_RESOLVED = "ALERT_RESOLVED";
        public static final String TASK_CANCELLED = "TASK_CANCELLED";
        public static final String SYSTEM_RECOVERY = "SYSTEM_RECOVERY";
        public static final String ALERT_RECOVERED = "ALERT_RECOVERED";

        private AlertEventType() {
            throw new AssertionError("Cannot instantiate constants class");
        }
    }

    /**
     * 业务类型常量
     */
    public static final class BusinessType {
        public static final String SHIFT = "SHIFT";
        public static final String BOREHOLE = "BOREHOLE";
        public static final String OPERATION = "OPERATION";
        public static final String EQUIPMENT = "EQUIPMENT";
        public static final String WORKER = "WORKER";
        public static final String PROJECT = "PROJECT";

        private BusinessType() {
            throw new AssertionError("Cannot instantiate constants class");
        }
    }

    /**
     * 触发条件类型常量
     */
    public static final class TriggerType {
        public static final String ABSOLUTE = "ABSOLUTE";
        public static final String RELATIVE = "RELATIVE";
        public static final String HYBRID = "HYBRID";

        private TriggerType() {
            throw new AssertionError("Cannot instantiate constants class");
        }
    }

    /**
     * 逻辑操作符常量
     */
    public static final class LogicalOperator {
        public static final String AND = "AND";
        public static final String OR = "OR";

        private LogicalOperator() {
            throw new AssertionError("Cannot instantiate constants class");
        }
    }

    /**
     * 动作状态常量
     */
    public static final class ActionStatus {
        public static final String PENDING = "PENDING";
        public static final String SENT = "SENT";
        public static final String FAILED = "FAILED";
        public static final String COMPLETED = "COMPLETED";

        private ActionStatus() {
            throw new AssertionError("Cannot instantiate constants class");
        }
    }

    /**
     * JSON 字段名常量
     */
    public static final class JsonFields {
        public static final String STATUS = "status";
        public static final String DEPENDENCIES = "dependencies";
        public static final String LOGICAL_OPERATOR = "logicalOperator";
        public static final String EVENT_TYPE = "eventType";
        public static final String DELAY_MINUTES = "delayMinutes";
        public static final String REQUIRED = "required";
        public static final String TASK_ID = "taskId";
        public static final String SCHEDULED_TIME = "scheduledTime";
        public static final String UPDATED_AT = "updatedAt";
        public static final String READY_AT = "readyAt";
        public static final String CREATED_AT = "createdAt";

        private JsonFields() {
            throw new AssertionError("Cannot instantiate constants class");
        }
    }

    /**
     * 时间字段后缀常量
     */
    public static final class TimeFieldSuffix {
        public static final String TIME = "_time";

        private TimeFieldSuffix() {
            throw new AssertionError("Cannot instantiate constants class");
        }
    }

    /**
     * 默认值常量
     */
    public static final class Defaults {
        public static final int DEFAULT_PRIORITY = 5;
        public static final long DEFAULT_EXECUTION_TIMEOUT = 30L;
        public static final int DEFAULT_MAX_RETRY_COUNT = 1;

        private Defaults() {
            throw new AssertionError("Cannot instantiate constants class");
        }
    }
}
