package com.example.scheduled.alert.detection;

import java.util.Map;

/**
 * 异常检测策略接口
 */
public interface ExceptionDetectionStrategy {

    /**
     * 判断是否存在异常
     * @param config 检测配置
     * @param context 检测上下文信息
     * @return true 表示检测到异常
     */
    boolean detect(Map<String, Object> config, Map<String, Object> context);

    /**
     * 获取策略名称
     */
    String getStrategyName();
}
