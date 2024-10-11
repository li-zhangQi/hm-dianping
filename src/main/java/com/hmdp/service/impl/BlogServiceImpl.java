package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hmdp.dto.Result;
import com.hmdp.dto.ScrollResult;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.Blog;
import com.hmdp.entity.Follow;
import com.hmdp.entity.User;
import com.hmdp.mapper.BlogMapper;
import com.hmdp.service.IBlogService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.service.IFollowService;
import com.hmdp.service.IUserService;
import com.hmdp.utils.SystemConstants;
import com.hmdp.utils.UserHolder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.ArrayList;
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
public class BlogServiceImpl extends ServiceImpl<BlogMapper, Blog> implements IBlogService {

    @Resource
    private IUserService userService;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Autowired
    private IFollowService followService;

    @Override
    public Result queryHotBlog(Integer current) {
        // 根据用户查询
        Page<Blog> page = this.query()
                .orderByDesc("liked")
                .page(new Page<>(current, SystemConstants.MAX_PAGE_SIZE));
        // 获取当前页数据
        List<Blog> records = page.getRecords();
        //// 查询用户
        //records.forEach(blog ->{
        //    Long userId = blog.getUserId();
        //    User user = userService.getById(userId);
        //    blog.setName(user.getNickName());
        //    blog.setIcon(user.getIcon());
        //});
        //// 查询用户
        //records.forEach(this::queryBlogUser);
        // 查询用户和点赞信息
        records.forEach(blog -> {
            this.queryBlogUser(blog);
            this.isBlogLiked(blog);
        });
        return Result.ok(records);
    }

    /**
     * 查看探店笔记
     * @param id
     * @return
     */
    @Override
    public Result queryBlogById(Long id) {
        Blog blog = this.getById(id);
        if (blog == null) {
            Result.fail("笔记不存在！");
        }
        queryBlogUser(blog);
        //3.查询blog是否被当前用户点赞了，若是则给Blog的isLike字段设置相应值
        isBlogLiked(blog);

        return Result.ok(blog);
    }

    private void isBlogLiked(Blog blog) {
        //先判断用户是否登录
        UserDTO userDTO = UserHolder.getUser();
        if (userDTO == null) {
            //用户未登录，无需查询是否当前用户点赞
            return;
        }
        //1.获取登录用户
        Long userId = UserHolder.getUser().getId();
        //2.判断当前登录用户是否已经点赞 - SortedSet结构数据操作
        String key = "blog:liked:" + blog.getId();
        Double score = stringRedisTemplate.opsForZSet().score(key, userId.toString());
        blog.setIsLike(score != null);
    }
    /*private void isBlogLiked(Blog blog) {
        //1.获取登录用户
        Long userId = UserHolder.getUser().getId();
        //2.判断当前登录用户是否已经点赞 - set结构数据操作
        String key = "blog:liked:" + blog.getId();
        Boolean isMember = stringRedisTemplate.opsForSet().isMember(key, userId.toString());
        boolean isLiked = BooleanUtil.isTrue(isMember);
        blog.setIsLike(isLiked);
    }*/

    private void queryBlogUser(Blog blog) {
        Long userId = blog.getUserId();
        User user = userService.getById(userId);
        blog.setName(user.getNickName());
        blog.setIcon(user.getIcon());
    }

    /**
     * 笔记点赞 - 增加点赞排行榜
     * @param id
     * @return
     */
    @Override
    public Result likeBlog(Long id) {
        //1.获取登录用户
        Long userId = UserHolder.getUser().getId();
        //2.判断当前登录用户是否已经点赞 - SortedSet结构数据操作
        String key = "blog:liked:" + id;
        Double score = stringRedisTemplate.opsForZSet().score(key, userId.toString());

        if (score == null) {
            //3.如果未点赞，可以点赞
            //3.1.数据库点赞数+1
            LambdaUpdateWrapper<Blog> updateWrapper = new LambdaUpdateWrapper<>();
            updateWrapper.eq(Blog::getId, id);
            updateWrapper.setSql("liked = liked + 1");
            boolean isSuccess = this.update(updateWrapper);
            //3.2.保存用户到Redis的SortedSet集合，按时间戳排序
            if (isSuccess) {
                stringRedisTemplate.opsForZSet().add(key, userId.toString(), System.currentTimeMillis());
            }
        } else {
            //4.如果已点赞，取消点赞
            //4.1.数据库点赞数一1
            LambdaUpdateWrapper<Blog> updateWrapper = new LambdaUpdateWrapper<>();
            updateWrapper.eq(Blog::getId, id);
            updateWrapper.setSql("liked = liked - 1");
            boolean isSuccess = this.update(updateWrapper);
            //4.2.把用户从Redis的SortedSet集合移除
            if (isSuccess) {
                stringRedisTemplate.opsForZSet().remove(key, userId.toString());
            }
        }
        return Result.ok();
    }
    /*@Override
    public Result likeBlog(Long id) {
        //1.获取登录用户
        Long userId = UserHolder.getUser().getId();
        //2.判断当前登录用户是否已经点赞 - set结构数据操作
        String key = "blog:liked:" + id;
        Boolean isMember = stringRedisTemplate.opsForSet().isMember(key, userId.toString());

        if (BooleanUtil.isFalse(isMember)) {
            //3.如果未点赞，可以点赞
            //3.1.数据库点赞数+1
            LambdaUpdateWrapper<Blog> updateWrapper = new LambdaUpdateWrapper<>();
            updateWrapper.eq(Blog::getId, id);
            updateWrapper.setSql("liked = liked + 1");
            boolean isSuccess = this.update(updateWrapper);
            //3.2.保存用户到Redis的set集合
            if (isSuccess) {
                stringRedisTemplate.opsForSet().add(key, userId.toString());
            }
        } else {
            //4.如果已点赞，取消点赞
            //4.1.数据库点赞数一1
            LambdaUpdateWrapper<Blog> updateWrapper = new LambdaUpdateWrapper<>();
            updateWrapper.eq(Blog::getId, id);
            updateWrapper.setSql("liked = liked - 1");
            boolean isSuccess = this.update(updateWrapper);
            //4.2.把用户从Redis的set集合移除
            if (isSuccess) {
                stringRedisTemplate.opsForSet().remove(key, userId.toString());
            }
        }
        return Result.ok();
    }*/

