package com.wayn.domain.api.inventory.enums;

/**
 * 库存流水归属类型。
 * 用于区分普通商品库存和活动库存，避免对账时混用两套库存口径。
 */
public enum InventoryTypeEnum {

    PRODUCT,

    SECKILL
}
