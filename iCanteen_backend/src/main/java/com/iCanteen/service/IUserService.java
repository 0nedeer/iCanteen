package com.iCanteen.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.iCanteen.dto.LoginFormDTO;
import com.iCanteen.dto.Result;
import com.iCanteen.entity.User;

import javax.servlet.http.HttpSession;

/**
 * <p>
 * 已清理乱码注释
 * </p>
 *
 * @author 0nedeer
 * @since 2025-03-09
 */
public interface IUserService extends IService<User> {

    Result sendCode(String phone, HttpSession session);

    Result login(LoginFormDTO loginForm, HttpSession session);

    Result sign();

    Result signCount();

}

