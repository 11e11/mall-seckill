package com.wayn.domain.trade.support.seckill;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.wayn.domain.api.common.MybatisPlusTableInfoTestHelper;
import com.wayn.domain.api.promotion.entity.SeckillSku;
import com.wayn.domain.api.promotion.service.ISeckillSkuService;
import com.wayn.domain.api.trade.entity.Order;
import com.wayn.domain.api.trade.entity.OrderActivityRelation;
import com.wayn.domain.api.trade.mapper.OrderMapper;
import com.wayn.domain.api.trade.service.IOrderActivityRelationService;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 秒杀库存支付确认支撑服务测试。
 */
@ExtendWith(MockitoExtension.class)
class SeckillInventoryConfirmSupportTest {

    @Mock
    private IOrderActivityRelationService orderActivityRelationService;
    @Mock
    private ISeckillSkuService seckillSkuService;
    @Mock
    private OrderMapper orderMapper;
    @Mock
    private SeckillInventoryFlowSupport seckillInventoryFlowSupport;
    @InjectMocks
    private SeckillInventoryConfirmSupport support;

    @BeforeAll
    static void beforeAll() {
        MybatisPlusTableInfoTestHelper.init(SeckillSku.class, OrderActivityRelation.class);
    }

    @Test
    void confirmPaidStockRecordsFlowAfterStateAndStockUpdates() {
        OrderActivityRelation relation = relation();
        Order order = new Order();
        order.setOrderSn("S001");
        when(orderActivityRelationService.list(any(Wrapper.class))).thenReturn(List.of(relation));
        when(orderMapper.selectById(100L)).thenReturn(order);
        when(orderActivityRelationService.update(any())).thenReturn(true);
        when(seckillSkuService.update(any())).thenReturn(true);

        support.confirmPaidStock(100L);

        verify(seckillInventoryFlowSupport).recordConfirm("S001", relation);
    }

    @Test
    void confirmPaidStockDoesNotRecordFlowWhenRelationAlreadyHandled() {
        OrderActivityRelation relation = relation();
        Order order = new Order();
        order.setOrderSn("S001");
        when(orderActivityRelationService.list(any(Wrapper.class))).thenReturn(List.of(relation));
        when(orderMapper.selectById(100L)).thenReturn(order);
        when(orderActivityRelationService.update(any())).thenReturn(false);

        support.confirmPaidStock(100L);

        verify(seckillSkuService, never()).update(any());
        verify(seckillInventoryFlowSupport, never()).recordConfirm(any(), any());
    }

    private OrderActivityRelation relation() {
        OrderActivityRelation relation = new OrderActivityRelation();
        relation.setId(1L);
        relation.setActivityId(2L);
        relation.setActivitySkuId(10L);
        relation.setGoodsId(20L);
        relation.setProductId(30L);
        relation.setNumber(1);
        return relation;
    }
}
