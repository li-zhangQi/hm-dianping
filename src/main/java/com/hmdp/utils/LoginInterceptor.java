package com.hmdp.utils;


import com.hmdp.dto.UserDTO;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * @Author: Shinsam
 * @Date: 2024/10/05/18:43
 * @Description: 拦截器类 - 改为内层拦截器，真正拦截请求
 * @Notice: preHandle为执行Controller前，afterCompletion为渲染了对应的视图之后执行
 */
public class LoginInterceptor implements HandlerInterceptor {


    /*@Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        //1.获取session
        HttpSession session = request.getSession();
        //2.获取session中的用户
        Object user = session.getAttribute("user");
        //3.判断用户是否存在
        if (user == null) {
            //4.不存在，拦截，返回401错误码
            response.setStatus(401);
            return false;
        }
        //5.存在，保存用户信息到ThreadLocal
        UserHolder.saveUser((UserDTO) user);
        //6.放行
        return true;
    }*/
    /*@Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        //1.获取请求头中的token
        String token = request.getHeader("authorization");
        if (StrUtil.isBlank(token)) {
            //4.不存在，拦截，返回401错误码
            response.setStatus(401);
            return false;
        }
        //2.基于token获取Redis中的用户
        Map<Object, Object> userMap = stringRedisTemplate.opsForHash().entries(RedisConstants.LOGIN_USER_KEY  + token);
        //3.判断用户是否存在
        if (userMap.isEmpty()) {
            //4.不存在，拦截，返回401错误码
            response.setStatus(401);
            return false;
        }
        //5.将查询到的Hash数据转为UserDTO对象
        UserDTO userDTO = BeanUtil.fillBeanWithMap(userMap, new UserDTO(), false);
        //6.存在，保存用户信息到ThreadLocal
        UserHolder.saveUser(userDTO);
        //7.刷新token有效期
        stringRedisTemplate.expire(RedisConstants.LOGIN_USER_KEY  + token, RedisConstants.LOGIN_USER_TTL, TimeUnit.MINUTES);
        //8.放行
        return true;
    }*/
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {

        //1.判断是否需要拦截(ThreadLocal中是否有用户)
        UserDTO userDTO = UserHolder.getUser();
        if (userDTO == null) {
            //没有，需要拦截
            response.setStatus(401);
            return false;
        }

        //有用户，放行
        return true;
    }
}
