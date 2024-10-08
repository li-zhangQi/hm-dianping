package com.hmdp.config;

import com.hmdp.utils.LoginInterceptor;
import com.hmdp.utils.RefreshTokenLoginInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import javax.annotation.Resource;

/**
 * @Author: Shinsam
 * @Date: 2024/10/05/19:04
 * @Description: 拦截器配置给予使用 - 增加外层拦截器
 * @Notice:
 */
@Configuration
public class MvcConfig implements WebMvcConfigurer {

    //最终由此自动注入并传递
    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        //登录拦截器
        registry.addInterceptor(new LoginInterceptor()).excludePathPatterns(
                "/shop/**",
                "/voucher/**",
                "/shop-type/**",
                "/upload/**",
                "/blog/hot",
                "/user/code",
                "/user/login"
        ).order(1);
        //token刷新拦截器
        registry.addInterceptor(new RefreshTokenLoginInterceptor(stringRedisTemplate)).addPathPatterns("/**").order(0);
    }
}
