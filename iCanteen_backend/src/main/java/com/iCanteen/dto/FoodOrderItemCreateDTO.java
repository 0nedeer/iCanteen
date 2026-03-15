package com.iCanteen.dto;

import lombok.Data;

@Data
public class FoodOrderItemCreateDTO {

    private Long dishId;

    private Integer quantity;
}