    /**
     * 查询前5个点赞用户信息
     * @param id
     * @return
     */
    @Override
    public Result queryBlogLikes(Long id) {
        String key = "blog:liked:" + id;
        //1.查询top5的点赞用户 zrange key o 4
        Set<String> top5 = stringRedisTemplate.opsForZSet().range(key, 0, 4);
        if (top5 == null || top5.isEmpty()) {
            return Result.ok(Collections.emptyList());
        }
        //2.解析出其中的用户id。String —> Long
        List<Long> ids = top5.stream().map(new Function<String, Long>() {
            @Override
            public Long apply(String s) {
                return Long.valueOf(s);
            }
        }).collect(Collectors.toList());
        //3.根据用户id查询用户。数据库的排序还得特别注意，WHERE id IN ( 5 , 1 ) ORDER BY FIELD(id，5，1).最后拼接手写field排序
        //List<User> users = userService.listByIds(ids);
        String idStr = StrUtil.join(",", ids);
        List<User> users = userService.lambdaQuery().in(User::getId, ids).last("ORDER BY FIELD(id, "+ idStr +")").list();
        //不让特殊信息泄露，处理成为DTO对象
        List<UserDTO> userDTO = users.stream().map(new Function<User, UserDTO>() {
            @Override
            public UserDTO apply(User user) {
                return BeanUtil.copyProperties(user, UserDTO.class);
            }
        }).collect(Collectors.toList());
        //4。返回
        return Result.ok(userDTO);
    }

    /**
     * 基于Feed的推模式实现笔记推送功能，且使用SortedSet实现滚动分页
     * @param blog
     * @return
     */
    @Override
    public Result saveBlog(Blog blog) {
        //1.获取登录用户
        UserDTO userDTO = UserHolder.getUser();
        blog.setUserId(userDTO.getId());
        //2.保存探店笔记
        boolean isSuccess = this.save(blog);
        if (!isSuccess) {
            return Result.fail("新增笔记失败");
        }
        //3.查询当前笔记作者的所有粉丝
        LambdaQueryWrapper<Follow> queryWrapper = new LambdaQueryWrapper<>();
        //FollowUserId为被关注者的id
        queryWrapper.eq(Follow::getFollowUserId, userDTO.getId());
        List<Follow> follows = followService.list(queryWrapper);
        //4.推送笔记id给所有粉丝
        for (Follow follow : follows) {
            //4.1.获取粉丝id
            Long userId = follow.getUserId();
            //4.2.推送
            stringRedisTemplate.opsForZSet().add("feed:" + userId, blog.getId().toString(), System.currentTimeMillis());
        }
        //5.返回id
        return Result.ok(blog.getId());
    }

    /**
     * 滚动分页查询笔记，基于SortedSet实现
     * @param max
     * @param offset
     * @return
     */
    @Override
    public Result queryBlogOfFollow(Long max, Integer offset) {
        //1.获取当前用户
        Long userId = UserHolder.getUser().getId();
        //2.查询收件箱.ZREVRANGEBYSCORE key Min Max  LIMIT offset count
        String key = "feed:" + userId;
        Set<ZSetOperations.TypedTuple<String>> typedTuples = stringRedisTemplate.opsForZSet()
                .reverseRangeByScoreWithScores(key, 0, max, offset, 2);
        //3.判断非空
        if (typedTuples == null || typedTuples.isEmpty()) {
            return Result.ok();
        }
        //3。解析数据并暂存: blogId、minTime (时间戳）、offset
        List<Long> ids  = new ArrayList<>(typedTuples.size());
        long minTime = 0; //(维持最小时间)
        int os = 1; //(offset偏移量最小为1，往后再看情况增加)
        for (ZSetOperations.TypedTuple<String> typedTuple : typedTuples) {
            //获取笔记id
            String idStr = typedTuple.getValue();
            ids.add(Long.valueOf(idStr));
            //获取分数，即时间戳
            long time = typedTuple.getScore().longValue();
            if (time == minTime) {
                //获取的时间与最小值一样,偏移量加一次
                os++;
            } else {
                //不一致则新取得的时间最小，偏移量重置为1
                minTime = time;
                os = 1;
            }

        }
        //4.根据id查询blog，需要查询出来的数据有序
        String idStr = StrUtil.join(",", ids);
        List<Blog> blogs = this.lambdaQuery().in(Blog::getId, ids).last("ORDER BY FIELD(id, " + idStr + ")").list();
        //每一个blog都得展示用户信息和是否点赞的信息
        for (Blog blog : blogs) {
            queryBlogUser(blog);
            isBlogLiked(blog);
        }
        //5.封装并返回
        ScrollResult rt = new ScrollResult();
        rt.setList(blogs);
        rt.setMinTime(minTime);
        rt.setOffset(os);
        return Result.ok(rt);
    }
}














