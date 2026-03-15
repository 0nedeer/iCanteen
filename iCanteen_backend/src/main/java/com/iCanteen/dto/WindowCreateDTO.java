package com.iCanteen.dto;

import lombok.Data;

@Data
public class WindowCreateDTO {
    private Long canteenId;
    private String name;
    private String description;
    private Integer waitTime;
    private Integer waitTimeLevel;
    private Integer status;
    private Integer sort;
}
