package com.hmdp.service.impl;

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
import org.springframework.aop.framework.AopContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {

    @Autowired
    private ISeckillVoucherService seckillVoucherService;
    @Autowired
    private RedisIdWorker redisIdWorker;

    /**
     * 根据优惠券Id下单秒杀优惠券
     * @param voucherId
     * @return
     */
    @Override
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
    }

    @Transactional
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
    }
}














