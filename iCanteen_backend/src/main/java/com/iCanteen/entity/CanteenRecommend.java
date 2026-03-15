package com.iCanteen.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("tb_canteen_recommend")
public class CanteenRecommend implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId("canteen_id")
    private Long canteenId;

    private Long dishId1;

    private Long dishId2;

    private Long dishId3;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
