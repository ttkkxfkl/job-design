package com.example.scheduled.alert.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.example.scheduled.config.JsonTypeHandler;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 异常类型表 - 定义不同的异常类型及其检测逻辑
 */
@TableName(value = "exception_type", autoResultMap = true)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExceptionType {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String name;

    private String description;

    /**
     * 检测逻辑类型：RECORD_CHECK、TIME_CHECK、CUSTOM 等
     */
    private String detectionLogicType;

    /**
     * 检测配置（JSON格式）
     */
    @TableField(value = "detection_config", typeHandler = JsonTypeHandler.class)
    private Map<String, Object> detectionConfig;

    private Boolean enabled;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
