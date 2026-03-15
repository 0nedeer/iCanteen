package com.iCanteen.dto;

import com.iCanteen.entity.DishReview;
import lombok.Data;

import java.util.List;

@Data
public class DishReviewPageDTO {
    private List<DishReview> records;
    private Long total;
}
