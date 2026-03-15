package com.iCanteen.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class FoodOrderSimpleDTO {

    private Long id;

    private String orderNo;

    private Long userId;

    private BigDecimal totalAmount;

    private Integer totalCount;

    private Integer status;

    private String pickupCode;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}

