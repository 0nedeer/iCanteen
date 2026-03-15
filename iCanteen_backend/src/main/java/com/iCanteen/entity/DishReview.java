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
 * 已清理乱码注释
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("tb_dish_review")
public class DishReview implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 已清理乱码注释
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 已清理乱码注释
     */
    private Long dishId;

    /**
     * 已清理乱码注释
     */
    private Long userId;

    /**
     * 已清理乱码注释
     */
    private Integer rating;

    /**
     * 已清理乱码注释
     */
    private String content;

    /**
     * 已清理乱码注释
     */
    private String tags;

    /**
     * 已清理乱码注释
     */
    private String images;

    /**
     * 已清理乱码注释
     */
    private LocalDateTime createTime;

    /**
     * 已清理乱码注释
     */
    private LocalDateTime updateTime;
}


