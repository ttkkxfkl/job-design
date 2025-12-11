package com.example.scheduled.alert.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.scheduled.alert.entity.AlertEventLog;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 报警事件日志数据仓储
 */
@Mapper
public interface AlertEventLogRepository extends BaseMapper<AlertEventLog> {

    /**
     * 根据异常事件ID查询所有报警日志，按时间倒序
     */
    @Select("SELECT * FROM alert_event_log WHERE exception_event_id = #{exceptionEventId} " +
            "ORDER BY triggered_at DESC")
    List<AlertEventLog> findByExceptionEventId(Long exceptionEventId);

    /**
     * 查询未发送的报警日志（状态为 PENDING）
     */
    @Select("SELECT * FROM alert_event_log WHERE action_status = 'PENDING' LIMIT #{limit}")
    List<AlertEventLog> findPendingLogs(Integer limit);
}
