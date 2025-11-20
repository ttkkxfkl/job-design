package com.example.scheduled.repository;

import com.example.scheduled.entity.ScheduledTask;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 定时任务数据访问层
 */
@Mapper
public interface ScheduledTaskRepository extends BaseMapper<ScheduledTask> {

    // 复杂查询建议用 XML 或 Wrapper 实现

}
