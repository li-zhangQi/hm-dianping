package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.CacheClient;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.RedisData;
import com.hmdp.utils.SystemConstants;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.geo.Distance;
import org.springframework.data.geo.GeoResult;
import org.springframework.data.geo.GeoResults;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.domain.geo.GeoReference;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.*;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Autowired
    private CacheClient cacheClient;

    //创建一个有上限的线程池
    //public static final ExecutorService CACHE_REBUILD_EXECUTOR  = Executors.newFixedThreadPool(10);

    /**
     * 依据id查询商店信息-增加缓存超时剔除 - 解决缓存穿透问题 - 1解决缓存击穿问题 - 2逻辑过期解决缓存击穿（数据已预热，强制不让其击穿直接返回）
     * @param id
     * @return
     */
    /*@Override
    public Result queryById(Long id) {
        String key = CACHE_SHOP_KEY + id;
        //1.从redis查询商铺缓存
        String shopCache = stringRedisTemplate.opsForValue().get(key);
        //2.判断是否存在
        if (StrUtil.isNotBlank(shopCache)) {
            //3.存在，直接返回
            Shop shop = JSONUtil.toBean(shopCache, Shop.class);
            return Result.ok(shop);
        }
        //4.不存在，根据id查询数据库
        Shop shop = this.getById(id);
        //5.不存在，返回错误
        if (shop == null) {
            return Result.fail("商铺不存在！");
        }
        //6.存在，写入redis --超时剔除
        //stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(shop));
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(shop), CACHE_SHOP_TTL, TimeUnit.MINUTES);
        //7.返回
        return Result.ok(shop);
    }*/
    /* @Override
    public Result queryById(Long id) {
        String key = CACHE_SHOP_KEY + id;
        //1.从redis查询商铺缓存
        String shopCache = stringRedisTemplate.opsForValue().get(key);
        //2.判断是否存在
        if (StrUtil.isNotBlank(shopCache)) {
            //3.存在，直接返回
            Shop shop = JSONUtil.toBean(shopCache, Shop.class);
            return Result.ok(shop);
        }
        //不存在还要判断是否是空值
        if (shopCache != null) {
            //不为null，只能是空值
            return Result.fail("店铺信息不存在!");
        }
        //4.不存在，根据id查询数据库
        Shop shop = this.getById(id);
        //5.不存在，将空值写入Redis，返回错误
        if (shop == null) {
            stringRedisTemplate.opsForValue().set(key, "", CACHE_NULL_TTL, TimeUnit.MINUTES);
            return Result.fail("商铺不存在！");
        }
        //6.存在，写入redis --超时剔除
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(shop), CACHE_SHOP_TTL, TimeUnit.MINUTES);
        //7.返回
        return Result.ok(shop);
    }*/
    /*@Override
    public Result queryById(Long id) {
        String key = CACHE_SHOP_KEY + id;
        //1.从redis查询商铺缓存
        String shopCache = stringRedisTemplate.opsForValue().get(key);
        //2.判断是否存在
        if (StrUtil.isNotBlank(shopCache)) {
            //3.存在，直接返回
            Shop shop = JSONUtil.toBean(shopCache, Shop.class);
            return Result.ok(shop);
        }
        //不存在还要判断是否是空值
        if (shopCache != null) {
            //不为null，只能是空值
            return Result.fail("店铺信息不存在!");
        }
         //4.实现缓存重建
         //4.1．获取互斥锁
         String lockKey = "lock:shop:" + id;
         Shop shop = null;
         try {
             boolean isLock = tryLock(lockKey);
             //4.2.判断是否获取成功
             if (!isLock) {
                 // TODO 4.3.失败，则休眠并重试
                 Thread.sleep(50);
                 //递归再次尝试获取锁（此方式有很大缺点）
                 return queryById(id);
             }
             // TODO 4.5.获取锁成功应该再次检测redis缓存是否存在，如果存在则无需重建缓存
             //if (StrUtil.isNotBlank(shopCache)) {
             //    //3.存在，直接返回
             //    shop = JSONUtil.toBean(shopCache, Shop.class);
             //    return Result.ok(shop);
             //}
             //根据id查询数据库
             shop = this.getById(id);
             //模拟重建Redis的延时
             Thread.sleep(200);
             //5.不存在，将空值写入Redis，返回错误
             if (shop == null) {
                 stringRedisTemplate.opsForValue().set(key, "", CACHE_NULL_TTL, TimeUnit.MINUTES);
                 return Result.fail("商铺不存在！");
             }
             //6.存在，写入redis --超时剔除
             stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(shop), CACHE_SHOP_TTL, TimeUnit.MINUTES);
         } catch (InterruptedException e) {
             throw new RuntimeException(e);
         } finally {
             //7.释放互斥锁
             unLock(lockKey);
         }
        //8.返回
        return Result.ok(shop);
    }*/
    /*@Override
    public Result queryById(Long id) {
        String key = CACHE_SHOP_KEY + id;
        //1.从redis查询商铺缓存
        String shopCache = stringRedisTemplate.opsForValue().get(key);
        //2.判断是否存在
        if (StrUtil.isBlank(shopCache)) {
            //3.不存在，直接返回
            return Result.fail("店铺信息不能为空！");
        }
        //4.命中，需要先把json反序列化为对象
        RedisData redisData = JSONUtil.toBean(shopCache, RedisData.class);
        JSONObject data = (JSONObject) redisData.getData();
        Shop shop = JSONUtil.toBean(data, Shop.class);
        LocalDateTime expireTime = redisData.getExpireTime();
        //5.判断是否过期
        if (expireTime.isAfter(LocalDateTime.now())) {
            //5.1.未过期，直接返回店铺信息
            return Result.ok(shop);
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
                        saveShop2Redis(id, 20L);
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
        return Result.ok(shop);
    }*/
    @Override
    public Result queryById(Long id) {

        //直接使用工具类封装的方法来解决缓存穿透问题
        //Shop shop = cacheClient.queryWithPassThrough(CACHE_SHOP_KEY, id, Shop.class, this::getById, CACHE_SHOP_TTL, TimeUnit.MINUTES);

        //直接使用工具类封装的方法来 解决缓存击穿问题
        Shop shop = cacheClient.queryWithLogicalExpire(CACHE_SHOP_KEY, id, Shop.class, this::getById, 20L, TimeUnit.SECONDS);

        if (shop == null) {
            return Result.fail("店铺不存在！");
        }
        return Result.ok(shop);
    }

    //加锁
    //private boolean tryLock(String key) {
    //    Boolean flag = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", 10, TimeUnit.SECONDS);
    //    return BooleanUtil.isTrue(flag);
    //}
    //解锁
    //private void unLock(String key) {
    //     stringRedisTemplate.delete(key);
    //}

    //缓存重建
    //public void saveShop2Redis(Long id, Long expireSeconds) throws InterruptedException {
    //    //查询店铺数据
    //    Shop shop = this.getById(id);
    //    //（模拟缓存重建延迟）
    //    Thread.sleep(200L);
    //    //封装逻辑过期时间
    //    RedisData redisData = new RedisData();
    //    redisData.setExpireTime(LocalDateTime.now().plusSeconds(expireSeconds));
    //    redisData.setData(shop);
    //    //写入Redis
    //    stringRedisTemplate.opsForValue().set(CACHE_SHOP_KEY + id, JSONUtil.toJsonStr(redisData));
    //}

    /**
     * 依据id跟新商店信息 - 增加主动更新缓存策略
     * @param shop
     * @return
     */
    @Transactional
    @Override
    public Result update(Shop shop) {
        Long id = shop.getId();
        if (id == null) {
            return Result.fail("商店id不能为空！");
        }

        //先更新数据库
        this.updateById(shop);

        //再删除缓存
        stringRedisTemplate.delete(CACHE_SHOP_KEY + id);
        return Result.ok();
    }

    ///**
    // * 根据商铺名称关键字分页查询商铺信息
    // * @param typeId
    // * @param current
    // * @param x
    // * @param y
    // * @return
    // */
    /*@Override
    public Result queryShopByType(Integer typeId, Integer current, Double x, Double y) {
        //1.判断是否需要根据坐标查询
        if (x == null || y == null) {
            //不需要坐标查询，按数据库查询
            // 根据类型分页查询
            Page<Shop> page = this.query()
                    .eq("type_id", typeId)
                    .page(new Page<>(current, SystemConstants.DEFAULT_PAGE_SIZE));
            // 返回数据
            return Result.ok(page.getRecords());
        }
        //2.计算分页参数
        int from = (current - 1) * SystemConstants.DEFAULT_PAGE_SIZE;
        int end = current * SystemConstants.DEFAULT_PAGE_SIZE;
        //3.查询redis、按照距离排序、分页。结果: shopId、distance
        //按店铺类型分类
        String key = "shop:geo:" + typeId;
        GeoResults<RedisGeoCommands.GeoLocation<String>> results = stringRedisTemplate.opsForGeo() //GEOSEARCH key BYLONLAT x y BYRADIUS 10 WITHDISTANCE
                .search(
                        key,
                        GeoReference.fromCoordinate(x, y),
                        new Distance(5000),
                        RedisGeoCommands.GeoSearchCommandArgs.newGeoSearchArgs().includeDistance().limit(end)
                );
        //4.解析出id
        if (results == null) {
            return Result.ok(Collections.emptyList());
        }
        List<GeoResult<RedisGeoCommands.GeoLocation<String>>> list = results.getContent();
        if (list.size() <= from) {
            //没有下一页了，结束
            return Result.ok(Collections.emptyList());
        }
        //4.1.截取from ~ end的部分
        List<Long> ids = new ArrayList<>(list.size());
        Map<String, Distance> distanceMap = new HashMap<>(list.size());
        list.stream().skip(from).forEach(result -> {
            //4.2.获取店铺id
            String shopStr = result.getContent().getName();
            ids.add(Long.valueOf(shopStr));
            //4.2.获取距离
            Distance distance = result.getDistance();
            distanceMap.put(shopStr, distance);
        });
        //5.根据id查询Shop,保证有序
        String idStr = StrUtil.join(",", ids);
        List<Shop> shops = this.lambdaQuery().in(Shop::getId, ids).last("ORDER BY FIELD(id, " + idStr + ")").list();
        //将店铺与距离关联
        for (Shop shop : shops) {
            shop.setDistance(distanceMap.get(shop.getId().toString()).getValue());
        }
        //6.返国
        return Result.ok(shops);
    }*/

}
