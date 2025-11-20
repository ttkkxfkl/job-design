package com.example.scheduled.lock.impl;

import com.example.scheduled.lock.DistributedLock;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * Redis分布式锁实现（集群模式）
 * 需要在配置文件中设置：scheduled.task.lock.type=redis
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "scheduled.task.lock.type", havingValue = "redis")
public class RedisDistributedLock implements DistributedLock {

    private final StringRedisTemplate redisTemplate;

    public RedisDistributedLock(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public boolean tryLock(String lockKey, long expireSeconds) {
        String fullKey = "schedule:lock:" + lockKey;
        Boolean success = redisTemplate.opsForValue()
                .setIfAbsent(fullKey, "LOCKED", expireSeconds, TimeUnit.SECONDS);
        log.debug("尝试获取Redis锁：{}, 结果：{}", fullKey, success);
        return Boolean.TRUE.equals(success);
    }

    @Override
    public void unlock(String lockKey) {
        String fullKey = "schedule:lock:" + lockKey;
        redisTemplate.delete(fullKey);
        log.debug("释放Redis锁：{}", fullKey);
    }
}
