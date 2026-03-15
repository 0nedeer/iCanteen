package com.iCanteen.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.iCanteen.dto.DishReviewSubmitDTO;
import com.iCanteen.dto.Result;
import com.iCanteen.entity.DishReview;

public interface IDishReviewService extends IService<DishReview> {

    Result submitReview(DishReviewSubmitDTO dto);

    Result queryReviewsByDishId(Long dishId, Integer current);

    Result queryMyReviews(Integer current);

    Result deleteMyReview(Long reviewId);
}
