package com.wayn.common.model.response;

import lombok.Data;

/**
 * 本地消息补偿指标响应。
 */
@Data
public class LocalMessageCompensationMetricResVO {

    /**
     * 当前失败消息数量。
     */
    private Long failedCount;

    /** 待处理的秒杀 Redis 回补任务数。 */
    private Long seckillRedisReleasePendingCount;

    /** 最早一条秒杀 Redis 回补任务已等待的秒数。 */
    private Long seckillRedisReleaseOldestAgeSeconds;
}
