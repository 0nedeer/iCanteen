package com.iCanteen.dto;

import lombok.Data;

import java.util.List;

@Data
public class FoodOrderCreateDTO {

    private List<FoodOrderItemCreateDTO> items;

    private String remark;

    /**
     * 用户下单时上报的经度
     */
    private Double longitude;

    /**
     * 用户下单时上报的纬度
     */
    private Double latitude;
}
