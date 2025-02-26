package com.hmdp.utils;


import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.hmdp.dto.UserDTO;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * @Author: Shinsam
 * @Date: 2024/10/05/18:43
 * @Description: 外层拦截器，拦截一切路径完成一定操作后都放行
 * @Notice: preHandle为执行Controller前，afterCompletion为渲染了对应的视图之后执行
 */
public class RefreshTokenLoginInterceptor implements HandlerInterceptor {
    //只能通过构造函数手动注入自己new的对象
    private StringRedisTemplate stringRedisTemplate;
    public RefreshTokenLoginInterceptor(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        //1.获取请求头中的token
        String token = request.getHeader("authorization");
        if (StrUtil.isBlank(token)) {
            //4.不存在，直接放行，不用刷新有效期
            return true;
        }
        //2.基于token获取Redis中的用户
        Map<Object, Object> userMap = stringRedisTemplate.opsForHash().entries(RedisConstants.LOGIN_USER_KEY  + token);
        //3.判断用户是否存在
        if (userMap.isEmpty()) {
            //4.不存在，直接放行，不用刷新有效期
            return true;
        }
        //5.将查询到的Hash数据转为UserDTO对象
        UserDTO userDTO = BeanUtil.fillBeanWithMap(userMap, new UserDTO(), false);
        //6.存在，保存用户信息到ThreadLocal
        UserHolder.saveUser(userDTO);
        //7.刷新token有效期
        stringRedisTemplate.expire(RedisConstants.LOGIN_USER_KEY  + token, RedisConstants.LOGIN_USER_TTL, TimeUnit.MINUTES);
        //8.放行
        return true;
    }
    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {
        //移除用户
        UserHolder.removeUser();
    }
}
