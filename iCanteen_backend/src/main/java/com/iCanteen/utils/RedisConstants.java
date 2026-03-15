package com.iCanteen.utils;

public class RedisConstants {
    public static final String LOGIN_CODE_KEY = "login:code:";
    public static final Long LOGIN_CODE_TTL = 2L;
    public static final String LOGIN_USER_KEY = "login:token:";
    public static final Long LOGIN_USER_TTL = 36000L;

    public static final Long CACHE_NULL_TTL = 2L;

    public static final Long CACHE_SHOP_TTL = 30L;
    public static final String CACHE_SHOP_KEY = "cache:shop:";
    public static final Long CACHE_CANTEEN_TTL = 2L;
    public static final String CACHE_CANTEEN_LIST_KEY = "cache:canteen:list";
    public static final String CACHE_CANTEEN_CROWD_LIST_KEY = "cache:canteen:crowd:list";
    public static final String CACHE_CANTEEN_DETAIL_KEY = "cache:canteen:detail:";
    public static final String CACHE_WINDOW_LIST_BY_CANTEEN_KEY = "cache:window:list:canteen:";
    public static final String CACHE_WINDOW_DETAIL_KEY = "cache:window:detail:";
    public static final String CACHE_DISH_LIST_BY_WINDOW_KEY = "cache:dish:list:window:";
    public static final String CACHE_DISH_LIST_BY_CANTEEN_KEY = "cache:dish:list:canteen:";
    public static final String CACHE_DISH_DETAIL_KEY = "cache:dish:detail:";
    public static final String CACHE_DISH_RANDOM_RECOMMEND_KEY = "cache:dish:random:recommend";
    public static final String CACHE_DISH_RECOMMEND_BY_CANTEEN_KEY = "cache:dish:recommend:canteen:";
    public static final Long CACHE_DISH_RECOMMEND_TTL = 20L;
    public static final String CACHE_FOOD_ORDER_DETAIL_KEY = "cache:food:order:detail:";
    public static final String CACHE_FOOD_ORDER_LIST_USER_KEY = "cache:food:order:list:user:";
    public static final String CACHE_FOOD_ORDER_LIST_ADMIN_KEY = "cache:food:order:list:admin:";
    public static final Long CACHE_FOOD_ORDER_TTL = 2L;
    public static final String FOOD_ORDER_USER_LIST_VERSION_KEY = "food:order:list:user:version:";
    public static final String FOOD_ORDER_ADMIN_LIST_VERSION_KEY = "food:order:list:admin:version";
    public static final String CACHE_DISH_REVIEW_LIST_KEY = "cache:dish:review:list:";
    public static final Long CACHE_DISH_REVIEW_TTL = 2L;
    public static final String DISH_REVIEW_LIST_VERSION_KEY = "dish:review:list:version:";
    public static final String CROWD_REPORT_LIMIT_KEY = "crowd:report:limit:";
    public static final Long CROWD_REPORT_LIMIT_TTL = 5L;
    public static final String WAIT_REPORT_LIMIT_KEY = "wait:report:limit:";
    public static final Long WAIT_REPORT_LIMIT_TTL = 30L;
    public static final String CANTEEN_GEO_KEY = "canteen:geo";

    public static final String LOCK_SHOP_KEY = "lock:shop:";
    public static final String LOCK_CANTEEN_KEY = "lock:canteen:";
    public static final String LOCK_WINDOW_KEY = "lock:window:";
    public static final String LOCK_DISH_KEY = "lock:dish:";
    public static final String LOCK_CACHE_FOOD_ORDER_KEY = "lock:cache:food:order:";
    public static final String LOCK_DISH_STOCK_KEY = "lock:dish:stock:";
    public static final String LOCK_FOOD_ORDER_KEY = "lock:food:order:";
    public static final String LOCK_FOOD_ORDER_CREATE_USER_KEY = "lock:food:order:create:user:";
    public static final Long LOCK_SHOP_TTL = 10L;

    public static final String SECKILL_STOCK_KEY = "seckill:stock:";
    public static final String BLOG_LIKED_KEY = "blog:liked:";
    public static final String FEED_KEY = "feed:";
    public static final String SHOP_GEO_KEY = "shop:geo:";
    public static final String USER_SIGN_KEY = "user:sign:";
}
