package com.example.scheduled.lock;

/**
 * 分布式锁接口 - 预留扩展
 * 单机使用本地锁，集群时可切换到Redis实现
 */
public interface DistributedLock {

    /**
     * 尝试获取锁
     * @param lockKey 锁键
     * @param expireSeconds 过期时间（秒）
     * @return 是否获取成功
     */
    boolean tryLock(String lockKey, long expireSeconds);

    /**
     * 释放锁
     * @param lockKey 锁键
     */
    void unlock(String lockKey);
}
