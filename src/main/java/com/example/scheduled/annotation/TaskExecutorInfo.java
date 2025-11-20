package com.example.scheduled.annotation;

import com.example.scheduled.entity.ScheduledTask;

import java.lang.annotation.*;

/**
 * 任务执行器元信息注解
 * 用于声明执行器支持的任务类型及其展示信息
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface TaskExecutorInfo {
    
    /**
     * 支持的任务类型
     */
    ScheduledTask.TaskType taskType();
    
    /**
     * 显示名称（本地化）
     */
    String displayName();
    
    /**
     * 任务描述
     */
    String description() default "";
    
    /**
     * 执行器优先级（同一类型多个执行器时使用）
     */
    int priority() default 0;
}
