package com.iCanteen.dto;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class FoodOrderItemDTO {

    private Long id;

    private Long dishId;

    private String dishName;

    private String dishImage;

    private Long windowId;

    private BigDecimal price;

    private Integer quantity;

    private BigDecimal amount;
}

