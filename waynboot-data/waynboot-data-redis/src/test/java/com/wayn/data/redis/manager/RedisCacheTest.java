package com.wayn.data.redis.manager;

import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.RedisTemplate;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Redis 脚本构建测试。
 */
class RedisCacheTest {

    @Test
    void deletingRedisKeyAlsoInvalidatesLocalCache() {
        RedisTemplate redisTemplate = mock(RedisTemplate.class);
        RedisCache redisCache = new RedisCache(redisTemplate);
        when(redisTemplate.delete("goods:1")).thenReturn(true);
        redisCache.setLocalCacheObject("goods:1", "cached");

        redisCache.deleteObject("goods:1");

        assertNull(redisCache.getLocalCacheObject("goods:1"));
    }

    /**
     * 秒杀 Lua 写入的字符串值后续会通过 GenericFastJsonRedisSerializer 读取，必须写成 JSON 字符串，不能写裸文本。
     */
    @Test
    void seckillAcquireScriptWritesJsonSerializableStringValues() {
        String script = RedisCache.buildLuaAcquireSeckillStockScript();

        assertFalse(script.contains("redis.call('set', resultKey, 'PROCESSING'"));
        assertFalse(script.contains("local serializedOrderSn = '\"' .. orderSn .. '\"'"));
        assertTrue(script.contains("redis.call('set', userKey, orderSn, 'EX', userTtl)"));
        assertTrue(script.contains("redis.call('set', resultKey, '\"PROCESSING\"', 'EX', resultTtl)"));
    }
}
