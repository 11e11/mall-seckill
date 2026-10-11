package com.wayn.domain.trade.support.common;

import com.wayn.domain.api.common.TradeLockSupport;
import lombok.AllArgsConstructor;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * 基于 Redisson 的交易锁实现。
 * timeout 为空时由 Redisson 看门狗自动续期；指定 timeout 时使用固定租期。
 */
@Service
@AllArgsConstructor
public class RedissonTradeLockSupport implements TradeLockSupport {

    private final RedissonClient redissonClient;

    @Override
    public void runWithLock(String lockKey, Integer timeout, Supplier<? extends RuntimeException> exceptionSupplier,
                            Runnable action) {
        executeWithLock(lockKey, timeout, exceptionSupplier, () -> {
            action.run();
            return null;
        });
    }

    @Override
    public <T> T executeWithLock(String lockKey, Integer timeout,
                                 Supplier<? extends RuntimeException> exceptionSupplier, Supplier<T> supplier) {
        RLock lock = redissonClient.getLock(lockKey);
        if (!tryLock(lock, timeout)) {
            throw exceptionSupplier.get();
        }
        try {
            return supplier.get();
        } finally {
            unlock(lock);
        }
    }

    /**
     * 按固定顺序获取多把锁，避免批量交易并发时以不同顺序加锁造成死锁。
     */
    @Override
    public <T> T executeWithLocks(Collection<String> lockKeys, Integer timeout,
                                  Supplier<? extends RuntimeException> exceptionSupplier, Supplier<T> supplier) {
        List<RLock> acquiredLocks = new ArrayList<>();
        List<String> sortedLockKeys = new LinkedHashSet<>(lockKeys).stream()
                .sorted()
                .toList();
        try {
            for (String lockKey : sortedLockKeys) {
                RLock lock = redissonClient.getLock(lockKey);
                if (!tryLock(lock, timeout)) {
                    throw exceptionSupplier.get();
                }
                acquiredLocks.add(lock);
            }
            return supplier.get();
        } finally {
            for (int i = acquiredLocks.size() - 1; i >= 0; i--) {
                unlock(acquiredLocks.get(i));
            }
        }
    }

    @Override
    public boolean tryRunWithLock(String lockKey, Integer timeout, Runnable action) {
        RLock lock = redissonClient.getLock(lockKey);
        if (!tryLock(lock, timeout)) {
            return false;
        }
        try {
            action.run();
            return true;
        } finally {
            unlock(lock);
        }
    }

    private boolean tryLock(RLock lock, Integer timeout) {
        try {
            return timeout == null
                    ? lock.tryLock()
                    : lock.tryLock(0, timeout, TimeUnit.SECONDS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    private void unlock(RLock lock) {
        if (lock.isHeldByCurrentThread()) {
            lock.unlock();
        }
    }
}
