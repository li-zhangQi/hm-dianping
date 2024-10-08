package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.lang.UUID;
import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.LoginFormDTO;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.User;
import com.hmdp.mapper.UserMapper;
import com.hmdp.service.IUserService;
import com.hmdp.utils.RegexUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import javax.servlet.http.HttpSession;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.BiFunction;

import static com.hmdp.utils.RedisConstants.*;
import static com.hmdp.utils.SystemConstants.USER_NICK_NAME_PREFIX;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Slf4j
@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements IUserService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    /**
     * 发送手机验证码 - 改Redis储存
     * @param phone
     * @param session
     * @return
     */
    /*@Override
    public Result sendCode(String phone, HttpSession session) {

        // 1.校验手机号
        if (RegexUtils.isPhoneInvalid(phone)) {
            // 2.如果不符合,返回错误信息
            return Result.fail("手机号格式错误！");
        }

        // 3.符合，生成验证码
        String code = RandomUtil.randomNumbers(6);

        // 4.保存验证码到session
        session.setAttribute("code", code);

        // 5.发送验证码
        log.debug("发送短信验证码成功，验证码为：{}", code);

        // 返回ok
        return Result.ok();
    }*/
    @Override
    public Result sendCode(String phone, HttpSession session) {

        // 1.校验手机号
        if (RegexUtils.isPhoneInvalid(phone)) {
            // 2.如果不符合,返回错误信息
            return Result.fail("手机号格式错误！");
        }

        // 3.符合，生成验证码
        String code = RandomUtil.randomNumbers(6);

        // 4.保存验证码到Redis
        stringRedisTemplate.opsForValue().set(LOGIN_CODE_KEY + phone, code, LOGIN_CODE_TTL, TimeUnit.MINUTES);

        // 5.发送验证码
        log.debug("发送短信验证码成功，验证码为：{}", code);

        // 返回ok
        return Result.ok();
    }

    /**
     * 短信验证码登录 - 改Redis储存
     * @param loginForm
     * @param session
     * @return
     */
    /*@Override
    public Result login(LoginFormDTO loginForm, HttpSession session) {
        //1.校验手机号
        if (RegexUtils.isPhoneInvalid(loginForm.getPhone())) {
            return Result.fail("手机号码格式错误！");
        }
        //2.校验验证码
        Object cacheCode = session.getAttribute("code");
        String code = loginForm.getCode();
        //3.不一致，报错
        if (cacheCode == null || !cacheCode.toString().equals(code)) {
            return Result.fail("验证码错误");
        }
        //4.一致，根据手机号查询用户
        LambdaQueryWrapper<User> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(User::getPhone, loginForm.getPhone());
        User user = this.getOne(queryWrapper);
        //5.判断用户是否存在
        if (user == null) {
            //6.不存在，创建新用户并保存
            user = createWithPhone(loginForm.getPhone());
        }
        //7.保存用户信息到session中 (DTO对象设置部分信息即可)
        session.setAttribute("user", BeanUtil.copyProperties(user, UserDTO.class));
        return Result.ok();
    }*/
    @Override
    public Result login(LoginFormDTO loginForm, HttpSession session) {
        String phone = loginForm.getPhone();
        //1.校验手机号
        if (RegexUtils.isPhoneInvalid(phone)) {
            return Result.fail("手机号码格式错误！");
        }
        //2.从Redis中获取验证码并校验
        String cacheCode = stringRedisTemplate.opsForValue().get(LOGIN_CODE_KEY + phone);
        String code = loginForm.getCode();
        //3.不一致，报错
        if (cacheCode == null || !cacheCode.equals(code)) {
            return Result.fail("验证码错误");
        }
        //4.一致，根据手机号查询用户
        LambdaQueryWrapper<User> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(User::getPhone, loginForm.getPhone());
        User user = this.getOne(queryWrapper);
        //5.判断用户是否存在
        if (user == null) {
            //6.不存在，创建新用户并保存
            user = createWithPhone(loginForm.getPhone());
        }
        //7.保存用户信息到Redis中 (DTO对象设置部分信息即可)
        //7.1．随机生成token，作为登录令牌(不用-)
        String token = UUID.randomUUID().toString(true);
        //7.2.将User对象转为HashMap存储
        UserDTO userDTO = BeanUtil.copyProperties(user, UserDTO.class);
        //处理7.3把userMap往stringRedisTemplate写入时候的字段类型转换异常（id为Long需要String）
        Map<String, Object> userMap = BeanUtil.beanToMap(userDTO, new HashMap<>(),
                CopyOptions.create()
                        .setIgnoreNullValue(true)
                        .setFieldValueEditor(new BiFunction<String, Object, Object>() {
                            @Override
                            public Object apply(String s, Object o) {
                                return o.toString();
                            }
                        }));
        //7.3.存储
        String tokenKey = LOGIN_USER_KEY + token;
        stringRedisTemplate.opsForHash().putAll(tokenKey, userMap);
        //7.4.设置token有效期
        stringRedisTemplate.expire(tokenKey, 30, TimeUnit.MINUTES);
        //8.返回token
        return Result.ok(token);
    }

    private User createWithPhone(String phone) {
        //创建用户
        User user = new User();
        user.setPhone(phone);
        user.setNickName(USER_NICK_NAME_PREFIX + RandomUtil.randomString(10));
        //保存用户
        this.save(user);
        return user;
    }
}
