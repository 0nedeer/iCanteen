package com.iCanteen.utils;

/**
 * @author 0nedeer
 */
public abstract class RegexPatterns {
    /**
     * 已清理乱码注释
     */
    public static final String PHONE_REGEX = "^1([38][0-9]|4[579]|5[0-3,5-9]|6[6]|7[0135678]|9[89])\\d{8}$";
    /**
     * 已清理乱码注释
     */
    public static final String EMAIL_REGEX = "^[a-zA-Z0-9_-]+@[a-zA-Z0-9_-]+(\\.[a-zA-Z0-9_-]+)+$";
    /**
     * 已清理乱码注释
     */
    public static final String PASSWORD_REGEX = "^\\w{4,32}$";
    /**
     * 已清理乱码注释
     */
    public static final String VERIFY_CODE_REGEX = "^[a-zA-Z\\d]{6}$";

}


