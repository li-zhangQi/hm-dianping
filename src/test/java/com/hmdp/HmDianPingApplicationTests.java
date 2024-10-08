package com.hmdp;

import com.hmdp.entity.Shop;
import com.hmdp.service.impl.ShopServiceImpl;
import com.hmdp.utils.CacheClient;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.RedisIdWorker;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.CACHE_SHOP_KEY;

@RunWith(SpringJUnit4ClassRunner.class)
@SpringBootTest
public class HmDianPingApplicationTests {
    @Autowired
    private ShopServiceImpl shopService;

    @Autowired
    private CacheClient cacheClient;

    @Autowired
    private RedisIdWorker redisIdWorker;

    /*@Test
    public void testSavaShop2Redis() throws InterruptedException {
        shopService.saveShop2Redis(1L, 10L);
    }*/

    @Test
    public void testSetWithLogicalExpire() {
        Shop shop = shopService.getById(1L);
        cacheClient.setWithLogicalExpire(CACHE_SHOP_KEY + 1L, shop, 10L, TimeUnit.SECONDS);
    }


    //测试生成的全局ID
    //1创建线程池-
    private ExecutorService es = Executors.newFixedThreadPool(500);

    @Test
    public void testIdWorker() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(300);
        //2创建任务-为设置100个Id的
        Runnable task = new Runnable() {
            @Override
            public void run() {
                for (int i = 0; i < 100; i++) {
                    long id = redisIdWorker.nextId("order");
                    System.out.println("id = " + id);
                }
            }
        };
        latch.countDown();
        long begin = System.currentTimeMillis();
        //3提交任务-为300个
        for (int i = 0; i < 300; i++) {
            es.submit(task);
        }
        latch.await();
        long end = System.currentTimeMillis();
        System.out.println("time = " + (end - begin));
    }
}
