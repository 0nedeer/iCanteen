package com.iCanteen.dto;

import lombok.Data;

import java.util.List;

@Data
public class FoodOrderDetailDTO extends FoodOrderSimpleDTO {

    private String remark;

    private String cancelReason;

    private List<FoodOrderItemDTO> items;
}

