package com.wayn.domain.trade.support.seckill;

import com.wayn.domain.api.inventory.service.InventoryFlowCreateCommand;
import com.wayn.domain.api.inventory.service.InventoryFlowService;
import com.wayn.domain.api.trade.entity.OrderActivityRelation;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 秒杀活动库存流水支撑服务测试。
 */
class SeckillInventoryFlowSupportTest {

    @Test
    void recordFreezeBuildsSeckillInventoryFlow() {
        InventoryFlowService flowService = mock(InventoryFlowService.class);
        when(flowService.saveFlow(any())).thenReturn(true);
        SeckillInventoryFlowSupport support = new SeckillInventoryFlowSupport(flowService);

        support.recordFreeze("S001", 1L, 10L, 20L, 30L, 2);

        ArgumentCaptor<InventoryFlowCreateCommand> captor = ArgumentCaptor.forClass(InventoryFlowCreateCommand.class);
        verify(flowService).saveFlow(captor.capture());
        InventoryFlowCreateCommand command = captor.getValue();
        assertEquals("SECKILL_FREEZE:S001:10", command.flowKey());
        assertEquals("SECKILL_ORDER", command.bizType());
        assertEquals("SECKILL", command.inventoryType());
        assertEquals(1L, command.activityId());
        assertEquals(10L, command.activitySkuId());
        assertEquals("FREEZE", command.changeType());
        assertEquals(2, command.changeNumber());
    }

    @Test
    void recordConfirmAndReleaseUseIndependentIdempotencyKeys() {
        InventoryFlowService flowService = mock(InventoryFlowService.class);
        when(flowService.saveFlow(any())).thenReturn(true);
        SeckillInventoryFlowSupport support = new SeckillInventoryFlowSupport(flowService);
        OrderActivityRelation relation = relation();

        support.recordConfirm("S001", relation);
        support.recordRelease("S001", relation);

        ArgumentCaptor<InventoryFlowCreateCommand> captor = ArgumentCaptor.forClass(InventoryFlowCreateCommand.class);
        verify(flowService, org.mockito.Mockito.times(2)).saveFlow(captor.capture());
        assertEquals("SECKILL_CONFIRM:S001:10", captor.getAllValues().get(0).flowKey());
        assertEquals("SECKILL_RELEASE:S001:10", captor.getAllValues().get(1).flowKey());
    }

    @Test
    void duplicateFlowFailsCurrentTransaction() {
        InventoryFlowService flowService = mock(InventoryFlowService.class);
        when(flowService.saveFlow(any())).thenReturn(false);
        SeckillInventoryFlowSupport support = new SeckillInventoryFlowSupport(flowService);

        assertThrows(RuntimeException.class,
                () -> support.recordRelease("S001", relation()));
    }

    private OrderActivityRelation relation() {
        OrderActivityRelation relation = new OrderActivityRelation();
        relation.setActivityId(1L);
        relation.setActivitySkuId(10L);
        relation.setGoodsId(20L);
        relation.setProductId(30L);
        relation.setNumber(2);
        return relation;
    }
}
