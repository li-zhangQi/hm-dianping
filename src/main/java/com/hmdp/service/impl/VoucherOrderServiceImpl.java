package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.hmdp.dto.Result;
import com.hmdp.entity.SeckillVoucher;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherOrderService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisIdWorker;
import com.hmdp.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.aop.framework.AopContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.connection.stream.*;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.stream.Collector;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Slf4j
@Service
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {

    @Autowired
    private ISeckillVoucherService seckillVoucherService;
    @Autowired
    private RedisIdWorker redisIdWorker;
    @Resource
    private StringRedisTemplate stringRedisTemplate;

    //指定Lua脚本
    private static final DefaultRedisScript<Long> SECKILL_SCRIPT;
    static {
        SECKILL_SCRIPT = new DefaultRedisScript<>();
        SECKILL_SCRIPT.setLocation(new ClassPathResource("seckill.lua"));
        SECKILL_SCRIPT.setResultType(Long.class);
    }


    //单线程处理异步下单
    private static final ExecutorService SECKILL_ORDER_EXECUTOR = Executors.newSingleThreadExecutor();

    //类初始化后立即执行，循环获取任务
    @PostConstruct
    private void init() {
        SECKILL_ORDER_EXECUTOR.submit(tasks);
    }
    //消费者组消费
    Runnable tasks = new Runnable() {
        String queueName = "stream.orders";
        @Override
        public void run() {
            while(true) {
                try {
                    //1.获取消息队列中的订单信息，XREADGROUP GROUP g1 c1 COUNT 1 BLOCK 2000 STREAMS streams.orders >
                    List<MapRecord<String, Object, Object>> list = stringRedisTemplate.opsForStream().read(
                            //组属于消费者信息
                            Consumer.from("g1", "c1"),
                            StreamReadOptions.empty().count(1).block(Duration.ofSeconds(2)),
                            StreamOffset.create(queueName, ReadOffset.lastConsumed())
                    );
                    //2.判断消息获取是否成功
                    if (list == null || list.isEmpty()) {
                        //2.1.如果获取失败，说明没有消息，继续下一次循环
                        continue;
                    }
                    //3.解析消息中的订单信息，只有一个需要拿的。且lua脚本中，给消息队列发送的数据为key-value，所以为map集合
                    MapRecord<String, Object, Object> record = list.get(0);
                    Map<Object, Object> values = record.getValue();
                    VoucherOrder voucherOrder = BeanUtil.fillBeanWithMap(values, new VoucherOrder(), false);
                    //数据库中创建真正的订单记录
                    //4.如果获取成功，可以下单
                    handleVoucherOrder(voucherOrder);
                    //5.ACK确认， SACK stream.orders g1 id
                    stringRedisTemplate.opsForStream().acknowledge(queueName, "g1", record.getId());

                } catch (Exception e) {
                    log.error("处理订单异常！", e);
                    //有异常消息时的处理方法
                    handlePendingList();
                }
            }
        }

        //消息处理异常即为有消费完的消息但未确认。大致同上，但获取消息队列里面的第一个元素进行再确认即可
        private void handlePendingList() {
            while(true) {
                try {
                    //1.获取pending-list中的订单信息，XREADGROUP GROUP g1 c1 COUNT 1 BLOCK 2000 STREAMS streams.orders 0
                    List<MapRecord<String, Object, Object>> list = stringRedisTemplate.opsForStream().read(
                            //组属于消费者信息
                            Consumer.from("g1", "c1"),
                            StreamReadOptions.empty().count(1),
                            StreamOffset.create(queueName, ReadOffset.from("0"))
                    );
                    //2.判断消息获取是否成功
                    if (list == null || list.isEmpty()) {
                        //2.1.如果获取失败，说明pending-list没有异常消息，结束循环
                        break;
                    }
                    //3.解析消息中的订单信息，只有一个需要拿的。且lua脚本中，给消息队列发送的数据为key-value，所以为map集合
                    MapRecord<String, Object, Object> record = list.get(0);
                    Map<Object, Object> values = record.getValue();
                    VoucherOrder voucherOrder = BeanUtil.fillBeanWithMap(values, new VoucherOrder(), false);
                    //数据库中创建真正的订单记录
                    //4.如果获取成功，可以下单
                    handleVoucherOrder(voucherOrder);
                    //5.ACK确认， SACK stream.orders g1 id
                    stringRedisTemplate.opsForStream().acknowledge(queueName, "g1", record.getId());
                } catch (Exception e) {
                    //还有异常继续下次循环处理
                    log.error("处理pending-list异常！", e);
                    //适当休眠
                    try {
                        Thread.sleep(20);
                    } catch (InterruptedException ex) {
                        ex.printStackTrace();
                    }
                }
            }
        }
    };

    /*//阻塞队列
    private BlockingQueue<VoucherOrder> orderTasks = new ArrayBlockingQueue<>(1024 * 1024);
    Runnable tasks = new Runnable() {
        @Override
        public void run() {
            while(true) {
                try {
                    //获取队列中的订单信息，使用阻塞方法
                    VoucherOrder voucherOrder = orderTasks.take();
                    //数据库中创建真正的订单记录
                    handleVoucherOrder(voucherOrder);
                } catch (Exception e) {
                    log.error("处理订单异常！", e);
                }

            }
        }
    };*/

    private void handleVoucherOrder(VoucherOrder voucherOrder) {
        Long userId = voucherOrder.getUserId();
        synchronized (userId.toString().intern()) {
            //抢购时订单已下单，不需要再下单
            proxy.creatVoucherOrder(voucherOrder);
        }
    }


    /**
     * 根据优惠券Id下单秒杀优惠券 - 使用Lua脚本优化 - 使用Stream消息队列消费者组继续优化
     * @param voucherId
     * @return
     */
    //将代理对象设置为成员变量,多线程也能使用
    private IVoucherOrderService proxy;

    @Override
    public Result seckillVoucher(Long voucherId) {
        Long userId = UserHolder.getUser().getId();
        //设置订单ID，使用自建的全局ID生成器类
        long orderId = redisIdWorker.nextId("order");
        //1.执行lua脚本
        Long result = stringRedisTemplate.execute(
                SECKILL_SCRIPT,
                Collections.emptyList(),
                voucherId.toString(), userId.toString(), String.valueOf(orderId)
        );
        int r = result.intValue();
        //2.判断结果是为0
        if (r != 0) {
            //2.1.不为0，代表没有购买资格
            return Result.fail(r == 1 ? "库存不足！" : "不能重复下单！");
        }
        //内部方法调用（this目标对象）的事务会实现，使用其代理对象调用内部方法可完成事务操作
        proxy = (IVoucherOrderService) AopContext.currentProxy();

        //3.返回订单id
        return Result.ok(orderId);
    }
    /*@Override
    public Result seckillVoucher(Long voucherId) {
        Long userId = UserHolder.getUser().getId();
        //1.执行lua脚本
        Long result = stringRedisTemplate.execute(
                SECKILL_SCRIPT,
                Collections.emptyList(),
                voucherId.toString(), userId.toString()
        );
        int r = result.intValue();
        //2.判断结果是为0
        if (r != 0) {
            //2.1.不为0，代表没有购买资格
            return Result.fail(r == 1 ? "库存不足！" : "不能重复下单！");
        }
        //一、 TODO  2.2.为0，有购买资格，把下单信息保存到阻塞队列
        //创建订单
        VoucherOrder voucherOrder = new VoucherOrder();
        //设置订单ID，使用自建的全局ID生成器类
        long orderId = redisIdWorker.nextId("order");
        voucherOrder.setId(orderId);
        //代金券ID
        voucherOrder.setVoucherId(voucherId);
        //用户ID
        voucherOrder.setUserId(userId);
        //2.3.放入阻塞队列
        orderTasks.add(voucherOrder);

        //内部方法调用（this目标对象）的事务会实现，使用其代理对象调用内部方法可完成事务操作
        proxy = (IVoucherOrderService) AopContext.currentProxy();

        //3.返回订单id
        return Result.ok(orderId);
    }*/
    /*@Override
    public Result seckillVoucher(Long voucherId) {
        //1.查询优惠券
        SeckillVoucher voucher = seckillVoucherService.getById(voucherId);
        //2.判断秒杀是否开始
        LocalDateTime beginTime = voucher.getBeginTime();
        if (beginTime.isAfter(LocalDateTime.now())) {
            return Result.fail("优惠券秒杀尚未开始！");
        }
        //3.判断秒杀是否己经结束
        LocalDateTime endTime = voucher.getEndTime();
        if (endTime.isBefore(LocalDateTime.now())) {
            return Result.fail("优惠券秒杀已经结束！");
        }
        //4.判断库存是否充足
        if (voucher.getStock() < 1) {
            return Result.fail("优惠券库存不足！");
        }

        //依据用户ID生成悲观锁；注意需要同一个值且地址值一样的值；先获取锁，再等事务操作完成，最后释放锁
        Long userID = UserHolder.getUser().getId();
        synchronized(userID.toString().intern()) {
            //内部方法调用（this目标对象）的事务会实现，使用其代理对象调用内部方法可完成事务操作
            IVoucherOrderService proxy = (IVoucherOrderService) AopContext.currentProxy();
            return proxy.creatVoucherOrder(voucherId);
        }
    }*/

    ///**
    // *创建优惠券订单
    // * @param voucherId
    // * @return
    // */
    /*@Transactional
    public Result creatVoucherOrder(Long voucherId) {
        //5一人一单
        Long userID = UserHolder.getUser().getId();
        LambdaQueryWrapper<VoucherOrder> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(VoucherOrder::getUserId, userID);
        queryWrapper.eq(VoucherOrder::getVoucherId, voucherId);
        int count = this.count(queryWrapper);
        if (count > 0) {
            return Result.fail("用户已购买过一次！");
        }
        //6.扣减库存
        LambdaUpdateWrapper<SeckillVoucher> updateWrapper = new LambdaUpdateWrapper<>();
        updateWrapper.eq(SeckillVoucher::getVoucherId, voucherId);
        updateWrapper.setSql("stock = stock - 1");
        //CAS乐观锁解决超卖问题，有少买问题
        //updateWrapper.eq(SeckillVoucher::getStock, voucher.getStock());
        //CAS乐观锁解决超卖问题，同时避免少买情况
        updateWrapper.gt(SeckillVoucher::getStock , 0);
        boolean success = seckillVoucherService.update(updateWrapper);
        if (!success) {
            return Result.fail("优惠券库存不足！");
        }
        //7.创建订单
        VoucherOrder voucherOrder = new VoucherOrder();
        //7.1设置订单ID，使用自建的全局ID生成器类
        long orderId = redisIdWorker.nextId("order");
        voucherOrder.setId(orderId);
        //7.2代金券ID
        voucherOrder.setVoucherId(voucherId);
        //7.3用户ID
        voucherOrder.setUserId(userID);
        //8将订单写入数据库
        this.save(voucherOrder);
        //9.返回订单id
        return Result.ok(orderId);
    }*/

    /**
     * 二、 TODO 异步将已完成的订单写入数据库，异步处理不需要改前端返回值
     * @param voucherOrder
     */
    @Transactional
    public void creatVoucherOrder(VoucherOrder voucherOrder) {
        //5一人一单
        Long userID = voucherOrder.getUserId();
        LambdaQueryWrapper<VoucherOrder> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(VoucherOrder::getUserId, userID);
        queryWrapper.eq(VoucherOrder::getVoucherId, voucherOrder.getVoucherId());
        int count = this.count(queryWrapper);
        if (count > 0) {
            log.error("用户已购买过一次！");
            return;
        }
        //6.扣减库存
        LambdaUpdateWrapper<SeckillVoucher> updateWrapper = new LambdaUpdateWrapper<>();
        updateWrapper.eq(SeckillVoucher::getVoucherId, voucherOrder.getVoucherId());
        updateWrapper.setSql("stock = stock - 1");
        //CAS乐观锁解决超卖问题，有少买问题
        //updateWrapper.eq(SeckillVoucher::getStock, voucher.getStock());
        //CAS乐观锁解决超卖问题，同时避免少买情况
        updateWrapper.gt(SeckillVoucher::getStock , 0);
        boolean success = seckillVoucherService.update(updateWrapper);
        if (!success) {
            log.error("优惠券库存不足！");
            return;
        }
        //7.异步线程，保存订单即可
        this.save(voucherOrder);
    }
}














