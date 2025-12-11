package com.example.scheduled.alert.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.scheduled.alert.entity.AlertRule;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 报警规则数据仓储
 */
@Mapper
public interface AlertRuleRepository extends BaseMapper<AlertRule> {

    /**
     * 根据异常类型ID查询所有启用的规则，按等级从低到高排序
     */
    @Select("SELECT * FROM alert_rule WHERE exception_type_id = #{exceptionTypeId} AND enabled = true " +
            "ORDER BY FIELD(level, 'BLUE', 'YELLOW', 'RED'), id ASC")
    List<AlertRule> findEnabledRulesByExceptionType(Long exceptionTypeId);

    /**
     * 根据异常类型ID和等级查询规则
     */
    @Select("SELECT * FROM alert_rule WHERE exception_type_id = #{exceptionTypeId} AND level = #{level} LIMIT 1")
    AlertRule findByExceptionTypeIdAndLevel(Long exceptionTypeId, String level);
}
