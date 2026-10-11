package com.wayn.domain.trade.outbox;

import lombok.Data;

/**
 * 本地消息补偿指标。
 * 面向运营后台和监控采集暴露失败消息数量等轻量指标，避免直接暴露数据库实体。
 */
@Data
public class LocalMessageCompensationMetric {

    /**
     * 当前失败消息数量。
     */
    private Long failedCount;

    /** 待处理的秒杀 Redis 回补任务数。 */
    private Long seckillRedisReleasePendingCount;

    /** 最早一条秒杀 Redis 回补任务已等待的秒数。 */
    private Long seckillRedisReleaseOldestAgeSeconds;
}
