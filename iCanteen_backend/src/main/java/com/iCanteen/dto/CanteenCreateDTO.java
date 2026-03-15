package com.iCanteen.dto;

import lombok.Data;

@Data
public class CanteenCreateDTO {
    private String name;
    private String address;
    private String images;
    private Double x;
    private Double y;
    private String openHours;
    private Integer status;
}

