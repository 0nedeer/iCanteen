package com.iCanteen.dto;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class DishUpdateDTO {
    private Long id;
    private Long windowId;
    private String name;
    private String description;
    private String image;
    private BigDecimal price;
    private Integer quantity;
    private String tags;
    private Integer status;
    private Integer isToday;
}
