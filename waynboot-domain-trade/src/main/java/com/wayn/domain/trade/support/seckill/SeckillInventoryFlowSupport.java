package com.wayn.domain.trade.support.seckill;

import com.wayn.domain.api.inventory.enums.InventoryChangeTypeEnum;
import com.wayn.domain.api.inventory.enums.InventoryTypeEnum;
import com.wayn.domain.api.inventory.service.InventoryFlowCreateCommand;
import com.wayn.domain.api.inventory.service.InventoryFlowService;
import com.wayn.domain.api.outbox.service.LocalMessageTopics;
import com.wayn.domain.api.trade.entity.OrderActivityRelation;
import com.wayn.util.enums.ReturnCodeEnum;
import com.wayn.util.exception.BusinessException;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 秒杀活动库存流水支撑服务。
 * 统一生成冻结、确认和释放流水，并要求调用方把流水写入与活动库存更新放在同一本地事务中。
 */
@Service
@AllArgsConstructor
public class SeckillInventoryFlowSupport {

    private static final String FREEZE_FLOW_KEY_PREFIX = "SECKILL_FREEZE:";
    private static final String CONFIRM_FLOW_KEY_PREFIX = "SECKILL_CONFIRM:";
    private static final String RELEASE_FLOW_KEY_PREFIX = "SECKILL_RELEASE:";

    private final InventoryFlowService inventoryFlowService;

    /**
     * 记录秒杀订单落库时的活动库存冻结流水。
     */
    public void recordFreeze(String orderSn, Long activityId, Long activitySkuId, Long goodsId,
                             Long productId, Integer number) {
        saveRequired(buildCommand(FREEZE_FLOW_KEY_PREFIX, InventoryChangeTypeEnum.FREEZE, orderSn,
                activityId, activitySkuId, goodsId, productId, number));
    }

    /**
     * 记录支付成功后的活动库存确认流水。
     */
    public void recordConfirm(String orderSn, OrderActivityRelation relation) {
        saveRequired(buildCommand(CONFIRM_FLOW_KEY_PREFIX, InventoryChangeTypeEnum.CONFIRM, orderSn,
                relation.getActivityId(), relation.getActivitySkuId(), relation.getGoodsId(),
                relation.getProductId(), relation.getNumber()));
    }

    /**
     * 记录订单关闭后的活动库存释放流水。
     */
    public void recordRelease(String orderSn, OrderActivityRelation relation) {
        saveRequired(buildCommand(RELEASE_FLOW_KEY_PREFIX, InventoryChangeTypeEnum.RELEASE, orderSn,
                relation.getActivityId(), relation.getActivitySkuId(), relation.getGoodsId(),
                relation.getProductId(), relation.getNumber()));
    }

    private InventoryFlowCreateCommand buildCommand(String flowKeyPrefix, InventoryChangeTypeEnum changeType,
                                                     String orderSn, Long activityId, Long activitySkuId,
                                                     Long goodsId, Long productId, Integer number) {
        return InventoryFlowCreateCommand.builder()
                .flowKey(flowKeyPrefix + orderSn + ":" + activitySkuId)
                .bizType(LocalMessageTopics.BIZ_TYPE_SECKILL_ORDER)
                .bizId(orderSn)
                .inventoryType(InventoryTypeEnum.SECKILL.name())
                .activityId(activityId)
                .activitySkuId(activitySkuId)
                .goodsId(goodsId)
                .productId(productId)
                .changeType(changeType.getType())
                .changeNumber(number)
                .remark("秒杀活动" + changeType.getDescription())
                .build();
    }

    private void saveRequired(InventoryFlowCreateCommand command) {
        if (!inventoryFlowService.saveFlow(command)) {
            throw new BusinessException(ReturnCodeEnum.ORDER_SUBMIT_ERROR, "秒杀库存流水重复或写入失败");
        }
    }
}
