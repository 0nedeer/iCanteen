package com.iCanteen.utils;

import com.iCanteen.dto.Result;
import com.iCanteen.dto.UserDTO;
import com.iCanteen.entity.User;
import com.iCanteen.service.IUserService;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;

@Component
public class AdminPermissionService {

    @Resource
    private IUserService userService;

    public Result ensureAdmin() {
        UserDTO currentUser = UserHolder.getUser();
        if (currentUser == null) {
            return Result.fail("未登录或登录已过期");
        }
        User user = userService.getById(currentUser.getId());
        if (user == null) {
            return Result.fail("用户不存在");
        }
        if (user.getRole() == null || user.getRole() != 1) {
            return Result.fail("无管理员权限");
        }
        return null;
    }
}
