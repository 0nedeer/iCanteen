package com.iCanteen.dto;

import lombok.Data;

@Data
public class DishReviewSubmitDTO {
    private Long dishId;
    private Integer rating;
    private String content;
    private String tags;
    private String images;
}
