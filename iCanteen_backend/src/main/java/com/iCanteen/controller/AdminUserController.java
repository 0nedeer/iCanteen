package com.iCanteen.controller;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.iCanteen.dto.AdminUserDTO;
import com.iCanteen.dto.AdminUserUpdateDTO;
import com.iCanteen.dto.Result;
import com.iCanteen.entity.User;
import com.iCanteen.entity.UserInfo;
import com.iCanteen.service.IUserInfoService;
import com.iCanteen.service.IUserService;
import com.iCanteen.utils.AdminPermissionService;
import com.iCanteen.utils.PasswordEncoder;
import com.iCanteen.utils.RegexUtils;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/admin/user")
public class AdminUserController {

    @Resource
    private IUserService userService;
    @Resource
    private IUserInfoService userInfoService;
    @Resource
    private AdminPermissionService adminPermissionService;

    @GetMapping("/list")
    public Result listUsers(@RequestParam(value = "current", defaultValue = "1") Integer current,
                            @RequestParam(value = "pageSize", defaultValue = "10") Integer pageSize,
                            @RequestParam(value = "phone", required = false) String phone,
                            @RequestParam(value = "nickname", required = false) String nickname,
                            @RequestParam(value = "role", required = false) Integer role) {
        Result authResult = adminPermissionService.ensureAdmin();
        if (authResult != null) {
            return authResult;
        }

        if (current == null || current < 1) {
            current = 1;
        }
        if (pageSize == null || pageSize < 1 || pageSize > 100) {
            pageSize = 10;
        }
        if (role != null && role != 0 && role != 1) {
            return Result.fail("role仅支持0或1");
        }

        LambdaQueryWrapper<User> queryWrapper = new LambdaQueryWrapper<User>()
                .like(StrUtil.isNotBlank(phone), User::getPhone, phone)
                .like(StrUtil.isNotBlank(nickname), User::getNickName, nickname)
                .eq(role != null, User::getRole, role)
                .orderByDesc(User::getId);
        Page<User> page = userService.page(new Page<>(current, pageSize), queryWrapper);

        List<User> users = page.getRecords();
        if (users.isEmpty()) {
            return Result.ok(Collections.emptyList(), page.getTotal());
        }
        List<Long> userIds = users.stream().map(User::getId).collect(Collectors.toList());
        List<UserInfo> infos = userInfoService.list(new LambdaQueryWrapper<UserInfo>().in(UserInfo::getUserId, userIds));
        Map<Long, UserInfo> userInfoMap = infos.stream()
                .collect(Collectors.toMap(UserInfo::getUserId, Function.identity(), (a, b) -> a));

        List<AdminUserDTO> records = users.stream()
                .map(user -> toAdminUserDTO(user, userInfoMap.get(user.getId())))
                .collect(Collectors.toList());
        return Result.ok(records, page.getTotal());
    }

    @GetMapping("/{id}")
    public Result queryUserDetail(@PathVariable("id") Long id) {
        Result authResult = adminPermissionService.ensureAdmin();
        if (authResult != null) {
            return authResult;
        }
        if (id == null) {
            return Result.fail("用户id不能为空");
        }
        User user = userService.getById(id);
        if (user == null) {
            return Result.fail("用户不存在");
        }
        UserInfo userInfo = userInfoService.getById(id);
        return Result.ok(toAdminUserDTO(user, userInfo));
    }

    @PutMapping("/{id}")
    public Result updateUserDetail(@PathVariable("id") Long id, @RequestBody AdminUserUpdateDTO updateDTO) {
        Result authResult = adminPermissionService.ensureAdmin();
        if (authResult != null) {
            return authResult;
        }
        if (id == null) {
            return Result.fail("用户id不能为空");
        }
        if (updateDTO == null) {
            return Result.fail("参数不能为空");
        }

        User existedUser = userService.getById(id);
        if (existedUser == null) {
            return Result.fail("用户不存在");
        }
        if (updateDTO.getRole() != null && updateDTO.getRole() != 0 && updateDTO.getRole() != 1) {
            return Result.fail("role仅支持0或1");
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
        if (updateDTO.getBirthday() != null && updateDTO.getBirthday().isAfter(LocalDate.now())) {
            return Result.fail("生日不能大于今天");
        }
        if (updateDTO.getCredits() != null && updateDTO.getCredits() < 0) {
            return Result.fail("积分不能为负数");
        }

        User userToUpdate = new User();
        userToUpdate.setId(id);
        userToUpdate.setPhone(updateDTO.getPhone());
        userToUpdate.setRole(updateDTO.getRole());
        userToUpdate.setNickName(updateDTO.getNickname());
        userToUpdate.setIcon(updateDTO.getIcon());
        if (StrUtil.isNotBlank(updateDTO.getPassword())) {
            userToUpdate.setPassword(updateDTO.getPassword());
        }
        userService.updateById(userToUpdate);

        UserInfo userInfo = userInfoService.getById(id);
        if (userInfo == null) {
            userInfo = new UserInfo();
            userInfo.setUserId(id);
        }
        userInfo.setCity(updateDTO.getCity());
        userInfo.setIntroduce(updateDTO.getIntroduce());
        userInfo.setGender(updateDTO.getGender());
        userInfo.setBirthday(updateDTO.getBirthday());
        userInfo.setCredits(updateDTO.getCredits());
        userInfoService.saveOrUpdate(userInfo);

        return Result.ok();
    }

    private AdminUserDTO toAdminUserDTO(User user, UserInfo userInfo) {
        AdminUserDTO dto = new AdminUserDTO();
        dto.setUserId(user.getId());
        dto.setPhone(user.getPhone());
        dto.setPassword(user.getPassword());
        dto.setRole(user.getRole());
        dto.setNickname(user.getNickName());
        dto.setIcon(user.getIcon());
        if (userInfo != null) {
            dto.setCity(userInfo.getCity());
            dto.setIntroduce(userInfo.getIntroduce());
            dto.setGender(userInfo.getGender());
            dto.setBirthday(userInfo.getBirthday());
            dto.setCredits(userInfo.getCredits());
        }
        return dto;
    }
}
