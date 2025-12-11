package com.example.scheduled.alert.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.scheduled.alert.entity.ExceptionEvent;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 异常事件数据仓储
 */
@Mapper
public interface ExceptionEventRepository extends BaseMapper<ExceptionEvent> {

    /**
     * 查询所有活跃的异常事件
     */
    @Select("SELECT * FROM exception_event WHERE status = 'ACTIVE' ORDER BY detected_at DESC")
    List<ExceptionEvent> findActiveEvents();

    /**
     * 根据异常类型查询活跃事件
     */
    @Select("SELECT * FROM exception_event WHERE exception_type_id = #{exceptionTypeId} AND status = 'ACTIVE' " +
            "ORDER BY detected_at DESC")
    List<ExceptionEvent> findActiveEventsByExceptionType(Long exceptionTypeId);
}
