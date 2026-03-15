package com.iCanteen.dto;

import lombok.Data;

@Data
public class FoodOrderStatusUpdateDTO {

    private Integer status;

    private String cancelReason;
}

