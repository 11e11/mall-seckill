package com.wayn.domain.trade.support.seckill;

import com.alibaba.fastjson.JSON;

/**
 * 秒杀超时释放后的 Redis 入口库存回补任务数据。
 */
public record SeckillRedisReleasePayload(String orderSn, Long activityId, Long activitySkuId,
                                         Long goodsId, Long userId, Integer number) {

    /** 将任务数据写入本地消息。 */
    public String toJson() {
        return JSON.toJSONString(this);
    }

    /** 从本地消息读取任务数据。 */
    public static SeckillRedisReleasePayload fromJson(String json) {
        return JSON.parseObject(json, SeckillRedisReleasePayload.class);
    }
}
