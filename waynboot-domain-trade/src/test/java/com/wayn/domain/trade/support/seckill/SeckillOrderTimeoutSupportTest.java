package com.wayn.domain.trade.support.seckill;

import com.wayn.domain.api.trade.entity.OrderActivityRelation;
import com.wayn.domain.api.trade.entity.Order;
import com.wayn.domain.api.trade.service.IOrderActivityRelationService;
import com.wayn.domain.trade.support.order.OrderCancellationSupport;
import com.wayn.util.enums.OrderStatusEnum;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import java.util.function.Consumer;

/**
 * 秒杀订单超时释放测试。
 */
@ExtendWith(MockitoExtension.class)
class SeckillOrderTimeoutSupportTest {

    @Mock
    private IOrderActivityRelationService orderActivityRelationService;

    @Mock
    private OrderCancellationSupport orderCancellationSupport;

    @Mock
    private SeckillInventoryReleaseSupport seckillInventoryReleaseSupport;

    @InjectMocks
    private SeckillOrderTimeoutSupport seckillOrderTimeoutSupport;

    /**
     * 订单仍处于待支付并成功关闭时，必须释放秒杀活动库存和用户抢购资格。
     */
    @Test
    void closeUnpaidOrderReleasesSeckillInventoryWhenCancelSucceeded() {
        OrderActivityRelation relation = new OrderActivityRelation();
        relation.setOrderSn("S001");
        relation.setActivitySkuId(10L);
        relation.setUserId(99L);
        when(orderActivityRelationService.getByOrderSn("S001")).thenReturn(relation);
        when(orderCancellationSupport.cancel(eq("S001"), eq(OrderStatusEnum.STATUS_AUTO_CANCEL), any()))
                .thenAnswer(invocation -> {
                    invocation.<Consumer<Order>>getArgument(2).accept(new Order());
                    return true;
                });

        seckillOrderTimeoutSupport.closeUnpaidOrder("S001");

        verify(seckillInventoryReleaseSupport).releaseTimeoutStock(relation, "S001");
    }

    /**
     * 订单已支付或状态不允许关闭时，不能回补秒杀库存，避免支付成功后被超时消息误释放。
     */
    @Test
    void closeUnpaidOrderSkipsReleaseWhenCancelNotApplied() {
        OrderActivityRelation relation = new OrderActivityRelation();
        relation.setOrderSn("S001");
        when(orderActivityRelationService.getByOrderSn("S001")).thenReturn(relation);
        when(orderCancellationSupport.cancel(eq("S001"), eq(OrderStatusEnum.STATUS_AUTO_CANCEL), any())).thenReturn(false);

        seckillOrderTimeoutSupport.closeUnpaidOrder("S001");

        verify(seckillInventoryReleaseSupport, never()).releaseTimeoutStock(relation, "S001");
    }
}
