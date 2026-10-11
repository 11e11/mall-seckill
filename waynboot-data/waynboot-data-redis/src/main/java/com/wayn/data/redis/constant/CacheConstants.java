package com.wayn.data.redis.constant;

/**
 * 缓存常量
 */
public class CacheConstants {

    /**
     * 缓存前缀, 统一项目缓存前缀
     */
    public static final String CACHE_PREFIX = "waynboot:";

    /**
     * 登录用户 redis key
     */
    public static final String LOGIN_TOKEN_KEY = CACHE_PREFIX + "login_tokens:";

    /**
     * 用户会话索引 key 前缀，key 结构：waynboot:user_login_tokens:{userId}
     */
    public static final String USER_LOGIN_TOKEN_KEY_PREFIX = CACHE_PREFIX + "user_login_tokens:";


    public static final String SHOP_HOME_INDEX_HASH = CACHE_PREFIX + "shop_home_index_hash";
    public static final String SHOP_HOME_INDEX_HASH_EXPIRATION_FIELD = "expire_time";

    public static final String ORDER_SN_INCR_KEY = CACHE_PREFIX + "order_sn_incr_key";

    /**
     * 商品 ID 布隆过滤器位图 key，Redisson RBloomFilter 存储，多实例共享。
     */
    public static final String GOODS_ID_BLOOM_FILTER = CACHE_PREFIX + "goods_id_bloom";

    /**
     * 布隆过滤器全量装载完成标记，位图存在但标记缺失时启动会重新全量装载。
     */
    public static final String GOODS_ID_BLOOM_FILTER_LOADED = CACHE_PREFIX + "goods_id_bloom_loaded";

}
