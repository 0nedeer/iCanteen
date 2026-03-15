package com.iCanteen.controller;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.iCanteen.dto.LoginFormDTO;
import com.iCanteen.dto.Result;
import com.iCanteen.dto.UserDTO;
import com.iCanteen.dto.UserInfoUpdateDTO;
import com.iCanteen.dto.UserProfileDTO;
import com.iCanteen.entity.User;
import com.iCanteen.entity.UserInfo;
import com.iCanteen.service.IUserInfoService;
import com.iCanteen.service.IUserService;
import com.iCanteen.utils.PasswordEncoder;
import com.iCanteen.utils.RegexUtils;
import com.iCanteen.utils.UserHolder;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import javax.servlet.http.HttpSession;
import java.time.LocalDate;

import static com.iCanteen.utils.RedisConstants.LOGIN_USER_KEY;

@RestController
@RequestMapping("/user")
public class UserController {

    @Resource
    private IUserService userService;
    @Resource
    private IUserInfoService userInfoService;
    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @PostMapping("code")
    public Result sendCode(@RequestParam("phone") String phone, HttpSession session) {
        return userService.sendCode(phone, session);
    }

    @PostMapping("/login")
    public Result login(@RequestBody LoginFormDTO loginForm, HttpSession session) {
        return userService.login(loginForm, session);
    }

    @PostMapping("/logout")
    public Result logout(@RequestHeader(value = "authorization", required = false) String token) {
        token = normalizeToken(token);
        if (StrUtil.isBlank(token)) {
            return Result.ok();
        }
        stringRedisTemplate.delete(LOGIN_USER_KEY + token);
        return Result.ok();
    }

    @GetMapping("/me")
    public Result me() {
        UserDTO user = UserHolder.getUser();
        if (user == null) {
            return Result.fail("未登录");
        }
        return Result.ok(user);
    }

    @GetMapping("/info")
    public Result info() {
        UserDTO currentUser = UserHolder.getUser();
        if (currentUser == null) {
            return Result.fail("请先登录");
        }

        User user = userService.getById(currentUser.getId());
        if (user == null) {
            return Result.fail("用户不存在");
        }
        UserInfo userInfo = userInfoService.getById(currentUser.getId());

        UserProfileDTO profile = new UserProfileDTO();
        profile.setPhone(user.getPhone());
        profile.setNickname(user.getNickName());
        profile.setIcon(user.getIcon());
        if (userInfo != null) {
            profile.setCity(userInfo.getCity());
            profile.setIntroduce(userInfo.getIntroduce());
            profile.setGender(userInfo.getGender());
            profile.setBirthday(userInfo.getBirthday());
            profile.setCredits(userInfo.getCredits());
        }
        return Result.ok(profile);
    }

    @PutMapping("/info")
    public Result updateInfo(@RequestBody UserInfoUpdateDTO updateDTO) {
        UserDTO currentUser = UserHolder.getUser();
        if (currentUser == null) {
            return Result.fail("请先登录");
        }
        if (updateDTO == null) {
            return Result.fail("参数不能为空");
        }

        if (StrUtil.isNotBlank(updateDTO.getPhone()) && RegexUtils.isPhoneInvalid(updateDTO.getPhone())) {
            return Result.fail("手机号格式错误");
        }
        if (StrUtil.isNotBlank(updateDTO.getNickname()) && updateDTO.getNickname().length() > 32) {
            return Result.fail("昵称长度不能超过32");
        }
        if (StrUtil.isNotBlank(updateDTO.getCity()) && updateDTO.getCity().length() > 64) {
            return Result.fail("城市长度不能超过64");
        }
        if (StrUtil.isNotBlank(updateDTO.getIntroduce()) && updateDTO.getIntroduce().length() > 256) {
            return Result.fail("简介长度不能超过256");
        }
        LocalDate birthday = updateDTO.getBirthday();
        if (birthday != null && birthday.isAfter(LocalDate.now())) {
            return Result.fail("生日不能大于今天");
        }

        Long uid = currentUser.getId();
        if (StrUtil.isNotBlank(updateDTO.getPhone())) {
            User phoneExisted = userService.getOne(new QueryWrapper<User>()
                    .eq("phone", updateDTO.getPhone())
                    .ne("id", uid));
            if (phoneExisted != null) {
                return Result.fail("手机号已被使用");
            }
        }

        boolean needUpdateUser = StrUtil.isNotBlank(updateDTO.getPhone())
                || StrUtil.isNotBlank(updateDTO.getPassword())
                || StrUtil.isNotBlank(updateDTO.getNickname())
                || StrUtil.isNotBlank(updateDTO.getIcon());

        if (needUpdateUser) {
            UpdateWrapper<User> uw = new UpdateWrapper<>();
            uw.eq("id", uid);
            if (StrUtil.isNotBlank(updateDTO.getPhone())) {
                uw.set("phone", updateDTO.getPhone());
            }
            if (StrUtil.isNotBlank(updateDTO.getNickname())) {
                uw.set("nick_name", updateDTO.getNickname());
            }
            if (StrUtil.isNotBlank(updateDTO.getIcon())) {
                uw.set("icon", updateDTO.getIcon());
            }
            if (StrUtil.isNotBlank(updateDTO.getPassword())) {
                // uw.set("password", PasswordEncoder.encode(updateDTO.getPassword()));
                uw.set("password", updateDTO.getPassword());
            }
            boolean ok = userService.update(uw);
            if (!ok) {
                return Result.fail("更新用户基础信息失败");
            }
        }

        boolean needUpdateInfo = StrUtil.isNotBlank(updateDTO.getCity())
                || StrUtil.isNotBlank(updateDTO.getIntroduce())
                || updateDTO.getGender() != null
                || updateDTO.getBirthday() != null;

        if (needUpdateInfo) {
            UserInfo userInfo = userInfoService.getById(uid);
            if (userInfo == null) {
                userInfo = new UserInfo();
                userInfo.setUserId(uid);
            }
            if (StrUtil.isNotBlank(updateDTO.getCity())) {
                userInfo.setCity(updateDTO.getCity());
            }
            if (StrUtil.isNotBlank(updateDTO.getIntroduce())) {
                userInfo.setIntroduce(updateDTO.getIntroduce());
            }
            if (updateDTO.getGender() != null) {
                userInfo.setGender(updateDTO.getGender());
            }
            if (updateDTO.getBirthday() != null) {
                userInfo.setBirthday(updateDTO.getBirthday());
            }
            userInfoService.saveOrUpdate(userInfo);
        }

        return Result.ok();
    }

    @PostMapping("/sign")
    public Result sign() {
        return userService.sign();
    }

    @GetMapping("/sign/count")
    public Result signCount() {
        return userService.signCount();
    }

    private String normalizeToken(String token) {
        if (StrUtil.isBlank(token)) {
            return null;
        }
        token = token.trim();
        if (StrUtil.startWithIgnoreCase(token, "Bearer ")) {
            token = token.substring(7).trim();
        }
        return token;
    }
}
