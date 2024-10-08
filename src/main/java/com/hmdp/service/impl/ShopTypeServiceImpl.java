package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.ShopType;
import com.hmdp.mapper.ShopTypeMapper;
import com.hmdp.service.IShopTypeService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.HashMap;
import java.util.List;

import static com.hmdp.utils.RedisConstants.CACHE_SHOPTYPE_KEY;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class ShopTypeServiceImpl extends ServiceImpl<ShopTypeMapper, ShopType> implements IShopTypeService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    /**
     * 查询商铺分类信息-用Redis做缓存
     * @return
     */
    @Override
    public Result queryTypeList() {
        String key = CACHE_SHOPTYPE_KEY;
        //尝试从缓存中读取数据
        String cache = stringRedisTemplate.opsForValue().get(key);
        if (StrUtil.isNotBlank(cache)) {
            //存在则获取出来
            //反序列化集合中的对象字符串
            List<ShopType> shopTypes = JSONUtil.toList(cache, ShopType.class);
            return Result.ok(shopTypes);
        }
        //不存在，从数据库中读取
        List<ShopType> shopTypes = this.list();
        if (shopTypes == null) {
            //为空返回错误
            return Result.fail("商铺分类错误！");
        }
        //不为空，写入到Redis中
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(shopTypes));
        //返回
        return Result.ok(shopTypes);

    }
}
