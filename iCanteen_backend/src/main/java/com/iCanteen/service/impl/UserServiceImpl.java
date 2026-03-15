package com.iCanteen.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.lang.UUID;
import cn.hutool.core.util.RandomUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.iCanteen.dto.LoginFormDTO;
import com.iCanteen.dto.Result;
import com.iCanteen.dto.UserDTO;
import com.iCanteen.entity.User;
import com.iCanteen.entity.UserInfo;
import com.iCanteen.mapper.UserInfoMapper;
import com.iCanteen.mapper.UserMapper;
import com.iCanteen.service.IUserService;
import com.iCanteen.utils.PasswordEncoder;
import com.iCanteen.utils.RegexUtils;
import com.iCanteen.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.connection.BitFieldSubCommands;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import javax.servlet.http.HttpSession;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static com.iCanteen.utils.RedisConstants.LOGIN_CODE_KEY;
import static com.iCanteen.utils.RedisConstants.LOGIN_CODE_TTL;
import static com.iCanteen.utils.RedisConstants.LOGIN_USER_KEY;
import static com.iCanteen.utils.RedisConstants.LOGIN_USER_TTL;
import static com.iCanteen.utils.RedisConstants.USER_SIGN_KEY;
import static com.iCanteen.utils.SystemConstants.USER_NICK_NAME_PREFIX;

