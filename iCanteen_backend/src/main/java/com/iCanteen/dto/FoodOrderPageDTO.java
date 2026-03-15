package com.iCanteen.dto;

import lombok.Data;

import java.util.List;

@Data
public class FoodOrderPageDTO {

    private List<FoodOrderSimpleDTO> records;

    private Long total;
}

