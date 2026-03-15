package com.iCanteen.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.iCanteen.dto.Result;
import com.iCanteen.entity.Dish;

import java.util.List;

public interface IDishService extends IService<Dish> {

    Result queryDishesByWindowId(Long windowId);

    Result queryTodayDishesByCanteenId(Long canteenId);

    Result getRandomRecommendedDish();

    Result queryDishById(Long id);

    Result queryRecommendedDishByCanteenId(Long canteenId);

    Result setManualRecommendedDishes(Long canteenId, List<Long> dishIds);

    Result createDish(Dish dish);

    Result updateDish(Dish dish);

    Result deleteDish(Long id);
}
