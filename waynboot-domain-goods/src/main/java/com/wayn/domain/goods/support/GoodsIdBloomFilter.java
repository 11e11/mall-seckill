package com.wayn.domain.goods.support;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.wayn.domain.api.goods.entity.Goods;
import com.wayn.domain.api.goods.mapper.GoodsMapper;
import com.wayn.data.redis.manager.RedisCache;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RBloomFilter;
import org.redisson.api.RedissonClient;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;

import static com.wayn.data.redis.constant.CacheConstants.GOODS_ID_BLOOM_FILTER;
import static com.wayn.data.redis.constant.CacheConstants.GOODS_ID_BLOOM_FILTER_LOADED;

/**
 * 商品 ID 布隆过滤器，基于 Redisson RBloomFilter 实现，位图存于 Redis，多实例共享。
 * 启动时通过 tryInit 竞争初始化权，胜出的实例全量装载商品 ID；
 * 位图已存在但装载标记缺失（上次装载中断或 Redis 位图丢失后重建）时重新全量装载，add 幂等，多实例并发装载安全。
 * 初始化完成前默认放行，避免把真实商品误拦截。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GoodsIdBloomFilter {

    private static final long MIN_EXPECTED_INSERTIONS = 10_000L;
    private static final double FALSE_POSITIVE_PROBABILITY = 0.01D;

    private final GoodsMapper goodsMapper;
    private final RedissonClient redissonClient;
    private final RedisCache redisCache;
    private volatile RBloomFilter<String> bloomFilter;

    @EventListener(ApplicationReadyEvent.class)
    public void rebuild() {
        try {
            List<Object> goodsIds = goodsMapper.selectObjs(new QueryWrapper<Goods>().select("id"));
            long expectedInsertions = Math.max(MIN_EXPECTED_INSERTIONS, goodsIds.size() * 2L);
            RBloomFilter<String> filter = redissonClient.getBloomFilter(GOODS_ID_BLOOM_FILTER);
            if (!filter.tryInit(expectedInsertions, FALSE_POSITIVE_PROBABILITY)
                    && redisCache.getCacheObject(GOODS_ID_BLOOM_FILTER_LOADED) != null) {
                // 位图已由其他实例初始化并完成装载，直接复用
                bloomFilter = filter;
                log.info("商品ID布隆过滤器复用Redis已有位图, expectedInsertions={}", expectedInsertions);
                return;
            }
            load(filter, goodsIds, expectedInsertions);
        } catch (Exception exception) {
            log.warn("商品ID布隆过滤器初始化失败，本次启动将暂时放行商品详情请求", exception);
        }
    }

    private void load(RBloomFilter<String> filter, List<Object> goodsIds, long expectedInsertions) {
        goodsIds.stream()
                .map(this::toStringId)
                .filter(Objects::nonNull)
                .forEach(filter::add);
        redisCache.setCacheObject(GOODS_ID_BLOOM_FILTER_LOADED, Boolean.TRUE);
        bloomFilter = filter;
        log.info("商品ID布隆过滤器装载完成, goodsCount={}, expectedInsertions={}", goodsIds.size(), expectedInsertions);
    }

    public boolean mightContain(Long goodsId) {
        if (goodsId == null) {
            return false;
        }
        RBloomFilter<String> current = bloomFilter;
        if (current == null) {
            return true;
        }
        try {
            return current.contains(String.valueOf(goodsId));
        } catch (RuntimeException exception) {
            // Redis 短暂故障时放行到数据库，不能把真实商品误判为不存在。
            log.warn("商品ID布隆过滤器查询失败，放行数据库查询, goodsId={}", goodsId, exception);
            return true;
        }
    }

    /**
     * 新增商品后写入共享位图，所有实例立即可见，无需实例间同步。
     */
    public void put(Long goodsId) {
        if (goodsId == null) {
            return;
        }
        RBloomFilter<String> current = bloomFilter;
        if (current == null) {
            return;
        }
        current.add(String.valueOf(goodsId));
    }

    private String toStringId(Object value) {
        if (value instanceof Number number) {
            return String.valueOf(number.longValue());
        }
        try {
            return value == null ? null : String.valueOf(Long.valueOf(value.toString()));
        } catch (NumberFormatException exception) {
            log.warn("忽略无法转换的商品ID, value={}", value);
            return null;
        }
    }
}
