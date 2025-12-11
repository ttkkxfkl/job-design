package com.example.scheduled.alert.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.scheduled.alert.entity.ExceptionType;
import org.apache.ibatis.annotations.Mapper;

/**
 * 异常类型数据仓储
 */
@Mapper
public interface ExceptionTypeRepository extends BaseMapper<ExceptionType> {

}
