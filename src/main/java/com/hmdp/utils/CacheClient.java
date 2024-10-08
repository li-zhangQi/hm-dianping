package com.hmdp.utils;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import static com.hmdp.utils.RedisConstants.*;

/**
 * @Author: Shinsam
 * @Date: 2024/10/07/11:11
 * @Description: 缓存穿透和击穿处理工具类
 * @Notice:
 */
@Slf4j
@Configuration
public class CacheClient {
    @Resource
    private StringRedisTemplate stringRedisTemplate;

    /**
     * 序列化储存对象并设置TTL过期时间方法
     * @param key
     * @param value
     * @param time
     * @param unit
     */
    public void set(String key, Object value, Long time, TimeUnit unit) {
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(value), time, unit);
    }

    /**
     * 序列化储存对象并设置逻辑过期时间，用于解决缓存击穿
     * @param key
     * @param value
     * @param time
     * @param unit
     */
    public void setWithLogicalExpire(String key, Object value, Long time, TimeUnit unit) {
        //设置逻辑过期时间
        RedisData redisData = new RedisData();
        redisData.setData(value);
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(unit.toSeconds(time)));
        //写入Redis
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(redisData));
    }

    /**
     * 按指定key查询缓存并反序列化为指定类型，利用缓存空值解决缓存穿透问题
     * @param keyPrefix
     * @param id
     * @param type
     * @param dbFallback
     * @param time
     * @param unit
     * @return
     * @param <R>
     * @param <ID>
     */
    public <R, ID> R queryWithPassThrough(String keyPrefix, ID id, Class<R> type, Function<ID, R> dbFallback,
                                Long time, TimeUnit unit) {
        String key = keyPrefix + id;
        //1.从redis查询商铺缓存
        String json = stringRedisTemplate.opsForValue().get(key);
        //2.判断是否存在
        if (StrUtil.isNotBlank(json)) {
            //3.存在，直接返回
            return JSONUtil.toBean(json, type);
        }
        //不存在还要判断是否是空值
        if (json != null) {
            //不为null，只能是空值
            return null;
        }
        //4.不存在，根据id查询数据库。
        //R r = this.getById(id); //是一个有参有返回值的函数，可使用函数式编程传递查询等需要的函数逻辑
        R r = dbFallback.apply(id);
        //5.不存在，将空值写入Redis，返回错误
        if (r == null) {
            stringRedisTemplate.opsForValue().set(key, "", CACHE_NULL_TTL, TimeUnit.MINUTES);
            return null;
        }
        //6.存在，缓存重建，写入redis
        this.set(key, r, time, unit);
        //7.返回
        return r;
    }

    /**
     * 按指定key查询缓存并反序列化为指定类型，利用逻辑过期时间解决缓存击穿问题
     * @param keyPrefix
     * @param id
     * @param type
     * @param dbFallBack
     * @param time
     * @param unit
     * @return
     * @param <R>
     * @param <ID>
     */
    public <R, ID> R queryWithLogicalExpire(String keyPrefix, ID id, Class<R> type, Function<ID, R> dbFallBack,
                                             Long time, TimeUnit unit) {
        String key = keyPrefix + id;
        //1.从redis查询商铺缓存
        String json = stringRedisTemplate.opsForValue().get(key);
        //2.判断是否存在
        if (StrUtil.isBlank(json)) {
            //3.不存在，直接返回
            return null;
        }
        //4.命中，需要先把json反序列化为对象
        RedisData redisData = JSONUtil.toBean(json, RedisData.class);
        JSONObject data = (JSONObject) redisData.getData();
        R r = JSONUtil.toBean(data, type);
        LocalDateTime expireTime = redisData.getExpireTime();
        //5.判断是否过期
        if (expireTime.isAfter(LocalDateTime.now())) {
            //5.1.未过期，直接返回店铺信息
            return r;
        }
        //5.2.已过期,需要缓存重建
        //6.缓存重建
        //6.1.获取互斥锁
        String lockKey = LOCK_SHOP_KEY + id;
        boolean isLock = tryLock(lockKey);
        //6.2.判断是否获取锁成功
        if (isLock) {
            //TODO 获取锁成功应该再次检测redis缓存是否过期，如果存在则无需重建缓存
            //6.3.成功，开启独立线程，实现缓存重建
            CACHE_REBUILD_EXECUTOR.submit(new Runnable() {
                @Override
                public void run() {
                    //重建缓存
                    try {
                        //先查数据库
                        R r1 = dbFallBack.apply(id);
                        //写入Redis，直接使用刚刚创建的逻辑过期时间方法
                        setWithLogicalExpire(key, r1, time, unit);
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    } finally {
                        //释放锁
                        unLock(lockKey);
                    }
                }
            });

        }
        // 6.4.返回过期的商铺信息
        return r;
    }

    //创建一个有上限的线程池
    public static final ExecutorService CACHE_REBUILD_EXECUTOR  = Executors.newFixedThreadPool(10);
    //加锁
    private boolean tryLock(String key) {
        Boolean flag = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", 10, TimeUnit.SECONDS);
        return BooleanUtil.isTrue(flag);
    }
    //解锁
    private void unLock(String key) {
        stringRedisTemplate.delete(key);
    }

}
