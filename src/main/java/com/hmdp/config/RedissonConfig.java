package com.hmdp.config;

import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * @Author: Shinsam
 * @Date: 2025/03/20/21:55
 * @Description: Redisson客户端配置类
 * @Notice:
 */
@Configuration
public class RedissonConfig {
    @Bean
    public RedissonClient redissonClient() {
        //配置
        Config config = new Config();
        //config.useSingleServer().setAddress("redis://127.0.0.1:6379").setPassword("...");
        config.useSingleServer().setAddress("redis://127.0.0.1:6379");
        //创建RedissonClient对象
        return Redisson.create(config);
    }
}
