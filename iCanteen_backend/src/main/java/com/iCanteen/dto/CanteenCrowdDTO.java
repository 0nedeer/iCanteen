package com.iCanteen.dto;

import lombok.Data;

@Data
public class CanteenCrowdDTO {
    private Long canteenId;
    private String name;
    private Integer crowdLevel;
    private Integer crowdScore;
}

