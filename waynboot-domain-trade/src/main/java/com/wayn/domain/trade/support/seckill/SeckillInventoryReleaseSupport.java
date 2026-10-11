package com.wayn.domain.trade.support.seckill;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.wayn.domain.api.promotion.entity.SeckillSku;
import com.wayn.domain.api.promotion.service.ISeckillSkuService;
import com.wayn.domain.api.trade.entity.OrderActivityRelation;
import com.wayn.domain.api.trade.enums.OrderActivityInventoryStatusEnum;
import com.wayn.domain.api.trade.service.IOrderActivityRelationService;
import com.wayn.domain.api.outbox.service.LocalMessageTopics;
import com.wayn.domain.trade.outbox.LocalMessageCreateCommand;
import com.wayn.domain.trade.outbox.LocalMessageService;
import com.wayn.util.enums.ReturnCodeEnum;
import com.wayn.util.exception.BusinessException;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;

import java.util.Date;

/**
 * 秒杀库存释放支撑服务。
 * 在超时关单事务内释放秒杀活动库存并写入 Redis 回补任务。
 */
@Service
@AllArgsConstructor
public class SeckillInventoryReleaseSupport {

    private final ISeckillSkuService seckillSkuService;
    private final IOrderActivityRelationService orderActivityRelationService;
    private final LocalMessageService localMessageService;
    private final SeckillInventoryFlowSupport seckillInventoryFlowSupport;

    /**
     * 释放超时未支付订单占用的秒杀活动库存。
     *
     * @param relation 订单活动关联
     * @param orderSn 订单号
     */
    @Transactional(propagation = Propagation.MANDATORY, rollbackFor = Exception.class)
    public void releaseTimeoutStock(OrderActivityRelation relation, String orderSn) {
        if (relation == null || relation.getId() == null || relation.getActivitySkuId() == null || relation.getActivityId() == null
                || relation.getGoodsId() == null || relation.getUserId() == null
                || relation.getNumber() == null || relation.getNumber() <= 0) {
            throw new IllegalArgumentException("秒杀库存释放缺少有效订单关联");
        }
        Integer number = relation.getNumber();
        boolean relationMarked = orderActivityRelationService.update(Wrappers.lambdaUpdate(OrderActivityRelation.class)
                    .set(OrderActivityRelation::getInventoryStatus, OrderActivityInventoryStatusEnum.RELEASED.getStatus())
                    .set(OrderActivityRelation::getUpdateTime, new Date())
                    .eq(OrderActivityRelation::getId, relation.getId())
                    .eq(OrderActivityRelation::getInventoryStatus, OrderActivityInventoryStatusEnum.LOCKED.getStatus()));
        if (!relationMarked) {
            throw new BusinessException(ReturnCodeEnum.ORDER_SUBMIT_ERROR, "秒杀库存关联不再处于冻结态");
        }
        boolean released = seckillSkuService.update(Wrappers.lambdaUpdate(SeckillSku.class)
                    .setSql("available_stock = available_stock + " + number)
                    .setSql("locked_stock = locked_stock - " + number)
                    .set(SeckillSku::getUpdateTime, new Date())
                    .eq(SeckillSku::getId, relation.getActivitySkuId())
                    .ge(SeckillSku::getLockedStock, number));
        if (!released) {
            throw new BusinessException(ReturnCodeEnum.ORDER_SUBMIT_ERROR, "秒杀活动库存释放失败");
        }
        seckillInventoryFlowSupport.recordRelease(orderSn, relation);
        SeckillRedisReleasePayload payload = new SeckillRedisReleasePayload(orderSn,
                relation.getActivityId(), relation.getActivitySkuId(), relation.getGoodsId(),
                relation.getUserId(), number);
        localMessageService.saveMessage(LocalMessageCreateCommand.builder()
                .messageKey("SECKILL_REDIS_RELEASE:" + orderSn + ":" + relation.getActivitySkuId())
                .topic(LocalMessageTopics.SECKILL_REDIS_RELEASE)
                .bizType(LocalMessageTopics.BIZ_TYPE_SECKILL_ORDER)
                .bizId(orderSn)
                .payload(payload.toJson())
                .build());
    }
}
