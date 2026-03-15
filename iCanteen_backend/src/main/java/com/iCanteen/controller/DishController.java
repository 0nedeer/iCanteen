package com.iCanteen.controller;

import com.iCanteen.dto.DishCreateDTO;
import com.iCanteen.dto.DishManualRecommendDTO;
import com.iCanteen.dto.DishUpdateDTO;
import com.iCanteen.dto.Result;
import com.iCanteen.entity.Dish;
import com.iCanteen.service.IDishService;
import com.iCanteen.utils.AdminPermissionService;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;

@RestController
@RequestMapping("/dish")
public class DishController {

    @Resource
    private IDishService dishService;
    @Resource
    private AdminPermissionService adminPermissionService;

    @GetMapping("/by-window")
    public Result queryDishesByWindowId(@RequestParam("windowId") Long windowId) {
        return dishService.queryDishesByWindowId(windowId);
    }

    @GetMapping("/by-canteen")
    public Result queryTodayDishesByCanteenId(@RequestParam("canteenId") Long canteenId) {
        return dishService.queryTodayDishesByCanteenId(canteenId);
    }

    @GetMapping("/{id}")
    public Result queryDishById(@PathVariable("id") Long id) {
        return dishService.queryDishById(id);
    }

    @GetMapping("/recommend/by-canteen")
    public Result queryRecommendedDishByCanteenId(@RequestParam("canteenId") Long canteenId) {
        return dishService.queryRecommendedDishByCanteenId(canteenId);
    }

    @PostMapping("/recommend/manual")
    public Result setManualRecommendedDishes(@RequestBody DishManualRecommendDTO dto) {
        Result authResult = adminPermissionService.ensureAdmin();
        if (authResult != null) {
            return authResult;
        }
        if (dto == null) {
            return Result.fail("参数不能为空");
        }
        return dishService.setManualRecommendedDishes(dto.getCanteenId(), dto.getDishIds());
    }

    @GetMapping("/random-recommend")
    public Result getRandomRecommendedDish() {
        return dishService.getRandomRecommendedDish();
    }

    @PostMapping
    public Result createDish(@RequestBody DishCreateDTO dto) {
        Result authResult = adminPermissionService.ensureAdmin();
        if (authResult != null) {
            return authResult;
        }
        if (dto == null) {
            return Result.fail("参数不能为空");
        }
        if (dto.getQuantity() != null && dto.getQuantity() < 0) {
            return Result.fail("数量不能小于0");
        }

        Dish dish = new Dish();
        dish.setWindowId(dto.getWindowId());
        dish.setName(dto.getName());
        dish.setDescription(dto.getDescription());
        dish.setImage(dto.getImage());
        dish.setPrice(dto.getPrice());
        dish.setQuantity(dto.getQuantity());
        dish.setTags(dto.getTags());
        dish.setStatus(dto.getStatus());
        dish.setIsToday(dto.getIsToday());
        return dishService.createDish(dish);
    }

    @PutMapping
    public Result updateDish(@RequestBody DishUpdateDTO dto) {
        Result authResult = adminPermissionService.ensureAdmin();
        if (authResult != null) {
            return authResult;
        }
        if (dto == null || dto.getId() == null) {
            return Result.fail("修改菜品必须传入id");
        }
        if (dto.getQuantity() != null && dto.getQuantity() < 0) {
            return Result.fail("数量不能小于0");
        }

        Dish dish = new Dish();
        dish.setId(dto.getId());
        dish.setWindowId(dto.getWindowId());
        dish.setName(dto.getName());
        dish.setDescription(dto.getDescription());
        dish.setImage(dto.getImage());
        dish.setPrice(dto.getPrice());
        dish.setQuantity(dto.getQuantity());
        dish.setTags(dto.getTags());
        dish.setStatus(dto.getStatus());
        dish.setIsToday(dto.getIsToday());
        return dishService.updateDish(dish);
    }

    @DeleteMapping("/{id}")
    public Result deleteDish(@PathVariable("id") Long id) {
        Result authResult = adminPermissionService.ensureAdmin();
        if (authResult != null) {
            return authResult;
        }
        return dishService.deleteDish(id);
    }
}
