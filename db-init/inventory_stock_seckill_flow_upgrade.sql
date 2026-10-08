ALTER TABLE `shop_inventory_flow`
    ADD COLUMN `inventory_type` varchar(16) NOT NULL DEFAULT 'PRODUCT' COMMENT '库存类型：PRODUCT 普通库存，SECKILL 秒杀活动库存' AFTER `biz_id`,
    ADD COLUMN `activity_id` bigint DEFAULT NULL COMMENT '活动 ID' AFTER `inventory_type`,
    ADD COLUMN `activity_sku_id` bigint DEFAULT NULL COMMENT '活动 SKU ID' AFTER `activity_id`,
    ADD KEY `idx_inventory_flow_activity` (`inventory_type`, `activity_sku_id`, `change_type`);
