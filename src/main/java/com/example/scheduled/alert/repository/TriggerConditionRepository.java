package com.example.scheduled.alert.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.scheduled.alert.entity.TriggerCondition;
import org.apache.ibatis.annotations.Mapper;

/**
 * 触发条件数据仓储
 */
@Mapper
public interface TriggerConditionRepository extends BaseMapper<TriggerCondition> {

}
