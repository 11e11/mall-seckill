package com.wayn.domain.trade.support.seckill;

import com.wayn.domain.trade.outbox.LocalMessageCreateCommand;
import com.wayn.domain.trade.outbox.LocalMessageService;
import com.wayn.domain.api.common.MybatisPlusTableInfoTestHelper;
import com.wayn.domain.api.promotion.entity.SeckillSku;
import com.wayn.domain.api.promotion.service.ISeckillSkuService;
import com.wayn.domain.api.trade.entity.OrderActivityRelation;
import com.wayn.domain.api.trade.service.IOrderActivityRelationService;
import com.wayn.domain.api.outbox.service.LocalMessageTopics;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.ArgumentMatchers.any;
import org.mockito.ArgumentCaptor;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 秒杀库存释放支撑服务测试。
 */
@ExtendWith(MockitoExtension.class)
class SeckillInventoryReleaseSupportTest {

    @Mock
    private ISeckillSkuService seckillSkuService;

    @Mock
    private IOrderActivityRelationService orderActivityRelationService;

    @Mock
    private LocalMessageService localMessageService;

    @Mock
    private SeckillInventoryFlowSupport seckillInventoryFlowSupport;

    @InjectMocks
    private SeckillInventoryReleaseSupport seckillInventoryReleaseSupport;

    /**
     * 初始化 MyBatis-Plus 实体元数据，保证 lambdaUpdate 可在纯单测中正常构建。
     */
    @BeforeAll
    static void beforeAll() {
        MybatisPlusTableInfoTestHelper.init(SeckillSku.class, OrderActivityRelation.class);
    }

    /**
     * 数据库活动库存释放成功后，只写可靠回补任务，不在事务中操作 Redis。
     */
    @Test
    void releaseTimeoutStockEnqueuesRedisRecoveryWhenDatabaseReleaseSucceeded() {
        OrderActivityRelation relation = new OrderActivityRelation();
        relation.setId(1L);
        relation.setActivitySkuId(10L);
        relation.setActivityId(1L);
        relation.setGoodsId(30L);
        relation.setProductId(20L);
        relation.setUserId(99L);
        relation.setNumber(2);
        when(orderActivityRelationService.update(any())).thenReturn(true);
        when(seckillSkuService.update(any())).thenReturn(true);

        seckillInventoryReleaseSupport.releaseTimeoutStock(relation, "S001");

        verify(seckillInventoryFlowSupport).recordRelease("S001", relation);
        ArgumentCaptor<LocalMessageCreateCommand> command = ArgumentCaptor.forClass(LocalMessageCreateCommand.class);
        verify(localMessageService).saveMessage(command.capture());
        assertEquals(LocalMessageTopics.SECKILL_REDIS_RELEASE, command.getValue().topic());
        assertEquals("SECKILL_REDIS_RELEASE:S001:10", command.getValue().messageKey());
        assertEquals(2, SeckillRedisReleasePayload.fromJson(command.getValue().payload()).number());
    }

    @Test
    void releaseTimeoutStockDoesNotEnqueueWhenRelationIsNoLongerLocked() {
        OrderActivityRelation relation = new OrderActivityRelation();
        relation.setId(1L);
        relation.setActivitySkuId(10L);
        relation.setActivityId(1L);
        relation.setGoodsId(30L);
        relation.setUserId(99L);
        relation.setNumber(1);
        when(orderActivityRelationService.update(any())).thenReturn(false);

        assertThrows(RuntimeException.class, () -> seckillInventoryReleaseSupport.releaseTimeoutStock(relation, "S001"));
        org.mockito.Mockito.verifyNoInteractions(localMessageService);
    }
}
