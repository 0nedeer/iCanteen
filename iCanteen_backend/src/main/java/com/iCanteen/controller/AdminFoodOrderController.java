package com.iCanteen.controller;

import com.iCanteen.dto.FoodOrderStatusUpdateDTO;
import com.iCanteen.dto.Result;
import com.iCanteen.service.IFoodOrderService;
import com.iCanteen.utils.AdminPermissionService;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;

@RestController
@RequestMapping("/admin/food-order")
public class AdminFoodOrderController {

    @Resource
    private IFoodOrderService foodOrderService;
    @Resource
    private AdminPermissionService adminPermissionService;

    @GetMapping("/list")
    public Result queryOrders(@RequestParam(value = "current", required = false) Integer current,
                              @RequestParam(value = "pageSize", required = false) Integer pageSize,
                              @RequestParam(value = "status", required = false) Integer status,
                              @RequestParam(value = "userId", required = false) Long userId,
                              @RequestParam(value = "windowId", required = false) Long windowId) {
        Result authResult = adminPermissionService.ensureAdmin();
        if (authResult != null) {
            return authResult;
        }
        return foodOrderService.adminQueryOrders(current, pageSize, status, userId, windowId);
    }

    @GetMapping("/{id}")
    public Result queryOrderDetail(@PathVariable("id") Long orderId) {
        Result authResult = adminPermissionService.ensureAdmin();
        if (authResult != null) {
            return authResult;
        }
        return foodOrderService.adminQueryOrderDetail(orderId);
    }

    @PutMapping("/{id}/status")
    public Result updateOrderStatus(@PathVariable("id") Long orderId,
                                    @RequestBody FoodOrderStatusUpdateDTO updateDTO) {
        Result authResult = adminPermissionService.ensureAdmin();
        if (authResult != null) {
            return authResult;
        }
        if (updateDTO == null || updateDTO.getStatus() == null) {
            return Result.fail("必须传入目标状态");
        }
        return foodOrderService.adminUpdateStatus(orderId, updateDTO.getStatus(), updateDTO.getCancelReason());
    }
}
