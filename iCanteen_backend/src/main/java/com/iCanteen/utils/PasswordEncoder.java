package com.iCanteen.utils;

import cn.hutool.core.util.RandomUtil;
import org.springframework.util.DigestUtils;

import java.nio.charset.StandardCharsets;

public class PasswordEncoder {

    public static String encode(String password) {
        String salt = RandomUtil.randomString(20);
        return encode(password, salt);
    }

    private static String encode(String password, String salt) {
        return salt + "@" + DigestUtils.md5DigestAsHex((password + salt).getBytes(StandardCharsets.UTF_8));
    }

    public static Boolean matches(String encodedPassword, String rawPassword) {
        if (encodedPassword == null || rawPassword == null) {
            return false;
        }
        if (!encodedPassword.contains("@")) {
            throw new RuntimeException("密码格式错误，缺少分隔符@");
        }
        String[] arr = encodedPassword.split("@", 2);
        if (arr.length != 2 || arr[0].isEmpty()) {
            throw new RuntimeException("密码格式错误");
        }
        String salt = arr[0];
        return encodedPassword.equals(encode(rawPassword, salt));
    }
}
