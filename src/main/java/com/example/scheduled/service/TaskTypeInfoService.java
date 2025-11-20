package com.example.scheduled.service;

import com.example.scheduled.annotation.TaskExecutorInfo;
import com.example.scheduled.dto.TaskTypeInfo;
import com.example.scheduled.entity.ScheduledTask;
import com.example.scheduled.executor.TaskExecutor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 任务类型信息服务
 * 从所有注册的 TaskExecutor Bean 中收集 @TaskExecutorInfo 注解信息
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TaskTypeInfoService {

    private final List<TaskExecutor> executors;
    
    // 缓存任务类型信息，key 为 TaskType，value 为 TaskTypeInfo
    private final Map<ScheduledTask.TaskType, TaskTypeInfo> taskTypeInfoMap = new HashMap<>();

    /**
     * 初始化时扫描所有执行器的注解
     */
    @PostConstruct
    public void init() {
        log.info("开始扫描任务执行器注解信息...");
        
        for (TaskExecutor executor : executors) {
            TaskExecutorInfo annotation = executor.getClass().getAnnotation(TaskExecutorInfo.class);
            
            if (annotation != null) {
                ScheduledTask.TaskType taskType = annotation.taskType();
                TaskTypeInfo info = new TaskTypeInfo(
                    taskType.name(),
                    annotation.displayName(),
                    annotation.description()
                );
                
                // 如果同一类型已经存在，根据优先级决定是否覆盖
                TaskTypeInfo existing = taskTypeInfoMap.get(taskType);
                if (existing == null) {
                    taskTypeInfoMap.put(taskType, info);
                    log.info("注册任务类型：{} -> {} ({})", 
                        taskType, annotation.displayName(), executor.getClass().getSimpleName());
                } else {
                    log.warn("任务类型 {} 已存在，跳过重复注册：{}", 
                        taskType, executor.getClass().getSimpleName());
                }
            } else {
                log.warn("执行器 {} 未标注 @TaskExecutorInfo 注解", 
                    executor.getClass().getSimpleName());
            }
        }
        
        log.info("任务执行器注解扫描完成，共注册 {} 种任务类型", taskTypeInfoMap.size());
    }

    /**
     * 获取所有任务类型信息
     */
    public List<TaskTypeInfo> getAllTaskTypes() {
        return taskTypeInfoMap.values().stream()
            .sorted(Comparator.comparing(TaskTypeInfo::getCode))
            .collect(Collectors.toList());
    }

    /**
     * 根据任务类型获取展示信息
     */
    public TaskTypeInfo getTaskTypeInfo(ScheduledTask.TaskType taskType) {
        return taskTypeInfoMap.get(taskType);
    }

    /**
     * 检查某个任务类型是否已注册
     */
    public boolean isTaskTypeRegistered(ScheduledTask.TaskType taskType) {
        return taskTypeInfoMap.containsKey(taskType);
    }
}
