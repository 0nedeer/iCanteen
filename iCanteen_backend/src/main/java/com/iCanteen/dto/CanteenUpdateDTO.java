package com.iCanteen.dto;

import lombok.Data;

@Data
public class CanteenUpdateDTO {
    private Long id;
    private String name;
    private String address;
    private String images;
    private Double x;
    private Double y;
    private String openHours;
    private Integer status;
}

