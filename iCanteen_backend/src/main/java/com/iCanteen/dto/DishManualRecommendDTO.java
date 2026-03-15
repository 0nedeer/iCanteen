package com.iCanteen.dto;

import lombok.Data;

import java.util.List;

@Data
public class DishManualRecommendDTO {
    private Long canteenId;
    private List<Long> dishIds;
}
