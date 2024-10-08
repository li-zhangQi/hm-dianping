package com.hmdp.utils;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

/**
 * @Author: Shinsam
 * @Date: 2024/10/07/17:02
 * @Description: 全局唯一ID生成器类
 * @Notice:
 */
@Component
public class RedisIdWorker {
    /**
     * 开始时间戳
     */
    private static final long BEGIN_TIMESTAMP = 1640995200L;
    /**
     * 序列号位数
     */
    private static final int COUNT_BITS = 32;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    public long nextId(String keyPrefix) {
        //1.生成时间戳
        LocalDateTime now = LocalDateTime.now();
        long nowSecond = now.toEpochSecond(ZoneOffset.UTC);
        long timestamp = nowSecond - BEGIN_TIMESTAMP;

        //2.生成序列号
        //2.1.获取当前日期，精确到天
        String data = now.format(DateTimeFormatter.ofPattern("yyyy:MM:dd"));
        //2.2.自增长key
        Long count = stringRedisTemplate.opsForValue().increment("icr:" + keyPrefix + ":" + data);

        //3.拼接并返回
        return timestamp << COUNT_BITS | count;
    }

    //public static void main(String[] args) {
    //    //生成指定时间的时间戳
    //    LocalDateTime time = LocalDateTime.of(2022, 1, 1, 0, 0, 0);
    //    long second = time.toEpochSecond(ZoneOffset.UTC);
    //    System.out.println("second=" + second);
    //}
}

