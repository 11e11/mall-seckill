package com.wayn.domain.trade.support.seckill;

import com.wayn.data.redis.manager.RedisCache;
import com.wayn.domain.api.outbox.entity.LocalMessage;
import com.wayn.domain.promotion.support.seckill.SeckillRedisKeySupport;
import com.wayn.domain.promotion.support.seckill.SeckillResultSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 秒杀库存 Redis 回补消息测试。 */
@ExtendWith(MockitoExtension.class)
class SeckillRedisReleaseHandlerTest {

    @Mock
    private RedisCache redisCache;
    @Mock
    private SeckillResultSupport seckillResultSupport;
    @InjectMocks
    private SeckillRedisReleaseHandler handler;

    /** 首次回补成功后更新轮询结果。 */
    @Test
    void appliesReleaseAndMarksOrderClosed() {
        when(redisCache.luaReleaseSeckillStock(stockKey(), userKey(), markerKey(), 2)).thenReturn(1L);

        handler.handle(message());

        verify(seckillResultSupport).markClosed("S001");
    }

    /** 已执行的 Lua 操作再次到达时仍可完成结果更新。 */
    @Test
    void repeatedMessageDoesNotBlockResultUpdate() {
        when(redisCache.luaReleaseSeckillStock(stockKey(), userKey(), markerKey(), 2)).thenReturn(2L);

        handler.handle(message());

        verify(seckillResultSupport).markClosed("S001");
    }

    /** 库存 Key 丢失时保持消息待处理，不能凭空创建库存。 */
    @Test
    void missingStockKeyFailsForRelayRetry() {
        when(redisCache.luaReleaseSeckillStock(stockKey(), userKey(), markerKey(), 2)).thenReturn(-1L);

        assertThrows(IllegalStateException.class, () -> handler.handle(message()));

        verify(seckillResultSupport, never()).markClosed("S001");
    }

    private LocalMessage message() {
        LocalMessage message = new LocalMessage();
        message.setPayload(new SeckillRedisReleasePayload("S001", 1L, 10L, 30L, 99L, 2).toJson());
        return message;
    }

    private String stockKey() {
        return SeckillRedisKeySupport.stockKey(10L);
    }

    private String userKey() {
        return SeckillRedisKeySupport.userPurchaseKey(1L, 30L, 99L);
    }

    private String markerKey() {
        return SeckillRedisKeySupport.releaseMarkerKey("S001", 10L);
    }
}
