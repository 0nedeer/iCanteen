package com.iCanteen.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * <p>
 * 
 * </p>
 *
 * @author 0nedeer
 * @since 2021-12-24
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("tb_user_info")
public class UserInfo implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 已清理乱码注释
     */
    @TableId(value = "user_id", type = IdType.AUTO)
    private Long userId;

    /**
     * 已清理乱码注释
     */
    private String city;

    /**
     * 已清理乱码注释
     */
    private String introduce;

    /**
     * 已清理乱码注释
     */
    private Boolean gender;

    /**
     * 已清理乱码注释
     */
    private LocalDate birthday;

    /**
     * 已清理乱码注释
     */
    private Integer credits;

    /**
     * 已清理乱码注释
     */
    private LocalDateTime createTime;

    /**
     * 已清理乱码注释
     */
    private LocalDateTime updateTime;


}


