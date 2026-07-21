package com.ticket.web.lock;

import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.concurrent.TimeUnit;

/**
 * 基于 Redisson 的 Redis 分布式锁。
 *
 * 用途（对应核心痛点“重复处理/超卖式并发”）：
 *  - 幂等建单：同一 idempotencyKey 的并发提交只建一张单；
 *  - 接单互斥：同一工单同一时刻只允许一人认领，杜绝多人同时接单导致重复处理。
 *
 * 锁获取失败即视为“未拿到资源”，由调用方决定重试或直接返回，绝不裸等。
 */
@Component
public class RedisDistributedLock {

    @Resource
    private RedissonClient redissonClient;

    /**
     * 尝试获取锁。
     *
     * @param waitSeconds  最多等待时间
     * @param leaseSeconds 锁自动过期时间（防止宕机死锁）
     * @return 是否获取到锁
     */
    public boolean tryLock(String key, long waitSeconds, long leaseSeconds) {
        RLock lock = redissonClient.getLock(key);
        try {
            return lock.tryLock(waitSeconds, leaseSeconds, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    public void unlock(String key) {
        RLock lock = redissonClient.getLock(key);
        if (lock.isHeldByCurrentThread()) {
            lock.unlock();
        }
    }
}
