package com.example.scheduled.repository;

import com.example.scheduled.entity.TaskExecutionLog;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 任务执行日志数据访问层
 */
@Mapper
public interface TaskExecutionLogRepository extends BaseMapper<TaskExecutionLog> {

    // 复杂查询建议用 XML 或 Wrapper 实现

}
