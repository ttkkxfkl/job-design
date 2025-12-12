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

    /**
     * 根据业务ID查询异常事件
     */
    @Select("SELECT * FROM exception_event WHERE business_id = #{businessId} ORDER BY detected_at DESC")
    List<ExceptionEvent> findByBusinessId(String businessId);

    /**
     * 根据业务ID和业务类型查询活跃异常事件
     */
    @Select("SELECT * FROM exception_event WHERE business_id = #{businessId} AND business_type = #{businessType} " +
            "AND status = 'ACTIVE' ORDER BY detected_at DESC")
    List<ExceptionEvent> findActiveEventsByBusinessIdAndType(String businessId, String businessType);
}
