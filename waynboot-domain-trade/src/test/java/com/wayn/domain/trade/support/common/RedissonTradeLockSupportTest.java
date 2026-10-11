package com.wayn.domain.trade.support.common;

import com.wayn.domain.api.common.TradeLockSupport;
import com.wayn.util.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;

import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RedissonTradeLockSupportTest {

    @Mock
    private RedissonClient redissonClient;
    @Mock
    private RLock lock;
    @Mock
    private RLock firstLock;
    @Mock
    private RLock secondLock;

    @Test
    void executeWithLockUsesFixedLeaseAndUnlocks() throws InterruptedException {
        TradeLockSupport tradeLockSupport = new RedissonTradeLockSupport(redissonClient);
        when(redissonClient.getLock("lock-key")).thenReturn(lock);
        when(lock.tryLock(0, 2, TimeUnit.SECONDS)).thenReturn(true);
        when(lock.isHeldByCurrentThread()).thenReturn(true);

        String result = tradeLockSupport.executeWithLock("lock-key", 2,
                () -> new BusinessException("lock failed"),
                () -> "ok");

        assertEquals("ok", result);
        verify(lock).unlock();
    }

    @Test
    void executeWithLockThrowsWhenLockFailed() {
        TradeLockSupport tradeLockSupport = new RedissonTradeLockSupport(redissonClient);
        when(redissonClient.getLock("lock-key")).thenReturn(lock);
        when(lock.tryLock()).thenReturn(false);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> tradeLockSupport.executeWithLock("lock-key", null,
                        () -> new BusinessException("lock failed"),
                        () -> "ok"));

        assertEquals("lock failed", exception.getMsg());
        verify(lock, never()).unlock();
    }

    @Test
    void executeWithLocksSortsLocksAndUnlocksInReverseOrder() {
        TradeLockSupport tradeLockSupport = new RedissonTradeLockSupport(redissonClient);
        when(redissonClient.getLock("a-lock")).thenReturn(firstLock);
        when(redissonClient.getLock("b-lock")).thenReturn(secondLock);
        when(firstLock.tryLock()).thenReturn(true);
        when(secondLock.tryLock()).thenReturn(true);
        when(firstLock.isHeldByCurrentThread()).thenReturn(true);
        when(secondLock.isHeldByCurrentThread()).thenReturn(true);

        String result = tradeLockSupport.executeWithLocks(List.of("b-lock", "a-lock", "a-lock"), null,
                () -> new BusinessException("lock failed"),
                () -> "ok");

        assertEquals("ok", result);
        InOrder acquisitionOrder = inOrder(redissonClient);
        acquisitionOrder.verify(redissonClient).getLock("a-lock");
        acquisitionOrder.verify(redissonClient).getLock("b-lock");
        InOrder releaseOrder = inOrder(secondLock, firstLock);
        releaseOrder.verify(secondLock).unlock();
        releaseOrder.verify(firstLock).unlock();
    }
}
