package com.iCanteen.controller;

import com.iCanteen.dto.DishReviewSubmitDTO;
import com.iCanteen.dto.Result;
import com.iCanteen.service.IDishReviewService;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;

@RestController
@RequestMapping("/dish-review")
public class DishReviewController {

    @Resource
    private IDishReviewService dishReviewService;

    @PostMapping
    public Result submitReview(@RequestBody DishReviewSubmitDTO dto) {
        return dishReviewService.submitReview(dto);
    }

    @GetMapping("/by-dish")
    public Result queryReviewsByDishId(
            @RequestParam("dishId") Long dishId,
            @RequestParam(value = "current", defaultValue = "1") Integer current) {
        return dishReviewService.queryReviewsByDishId(dishId, current);
    }

    @GetMapping("/my")
    public Result queryMyReviews(@RequestParam(value = "current", defaultValue = "1") Integer current) {
        return dishReviewService.queryMyReviews(current);
    }

    @DeleteMapping("/{id}")
    public Result deleteMyReview(@PathVariable("id") Long reviewId) {
        return dishReviewService.deleteMyReview(reviewId);
    }
}
