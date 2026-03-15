package com.iCanteen.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * <p>
 * 
 * </p>
 *
 * @author 0nedeer
 * @since 2021-12-22
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("tb_user")
public class User implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 已清理乱码注释
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 已清理乱码注释
     */
    private String phone;

    /**
     * 已清理乱码注释
     */
    private String password;

    /**
     * 已清理乱码注释
     */
    private Integer role;

    /**
     * 已清理乱码注释
     */
    private String nickName;

    /**
     * 已清理乱码注释
     */
    private String icon = "";

    /**
     * 已清理乱码注释
     */
    private LocalDateTime createTime;

    /**
     * 已清理乱码注释
     */
    private LocalDateTime updateTime;


}


