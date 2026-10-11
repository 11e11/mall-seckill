package com.wayn.domain.promotion.support.seckill;

import com.wayn.data.redis.manager.RedisCache;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * 秒杀活动详情两级缓存支撑。
 * 读侧按 L1(Caffeine) -> L2(Redis) -> DB 回源；写侧沿用 Cache Aside，数据库提交后删除两级缓存，TTL 只负责最终过期。
 * enabled 开关用于压测对照与缓存故障降级：关闭后读请求绕过 Caffeine 与 Redis，直接回源数据库。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SeckillActivityDetailCacheSupport {

    /**
     * 详情缓存 TTL。活动详情读多写少，5 分钟兜底覆盖人工核对窗口；实时性由更新侧主动失效保证。
     */
    private static final int DETAIL_TTL_SECONDS = 300;

    private final RedisCache redisCache;

    /**
     * 缓存开关。false 时读请求直接查库，用于无缓存基线压测和缓存故障降级。
     */
    @Value("${wayn.cache.seckill-activity-detail.enabled:true}")
    private boolean enabled;

    public boolean isEnabled() {
        return enabled;
    }

    /**
     * 读取 L1 Caffeine 本地缓存。
     *
     * @param cacheKey 缓存 Key
     * @return 缓存值，未命中返回 null
     */
    public <T> T getLocal(String cacheKey) {
        return redisCache.getLocalCacheObject(cacheKey);
    }

    /**
     * 写入 L1 Caffeine 本地缓存。
     *
     * @param cacheKey 缓存 Key
     * @param value 缓存值
     */
    public <T> void putLocal(String cacheKey, T value) {
        redisCache.setLocalCacheObject(cacheKey, value);
    }

    /**
     * 读取 L2 Redis 缓存。
     *
     * @param cacheKey 缓存 Key
     * @return 缓存值，未命中返回 null
     */
    public <T> T getRedis(String cacheKey) {
        return redisCache.getCacheObject(cacheKey);
    }

    /**
     * 写入 L2 Redis 缓存并携带 TTL。
     *
     * @param cacheKey 缓存 Key
     * @param value 缓存值
     */
    public <T> void putRedis(String cacheKey, T value) {
        redisCache.setCacheObject(cacheKey, value, DETAIL_TTL_SECONDS);
    }

    /**
     * 数据库事务提交后再失效两级缓存。
     * 事务方法内直接删除会与并发回源竞争：回源线程可能读到提交前的旧值并重新写入缓存，导致删缓存失效。
     *
     * @param activityId 活动 ID
     */
    public void evictAfterCommit(Long activityId) {
        String cacheKey = SeckillRedisKeySupport.activityDetailKey(activityId);
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    redisCache.deleteObject(cacheKey);
                }
            });
        } else {
            redisCache.deleteObject(cacheKey);
        }
    }
}
