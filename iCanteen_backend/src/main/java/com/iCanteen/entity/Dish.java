package com.iCanteen.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("tb_dish")
public class Dish implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private Long windowId;

    private String name;

    private String description;

    private String image;

    private BigDecimal price;

    private Integer quantity;

    private String tags;

    private BigDecimal avgRating;

    private Integer ratingCount;

    private Integer status;

    private Integer isToday;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
