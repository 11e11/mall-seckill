package com.wayn.domain.trade.support.seckill;

import com.wayn.data.redis.manager.RedisCache;
import com.wayn.domain.api.outbox.entity.LocalMessage;
import com.wayn.domain.api.outbox.service.LocalMessageTopics;
import com.wayn.domain.promotion.support.seckill.SeckillRedisKeySupport;
import com.wayn.domain.promotion.support.seckill.SeckillResultSupport;
import com.wayn.domain.trade.outbox.LocalMessageHandler;
import lombok.AllArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

/**
 * 处理已提交的秒杀超时释放任务；Redis 回补使用操作标记抵御本地消息重试。
 */
@Service
@AllArgsConstructor
public class SeckillRedisReleaseHandler implements LocalMessageHandler {

    private final RedisCache redisCache;
    private final SeckillResultSupport seckillResultSupport;

    /** 当前处理器只接收秒杀超时 Redis 回补主题。 */
    @Override
    public boolean supports(String topic) {
        return LocalMessageTopics.SECKILL_REDIS_RELEASE.equals(topic);
    }

    /**
     * 先幂等回补库存与用户资格，再刷新轮询结果；任一步失败交由 relay 重试。
     */
    @Override
    public void handle(LocalMessage message) {
        SeckillRedisReleasePayload payload = SeckillRedisReleasePayload.fromJson(message.getPayload());
        validate(payload);
        Long result = redisCache.luaReleaseSeckillStock(
                SeckillRedisKeySupport.stockKey(payload.activitySkuId()),
                SeckillRedisKeySupport.userPurchaseKey(payload.activityId(), payload.goodsId(), payload.userId()),
                SeckillRedisKeySupport.releaseMarkerKey(payload.orderSn(), payload.activitySkuId()),
                payload.number());
        if (result == null || (result != 1L && result != 2L)) {
            throw new IllegalStateException("秒杀 Redis 库存回补未完成, orderSn=" + payload.orderSn()
                    + ", activitySkuId=" + payload.activitySkuId() + ", result=" + result);
        }
        seckillResultSupport.markClosed(payload.orderSn());
    }

    /** 拒绝损坏的消息，避免 Lua 使用错误 Key 或数量。 */
    private void validate(SeckillRedisReleasePayload payload) {
        if (payload == null || StringUtils.isBlank(payload.orderSn()) || payload.activityId() == null
                || payload.activitySkuId() == null || payload.goodsId() == null || payload.userId() == null
                || payload.number() == null || payload.number() <= 0) {
            throw new IllegalArgumentException("秒杀 Redis 回补消息参数不完整");
        }
    }
}