@Slf4j
@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements IUserService {

    private static final int SIGN_REWARD_CREDITS = 1;

    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Resource
    private UserInfoMapper userInfoMapper;

    @Override
    public Result sendCode(String phone, HttpSession session) {
        if (RegexUtils.isPhoneInvalid(phone)) {
            return Result.fail("手机号格式错误");
        }

        String code = RandomUtil.randomNumbers(6);
        stringRedisTemplate.opsForValue().set(LOGIN_CODE_KEY + phone, code, LOGIN_CODE_TTL, TimeUnit.MINUTES);
        log.info("发送短信验证码成功，手机号：{}，验证码：{}", phone, code);
        return Result.ok();
    }

    @Override
    public Result login(LoginFormDTO loginForm, HttpSession session) {
        if (loginForm == null || StrUtil.isBlank(loginForm.getPhone())) {
            return Result.fail("手机号不能为空");
        }
        String phone = loginForm.getPhone();
        if (RegexUtils.isPhoneInvalid(phone)) {
            return Result.fail("手机号格式错误");
        }

        boolean useCodeLogin = StrUtil.isNotBlank(loginForm.getCode());
        boolean usePasswordLogin = StrUtil.isNotBlank(loginForm.getPassword());
        if (!useCodeLogin && !usePasswordLogin) {
            return Result.fail("请传入验证码或密码");
        }

        User user = getOne(new QueryWrapper<User>().eq("phone", phone));
        if (useCodeLogin) {
            return loginByCode(phone, loginForm.getCode(), user);
        }
        return loginByPassword(phone, loginForm.getPassword(), user);
    }

    /**
     * 用户签到功能
     * 使用 Redis 的 Bitmap 数据结构记录用户每月的签到情况，每个位代表一个月中的某一天
     *
     * @return Result 操作结果，成功返回 OK
     */
    @Override
    public Result sign() {
        // 获取当前登录用户 ID
        Long userId = UserHolder.getUser().getId();
        // 获取当前时间并生成 Redis Key，格式为：user:sign:{userId}:yyyyMM
        LocalDateTime now = LocalDateTime.now();
        String keySuffix = now.format(DateTimeFormatter.ofPattern(":yyyyMM"));
        String key = USER_SIGN_KEY + userId + keySuffix;
        int dayOfMonth = now.getDayOfMonth();

        // 在 Redis Bitmap 中将当前日期对应的位设置为 1，返回设置前状态
        Boolean alreadySigned = stringRedisTemplate.opsForValue().setBit(key, dayOfMonth - 1, true);
        // 仅首次签到奖励积分，避免重复签到重复加分
        if (!Boolean.TRUE.equals(alreadySigned)) {
            rewardCreditsOnSign(userId);
        }
        return Result.ok();
    }

        /**
         * 统计用户连续签到天数
         * 从 Redis Bitmap 中获取当月签到数据，计算截至今天的连续签到次数
         *
         * @return Result 返回连续签到天数，如果未签到则返回 0
         */
        @Override
        public Result signCount() {
            // 获取当前登录用户 ID 和当前时间
            Long userId = UserHolder.getUser().getId();
            LocalDateTime now = LocalDateTime.now();
            String keySuffix = now.format(DateTimeFormatter.ofPattern(":yyyyMM"));
            String key = USER_SIGN_KEY + userId + keySuffix;
            int dayOfMonth = now.getDayOfMonth();

            // 从 Redis Bitmap 中获取从月初到今天的签到数据
            List<Long> result = stringRedisTemplate.opsForValue().bitField(
                    key,
                    BitFieldSubCommands.create()
                            .get(BitFieldSubCommands.BitFieldType.unsigned(dayOfMonth)).valueAt(0)
            );
            // 如果查询结果为空，说明没有签到记录
            if (result == null || result.isEmpty()) {
                return Result.ok(0);
            }
            Long num = result.get(0);
            // 如果签到数据为 0，说明今天未签到
            if (num == null || num == 0) {
                return Result.ok(0);
            }

            // 通过位运算统计连续签到天数：从低位向高位遍历，遇到 0 则停止
            int count = 0;
            while (true) {
                if ((num & 1) == 0) {
                    break;
                } else {
                    count++;
                }
                num >>>= 1;
            }
            return Result.ok(count);
        }

    private Result loginByCode(String phone, String code, User user) {
        if (RegexUtils.isCodeInvalid(code)) {
            return Result.fail("验证码格式错误");
        }
        String cacheCode = stringRedisTemplate.opsForValue().get(LOGIN_CODE_KEY + phone);
        if (!StrUtil.equals(cacheCode, code)) {
            return Result.fail("验证码错误");
        }

        if (user == null) {
            user = createUserWithPhone(phone);
        }

        stringRedisTemplate.delete(LOGIN_CODE_KEY + phone);
        return saveUserToken(user);
    }

    private Result loginByPassword(String phone, String password, User user) {
        if (user == null) {
            return Result.fail("账号不存在，请先使用验证码登录");
        }
        String storedPassword = user.getPassword();
        if (StrUtil.isBlank(storedPassword)) {
            return Result.fail("该账号未设置密码，请使用验证码登录");
        }

        boolean passwordMatched;
        try {
            if (storedPassword.contains("@")) {
                passwordMatched = PasswordEncoder.matches(storedPassword, password);
            } else {
                passwordMatched = storedPassword.equals(password);
            }
        } catch (RuntimeException e) {
            log.error("密码校验异常", e);
            return Result.fail("密码格式错误");
        }
        if (!passwordMatched) {
            return Result.fail("密码错误");
        }
        return saveUserToken(user);
    }

    private Result saveUserToken(User user) {
        String token = UUID.randomUUID().toString(true);
        UserDTO userDTO = BeanUtil.copyProperties(user, UserDTO.class);

        Map<String, Object> userMap = BeanUtil.beanToMap(userDTO, new HashMap<>(),
                CopyOptions.create()
                        .setIgnoreNullValue(true)
                        .setFieldValueEditor((fieldName, fieldValue) -> fieldValue.toString()));

        String tokenKey = LOGIN_USER_KEY + token;
        stringRedisTemplate.opsForHash().putAll(tokenKey, userMap);
        stringRedisTemplate.expire(tokenKey, LOGIN_USER_TTL, TimeUnit.MINUTES);

        return Result.ok(token);
    }

    private User createUserWithPhone(String phone) {
        User user = new User();
        user.setPhone(phone);
        user.setRole(0);
        user.setNickName(USER_NICK_NAME_PREFIX + RandomUtil.randomString(10));
        save(user);
        return user;
    }

    private void rewardCreditsOnSign(Long userId) {
        int updated = userInfoMapper.update(null, new LambdaUpdateWrapper<UserInfo>()
                .setSql("credits = IFNULL(credits, 0) + " + SIGN_REWARD_CREDITS)
                .eq(UserInfo::getUserId, userId));
        if (updated > 0) {
            return;
        }

        UserInfo userInfo = new UserInfo();
        userInfo.setUserId(userId);
        userInfo.setCredits(SIGN_REWARD_CREDITS);
        try {
            userInfoMapper.insert(userInfo);
        } catch (DuplicateKeyException e) {
            userInfoMapper.update(null, new LambdaUpdateWrapper<UserInfo>()
                    .setSql("credits = IFNULL(credits, 0) + " + SIGN_REWARD_CREDITS)
                    .eq(UserInfo::getUserId, userId));
        }
    }
}
