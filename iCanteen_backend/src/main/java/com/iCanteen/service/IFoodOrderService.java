package com.iCanteen.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.iCanteen.dto.FoodOrderCreateDTO;
import com.iCanteen.dto.Result;
import com.iCanteen.entity.FoodOrder;

public interface IFoodOrderService extends IService<FoodOrder> {

    Result createOrder(FoodOrderCreateDTO createDTO);

    Result queryMyOrders(Integer current, Integer pageSize, Integer status);

    Result queryMyOrderDetail(Long orderId);

    Result cancelMyOrder(Long orderId, String cancelReason);

    Result adminQueryOrders(Integer current, Integer pageSize, Integer status, Long userId, Long windowId);

    Result adminQueryOrderDetail(Long orderId);

    Result adminUpdateStatus(Long orderId, Integer status, String cancelReason);
}
