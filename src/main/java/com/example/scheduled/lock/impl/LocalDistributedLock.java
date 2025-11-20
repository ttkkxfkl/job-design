package com.example.scheduled.lock.impl;

import com.example.scheduled.lock.DistributedLock;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * 本地分布式锁实现（单机模式）
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "scheduled.task.lock.type", havingValue = "local", matchIfMissing = true)
public class LocalDistributedLock implements DistributedLock {

    private final ConcurrentHashMap<String, Lock> lockMap = new ConcurrentHashMap<>();

    @Override
    public boolean tryLock(String lockKey, long expireSeconds) {
        Lock lock = lockMap.computeIfAbsent(lockKey, k -> new ReentrantLock());
        try {
            return lock.tryLock(expireSeconds, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("获取锁失败：{}", lockKey, e);
            return false;
        }
    }

    @Override
    public void unlock(String lockKey) {
        Lock lock = lockMap.get(lockKey);
        if (lock != null) {
            try {
                lock.unlock();
            } catch (Exception e) {
                log.error("释放锁失败：{}", lockKey, e);
            }
        }
    }
}
