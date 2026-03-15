package com.iCanteen.controller;

import com.iCanteen.dto.FoodOrderCreateDTO;
import com.iCanteen.dto.Result;
import com.iCanteen.service.IFoodOrderService;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;

@RestController
@RequestMapping("/food-order")
public class FoodOrderController {

    @Resource
    private IFoodOrderService foodOrderService;

    @PostMapping
    public Result createOrder(@RequestBody FoodOrderCreateDTO createDTO) {
        return foodOrderService.createOrder(createDTO);
    }

    @GetMapping("/my")
    public Result queryMyOrders(@RequestParam(value = "current", required = false) Integer current,
                                @RequestParam(value = "pageSize", required = false) Integer pageSize,
                                @RequestParam(value = "status", required = false) Integer status) {
        return foodOrderService.queryMyOrders(current, pageSize, status);
    }

    @GetMapping("/{id}")
    public Result queryMyOrderDetail(@PathVariable("id") Long orderId) {
        return foodOrderService.queryMyOrderDetail(orderId);
    }

    @PutMapping("/{id}/cancel")
    public Result cancelMyOrder(@PathVariable("id") Long orderId,
                                @RequestParam(value = "cancelReason", required = false) String cancelReason) {
        return foodOrderService.cancelMyOrder(orderId, cancelReason);
    }
}

