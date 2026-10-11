package com.wayn.domain.goods.support;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.wayn.domain.api.goods.mapper.GoodsMapper;
import com.wayn.data.redis.manager.RedisCache;
import org.junit.jupiter.api.Test;
import org.redisson.api.RBloomFilter;
import org.redisson.api.RedissonClient;

import java.util.List;

import static com.wayn.data.redis.constant.CacheConstants.GOODS_ID_BLOOM_FILTER;
import static com.wayn.data.redis.constant.CacheConstants.GOODS_ID_BLOOM_FILTER_LOADED;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GoodsIdBloomFilterTest {

    @Test
    void filterAllowsRequestsBeforeInitialization() {
        GoodsIdBloomFilter filter = new GoodsIdBloomFilter(
                mock(GoodsMapper.class), mock(RedissonClient.class), mock(RedisCache.class));

        assertTrue(filter.mightContain(1L));
    }

    @Test
    @SuppressWarnings("unchecked")
    void rebuildLoadsExistingIdsAndPutAddsNewId() {
        GoodsMapper goodsMapper = mock(GoodsMapper.class);
        when(goodsMapper.selectObjs(any(Wrapper.class))).thenReturn(List.of(1L, 2L));
        RBloomFilter<String> bloom = mock(RBloomFilter.class);
        when(bloom.tryInit(anyLong(), anyDouble())).thenReturn(true);
        RedissonClient redissonClient = mock(RedissonClient.class);
        when(redissonClient.<String>getBloomFilter(GOODS_ID_BLOOM_FILTER)).thenReturn(bloom);
        RedisCache redisCache = mock(RedisCache.class);

        GoodsIdBloomFilter filter = new GoodsIdBloomFilter(goodsMapper, redissonClient, redisCache);
        filter.rebuild();
        filter.put(3L);

        verify(bloom).add("1");
        verify(bloom).add("2");
        verify(bloom).add("3");
        verify(redisCache).setCacheObject(GOODS_ID_BLOOM_FILTER_LOADED, true);
        when(bloom.contains("1")).thenReturn(true);
        assertTrue(filter.mightContain(1L));
    }

    @Test
    @SuppressWarnings("unchecked")
    void rebuildReusesLoadedBitmapWithoutReloading() {
        GoodsMapper goodsMapper = mock(GoodsMapper.class);
        when(goodsMapper.selectObjs(any(Wrapper.class))).thenReturn(List.of(1L));
        RBloomFilter<String> bloom = mock(RBloomFilter.class);
        when(bloom.tryInit(anyLong(), anyDouble())).thenReturn(false);
        RedissonClient redissonClient = mock(RedissonClient.class);
        when(redissonClient.<String>getBloomFilter(GOODS_ID_BLOOM_FILTER)).thenReturn(bloom);
        RedisCache redisCache = mock(RedisCache.class);
        when(redisCache.getCacheObject(GOODS_ID_BLOOM_FILTER_LOADED)).thenReturn(true);

        GoodsIdBloomFilter filter = new GoodsIdBloomFilter(goodsMapper, redissonClient, redisCache);
        filter.rebuild();
        filter.put(2L);

        verify(bloom, never()).add("1");
        verify(bloom).add("2");
        when(bloom.contains("2")).thenReturn(true);
        assertTrue(filter.mightContain(2L));
    }

    @Test
    @SuppressWarnings("unchecked")
    void redisLookupFailureFallsBackToDatabase() {
        GoodsMapper goodsMapper = mock(GoodsMapper.class);
        when(goodsMapper.selectObjs(any(Wrapper.class))).thenReturn(List.of(1L));
        RBloomFilter<String> bloom = mock(RBloomFilter.class);
        when(bloom.tryInit(anyLong(), anyDouble())).thenReturn(true);
        when(bloom.contains("1")).thenThrow(new IllegalStateException("redis down"));
        RedissonClient redissonClient = mock(RedissonClient.class);
        when(redissonClient.<String>getBloomFilter(GOODS_ID_BLOOM_FILTER)).thenReturn(bloom);
        GoodsIdBloomFilter filter = new GoodsIdBloomFilter(goodsMapper, redissonClient, mock(RedisCache.class));

        filter.rebuild();

        assertTrue(filter.mightContain(1L));
    }
}
