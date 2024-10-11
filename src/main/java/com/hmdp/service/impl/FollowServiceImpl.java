package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.Follow;
import com.hmdp.entity.User;
import com.hmdp.mapper.FollowMapper;
import com.hmdp.service.IFollowService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.UserHolder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class FollowServiceImpl extends ServiceImpl<FollowMapper, Follow> implements IFollowService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Autowired
    private UserServiceImpl userService;

    /**
     * 关注和取关用户 - 将关注信息放入Redis
     * @param followUserId
     * @param isFollow 要关注传true,取关传false
     * @return
     */
    @Override
    public Result follow(Long followUserId, Boolean isFollow) {
        //获取当前登录用户Id
        Long userId = UserHolder.getUser().getId();
        String key = "follows:" + userId;
        //1.判断到底是关注还是取关
        if (isFollow) {
            //2.关注，新增数据
            Follow follow = new Follow();
            follow.setFollowUserId(followUserId);
            follow.setUserId(userId);
            boolean isSuccess = this.save(follow);
            if (isSuccess) {
                //把关注用户的id，放入redis的set集合
                stringRedisTemplate.opsForSet().add(key, followUserId.toString());
            }
        } else {
            //3.取关，删除
            LambdaQueryWrapper<Follow> queryWrapper = new LambdaQueryWrapper<>();
            queryWrapper.eq(Follow::getUserId, userId);
            queryWrapper.eq(Follow::getFollowUserId, followUserId);
            boolean isSuccess = this.remove(queryWrapper);
            if (isSuccess) {
                //把关注用户的id，从redis的set集合删除
                stringRedisTemplate.opsForSet().remove(key, followUserId.toString());
            }
        }
        return Result.ok();
    }
    /*@Override
    public Result follow(Long followUserId, Boolean isFollow) {
        //获取当前登录用户Id
        Long userId = UserHolder.getUser().getId();
        //1.判断到底是关注还是取关
        if (isFollow) {
            //2.关注，新增数据
            Follow follow = new Follow();
            follow.setFollowUserId(followUserId);
            follow.setUserId(userId);
            this.save(follow);
        } else {
            //3.取关，删除
            LambdaQueryWrapper<Follow> queryWrapper = new LambdaQueryWrapper<>();
            queryWrapper.eq(Follow::getUserId, userId);
            queryWrapper.eq(Follow::getFollowUserId, followUserId);
            this.remove(queryWrapper);
        }
        return Result.ok();
    }*/

    /**
     * 展示关注和取关状态
     * @param followUserId
     * @return
     */
    @Override
    public Result isfollow(Long followUserId) {
        Long userId = UserHolder.getUser().getId();
        LambdaQueryWrapper<Follow> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(Follow::getUserId, userId);
        queryWrapper.eq(Follow::getFollowUserId, followUserId);
        int count = this.count(queryWrapper);

        //count大于0表示已关注
        return Result.ok(count > 0);
    }

    /**
     * 查询共同关注
     * @param id 目标用户id
     * @return
     */
    @Override
    public Result followCommons(Long id) {
        //查询当前用户Id
        Long userId = UserHolder.getUser().getId();
        String key1 = "follows:" + userId;
        String key2 = "follows:" + id;
        //通过Set结构查询Redis中两个集合的并集
        Set<String> intersect = stringRedisTemplate.opsForSet().intersect(key1, key2);
        if (intersect == null || intersect.isEmpty()) {
            //为空,无交集，则返回一个空集合集合
            return Result.ok(Collections.emptyList());
        }
        //不为空，解析Id集合
        List<Long> ids = intersect.stream().map(new Function<String, Long>() {
            @Override
            public Long apply(String s) {
                return Long.valueOf(s);
            }
        }).collect(Collectors.toList());
        //查询用户,并用DTO对象传输给前端
        List<UserDTO> userDTOS = userService.listByIds(ids).stream().map(new Function<User, UserDTO>() {
            @Override
            public UserDTO apply(User user) {
                return BeanUtil.copyProperties(user, UserDTO.class);
            }
        }).collect(Collectors.toList());
        return Result.ok(userDTOS);
    }
}





















