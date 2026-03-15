package com.iCanteen.utils;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import static com.iCanteen.utils.RedisConstants.CACHE_NULL_TTL;
import static com.iCanteen.utils.RedisConstants.LOCK_SHOP_KEY;

/**
 * 缓存客户端工具类
 * 提供多种缓存查询和写入策略，包括穿透处理、逻辑过期、互斥锁等
 */
@Slf4j
@Component
public class CacheClient {

    private final StringRedisTemplate stringRedisTemplate;

    private static final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(10);

    public CacheClient(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    /**
     * 将对象存入 Redis 缓存，设置过期时间
     *
     * @param key Redis 键
     * @param value 要存储的值，将被序列化为 JSON 字符串
     * @param time 过期时长
     * @param unit 时间单位
     */
    public void set(String key, Object value, Long time, TimeUnit unit) {
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(value), time, unit);
    }

    /**
     * 将对象存入 Redis 缓存，使用逻辑过期时间（数据中包含过期时间字段）
     *
     * @param key Redis 键
     * @param value 要存储的值，将被封装为 RedisData 对象
     * @param time 过期时长
     * @param unit 时间单位
     */
    public void setWithLogicalExpire(String key, Object value, Long time, TimeUnit unit) {
        RedisData redisData = new RedisData();
        redisData.setData(value);
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(unit.toSeconds(time)));
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(redisData));
    }

    /**
     * 缓存穿透解决方案：查询数据库并回写缓存，空值也缓存防止穿透
     *
     * @param keyPrefix Redis 键前缀
     * @param id 业务 ID，用于拼接完整键名
     * @param type 返回值类型
     * @param dbFallback 数据库查询函数，当缓存未命中时调用
     * @param time 缓存过期时长
     * @param unit 时间单位
     * @return R 查询结果，如果数据库也未找到则返回 null
     */
    public <R, ID> R queryWithPassThrough(
            String keyPrefix, ID id, Class<R> type, Function<ID, R> dbFallback, Long time, TimeUnit unit) {
        String key = keyPrefix + id;
        String json = stringRedisTemplate.opsForValue().get(key);
        if (StrUtil.isNotBlank(json)) {
            return JSONUtil.toBean(json, type);
        }
        if (json != null) {
            return null;
        }

        // 缓存未命中，查询数据库
        R r = dbFallback.apply(id);
        if (r == null) {
            // 数据库也未找到，缓存空值防止穿透
            stringRedisTemplate.opsForValue().set(key, "", CACHE_NULL_TTL, TimeUnit.MINUTES);
            return null;
        }
        this.set(key, r, time, unit);
        return r;
    }

    /**
     * 逻辑过期方案查询缓存（简化版本，默认使用店铺锁前缀）
     *
     * @param keyPrefix Redis 键前缀
     * @param id 业务 ID
     * @param type 返回值类型
     * @param dbFallback 数据库查询函数
     * @param time 逻辑过期时长
     * @param unit 时间单位
     * @return R 查询结果
     */
    public <R, ID> R queryWithLogicalExpire(
            String keyPrefix, ID id, Class<R> type, Function<ID, R> dbFallback, Long time, TimeUnit unit) {
        return queryWithLogicalExpire(keyPrefix, LOCK_SHOP_KEY, id, type, dbFallback, time, unit);
    }

    /**
     * 逻辑过期方案查询缓存：缓存命中但过期时异步重建缓存，不阻塞返回
     *
     * @param keyPrefix Redis 键前缀
     * @param lockKeyPrefix 分布式锁键前缀
     * @param id 业务 ID
     * @param type 返回值类型
     * @param dbFallback 数据库查询函数
     * @param time 逻辑过期时长
     * @param unit 时间单位
     * @return R 查询结果，可能为旧数据
     */
    public <R, ID> R queryWithLogicalExpire(
            String keyPrefix, String lockKeyPrefix, ID id, Class<R> type,
            Function<ID, R> dbFallback, Long time, TimeUnit unit) {
        String key = keyPrefix + id;
        String json = stringRedisTemplate.opsForValue().get(key);
        if (StrUtil.isBlank(json)) {
            if (json != null) {
                return null;
            }
            return queryWithMutex(keyPrefix, lockKeyPrefix, id, type, dbFallback, time, unit);
        }

        RedisData redisData = JSONUtil.toBean(json, RedisData.class);
        if (redisData == null || redisData.getData() == null || redisData.getExpireTime() == null) {
            return queryWithMutex(keyPrefix, lockKeyPrefix, id, type, dbFallback, time, unit);
        }

        R r = JSONUtil.toBean((JSONObject) redisData.getData(), type);
        if (redisData.getExpireTime().isAfter(LocalDateTime.now())) {
            return r;
        }

        // 逻辑过期，尝试重建缓存
        String lockKey = lockKeyPrefix + id;
        boolean isLock = tryLock(lockKey);
        if (isLock) {
            // 获取锁成功，异步重建缓存
            CACHE_REBUILD_EXECUTOR.submit(() -> {
                try {
                    R newR = dbFallback.apply(id);
                    if (newR == null) {
                        stringRedisTemplate.opsForValue().set(key, "", CACHE_NULL_TTL, TimeUnit.MINUTES);
                        return;
                    }
                    this.setWithLogicalExpire(key, newR, time, unit);
                } catch (Exception e) {
                    log.error("rebuild cache failed, key={}", key, e);
                } finally {
                    unlock(lockKey);
                }
            });
        }
        return r;
    }

    /**
     * 互斥锁方案查询缓存（简化版本，默认使用店铺锁前缀）
     *
     * @param keyPrefix Redis 键前缀
     * @param id 业务 ID
     * @param type 返回值类型
     * @param dbFallback 数据库查询函数
     * @param time 缓存过期时长
     * @param unit 时间单位
     * @return R 查询结果
     */
    public <R, ID> R queryWithMutex(
            String keyPrefix, ID id, Class<R> type, Function<ID, R> dbFallback, Long time, TimeUnit unit) {
        return queryWithMutex(keyPrefix, LOCK_SHOP_KEY, id, type, dbFallback, time, unit);
    }

    /**
     * 互斥锁方案查询缓存：缓存未命中时加锁确保只有一个线程查询数据库
     *
     * @param keyPrefix Redis 键前缀
     * @param lockKeyPrefix 分布式锁键前缀
     * @param id 业务 ID
     * @param type 返回值类型
     * @param dbFallback 数据库查询函数
     * @param time 缓存过期时长
     * @param unit 时间单位
     * @return R 查询结果
     */
    public <R, ID> R queryWithMutex(
            String keyPrefix, String lockKeyPrefix, ID id, Class<R> type,
            Function<ID, R> dbFallback, Long time, TimeUnit unit) {
        String key = keyPrefix + id;
        String json = stringRedisTemplate.opsForValue().get(key);
        if (StrUtil.isNotBlank(json)) {
            log.info("redis cache key={}, value={}", key, json);
            return JSONUtil.toBean(json, type);
        }
        if (json != null) {
            return null;
        }

        String lockKey = lockKeyPrefix + id;
        boolean isLock = false;
        R r;
        try {
            // 尝试获取分布式锁
            isLock = tryLock(lockKey);
            if (!isLock) {
                // 未获取到锁，休眠后重试
                Thread.sleep(50);
                return queryWithMutex(keyPrefix, lockKeyPrefix, id, type, dbFallback, time, unit);
            }

            // 获取锁成功，查询数据库
            r = dbFallback.apply(id);
            if (r == null) {
                stringRedisTemplate.opsForValue().set(key, "", CACHE_NULL_TTL, TimeUnit.MINUTES);
                return null;
            }
            this.set(key, r, time, unit);
            return r;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        } finally {
            if (isLock) {
                unlock(lockKey);
            }
        }
    }

    /**
     * 尝试获取分布式锁
     *
     * @param key 锁的键
     * @return boolean 是否成功获取锁
     */
    private boolean tryLock(String key) {
        Boolean flag = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", 10, TimeUnit.SECONDS);
        return BooleanUtil.isTrue(flag);
    }

    /**
     * 释放分布式锁
     *
     * @param key 锁的键
     */
    private void unlock(String key) {
        stringRedisTemplate.delete(key);
    }
}
