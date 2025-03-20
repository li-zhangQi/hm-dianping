package com.hmdp.utils;

/**
 * @Author: Shinsam
 * @Date: 2025/03/20/10:53
 * @Description: 分布式锁接口
 * @Notice:
 */
public interface ILock {

    /**
     * 尝试获取锁
     * @param timeoutSec 锁持有的超时时间，过期后自动释放
     * @return true代表获取锁成功，false代表获取锁失败
     */
    boolean tryLock(long timeoutSec);

    /**
     * 释放锁123
     */
    void unlock();
}
