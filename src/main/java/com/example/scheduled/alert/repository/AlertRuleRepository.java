package com.example.scheduled.alert.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.scheduled.alert.entity.AlertRule;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 报警规则数据仓储
 */
@SuppressWarnings("all")
@Mapper
public interface AlertRuleRepository extends BaseMapper<AlertRule> {

    /**
     * 根据异常类型ID查询所有启用的规则（未排序）
     * 
     * 注意：此方法返回未排序的规则列表。排序应在应用层进行，以便灵活配置等级优先级。
     * 参考用法：
     * <pre>
     * List<AlertRule> rules = repository.findEnabledRulesByExceptionType(typeId);
     * List<AlertRule> sorted = rules.stream()
     *     .sorted(Comparator.comparingInt(r -> AlertConstants.AlertLevels.getPriority(r.getLevel()))
     *                      .thenComparingLong(AlertRule::getId))
     *     .collect(Collectors.toList());
     * </pre>
     * 
     * 为避免代码重复，建议在 AlertRuleService 中提供专门方法
     */
    @Select("SELECT * FROM alert_rule WHERE exception_type_id = #{exceptionTypeId} AND enabled = true ORDER BY id ASC")
    List<AlertRule> findEnabledRulesByExceptionType(Long exceptionTypeId);

    /**
     * 根据异常类型ID和等级查询规则
     */
    @Select("SELECT * FROM alert_rule WHERE exception_type_id = #{exceptionTypeId} AND level = #{level} LIMIT 1")
    AlertRule findByExceptionTypeIdAndLevel(Long exceptionTypeId, String level);
}
