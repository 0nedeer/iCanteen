package com.iCanteen.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.iCanteen.dto.FoodOrderCreateDTO;
import com.iCanteen.dto.FoodOrderDetailDTO;
import com.iCanteen.dto.FoodOrderItemCreateDTO;
import com.iCanteen.dto.FoodOrderItemDTO;
import com.iCanteen.dto.FoodOrderPageDTO;
import com.iCanteen.dto.FoodOrderSimpleDTO;
import com.iCanteen.dto.Result;
import com.iCanteen.dto.UserDTO;
import com.iCanteen.entity.Dish;
import com.iCanteen.entity.FoodOrder;
import com.iCanteen.entity.FoodOrderItem;
import com.iCanteen.entity.Canteen;
import com.iCanteen.entity.UserInfo;
import com.iCanteen.entity.Window;
import com.iCanteen.mapper.CanteenMapper;
import com.iCanteen.mapper.DishMapper;
import com.iCanteen.mapper.FoodOrderItemMapper;
import com.iCanteen.mapper.FoodOrderMapper;
import com.iCanteen.mapper.UserInfoMapper;
import com.iCanteen.mapper.WindowMapper;
import com.iCanteen.service.IFoodOrderService;
import com.iCanteen.utils.CacheClient;
import com.iCanteen.utils.CacheConsistencyHelper;
import com.iCanteen.utils.RedisIdWorker;
import com.iCanteen.utils.UserHolder;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.geo.Distance;
import org.springframework.data.geo.Metrics;
import org.springframework.data.geo.Point;
import org.springframework.data.redis.core.GeoOperations;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.interceptor.TransactionAspectSupport;

import javax.annotation.Resource;
import java.math.BigDecimal;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static com.iCanteen.utils.RedisConstants.CACHE_FOOD_ORDER_DETAIL_KEY;
import static com.iCanteen.utils.RedisConstants.CACHE_FOOD_ORDER_LIST_ADMIN_KEY;
import static com.iCanteen.utils.RedisConstants.CACHE_FOOD_ORDER_LIST_USER_KEY;
import static com.iCanteen.utils.RedisConstants.CACHE_FOOD_ORDER_TTL;
import static com.iCanteen.utils.RedisConstants.CACHE_DISH_DETAIL_KEY;
import static com.iCanteen.utils.RedisConstants.CACHE_DISH_LIST_BY_CANTEEN_KEY;
import static com.iCanteen.utils.RedisConstants.CACHE_DISH_LIST_BY_WINDOW_KEY;
import static com.iCanteen.utils.RedisConstants.CACHE_DISH_RANDOM_RECOMMEND_KEY;
import static com.iCanteen.utils.RedisConstants.CACHE_DISH_RECOMMEND_BY_CANTEEN_KEY;
import static com.iCanteen.utils.RedisConstants.FOOD_ORDER_ADMIN_LIST_VERSION_KEY;
import static com.iCanteen.utils.RedisConstants.FOOD_ORDER_USER_LIST_VERSION_KEY;
import static com.iCanteen.utils.RedisConstants.CANTEEN_GEO_KEY;
import static com.iCanteen.utils.RedisConstants.LOCK_CACHE_FOOD_ORDER_KEY;
import static com.iCanteen.utils.RedisConstants.LOCK_DISH_STOCK_KEY;
import static com.iCanteen.utils.RedisConstants.LOCK_FOOD_ORDER_KEY;
import static com.iCanteen.utils.RedisConstants.LOCK_FOOD_ORDER_CREATE_USER_KEY;

@Service
public class FoodOrderServiceImpl extends ServiceImpl<FoodOrderMapper, FoodOrder> implements IFoodOrderService {

    private static final int ORDER_DEDUCT_CREDITS = 15;
    private static final int STATUS_CREATED = 0;
    private static final int STATUS_ACCEPTED = 1;
    private static final int STATUS_READY = 2;
    private static final int STATUS_COMPLETED = 3;
    private static final int STATUS_CANCELLED = 4;
    private static final double ORDER_MAX_DISTANCE_KM = 1.0D;
    private static final DateTimeFormatter OPEN_HOURS_FORMATTER = DateTimeFormatter.ofPattern("H:mm");
    private static final long ORDER_CREATE_USER_LOCK_WAIT_SECONDS = 1L;
    private static final long ORDER_STOCK_LOCK_WAIT_SECONDS = 3L;
    private static final long ORDER_STATUS_LOCK_WAIT_SECONDS = 2L;

    @Resource
    private CanteenMapper canteenMapper;
    @Resource
    private DishMapper dishMapper;
    @Resource
    private WindowMapper windowMapper;
    @Resource
    private FoodOrderItemMapper foodOrderItemMapper;
    @Resource
    private UserInfoMapper userInfoMapper;
    @Resource
    private RedisIdWorker redisIdWorker;
    @Resource
    private RedissonClient redissonClient;
    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Resource
    private CacheClient cacheClient;
    @Resource
    private CacheConsistencyHelper cacheConsistencyHelper;

    /**
     * 创建食品订单
     *
     * @param createDTO 订单创建数据传输对象，包含菜品列表和备注信息
     * @return Result 操作结果，成功返回包含订单 ID 的 Result.ok(orderId)，失败返回包含错误信息的 Result.fail()
     */
    @Override
    @Transactional
    public Result createOrder(FoodOrderCreateDTO createDTO) {
        // 验证用户登录状态
        UserDTO currentUser = UserHolder.getUser();
        if (currentUser == null) {
            return Result.fail("please login first");
        }
        // 验证订单数据有效性
        if (createDTO == null || createDTO.getItems() == null || createDTO.getItems().isEmpty()) {
            return Result.fail("order items cannot be empty");
        }

        // 合并相同菜品的数量
        Map<Long, Integer> dishQuantityMap = mergeItems(createDTO.getItems());
        if (dishQuantityMap == null || dishQuantityMap.isEmpty()) {
            return Result.fail("invalid order items");
        }

        // 批量查询菜品信息并验证有效性
        List<Dish> dishes = dishMapper.selectBatchIds(dishQuantityMap.keySet());
        if (dishes == null || dishes.size() != dishQuantityMap.size()) {
            return Result.fail("contains invalid dishes");
        }
        Map<Long, Dish> dishMap = dishes.stream().collect(Collectors.toMap(Dish::getId, d -> d, (a, b) -> a));

        Result validationResult = validateOrderEnvironment(createDTO, dishes, currentUser.getId());
        if (Boolean.FALSE.equals(validationResult.getSuccess())) {
            return validationResult;
        }

        RLock userCreateLock = redissonClient.getLock(LOCK_FOOD_ORDER_CREATE_USER_KEY + currentUser.getId());
        List<RLock> stockLocks = Collections.emptyList();
        boolean userLocked = false;
        try {
            userLocked = userCreateLock.tryLock(ORDER_CREATE_USER_LOCK_WAIT_SECONDS, TimeUnit.SECONDS);
            if (!userLocked) {
                return Result.fail("duplicate submit, please retry later");
            }
            // 按菜品 ID 排序后构建分布式锁，防止死锁
            List<Long> sortedDishIds = new ArrayList<>(dishQuantityMap.keySet());
            Collections.sort(sortedDishIds);
            List<RLock> locks = new ArrayList<>(sortedDishIds.size());
            for (Long dishId : sortedDishIds) {
                locks.add(redissonClient.getLock(LOCK_DISH_STOCK_KEY + dishId));
            }

            // 尝试获取分布式锁
            stockLocks = tryLockAll(locks, ORDER_STOCK_LOCK_WAIT_SECONDS, TimeUnit.SECONDS);
            if (stockLocks.isEmpty()) {
                return Result.fail("too many requests, please retry later");
            }

            // 生成订单 ID、订单号和取餐码
            long orderId = redisIdWorker.nextId("food-order");
            String orderNo = String.valueOf(orderId);
            String pickupCode = buildPickupCode(orderNo);

            BigDecimal totalAmount = BigDecimal.ZERO;
            int totalCount = 0;
            List<FoodOrderItem> orderItems = new ArrayList<>(dishQuantityMap.size());

            // 遍历菜品清单，扣减库存并计算订单金额
            for (Map.Entry<Long, Integer> entry : dishQuantityMap.entrySet()) {
                Long dishId = entry.getKey();
                Integer buyCount = entry.getValue();
                Dish dish = dishMap.get(dishId);
                if (dish == null || dish.getStatus() == null || dish.getStatus() != 1) {
                    TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
                    return Result.fail("dish not found or unavailable");
                }
                if (dish.getPrice() == null || dish.getWindowId() == null) {
                    TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
                    return Result.fail("dish data is invalid");
                }

                // 扣减菜品库存
                boolean deducted = dishMapper.update(null, new LambdaUpdateWrapper<Dish>()
                        .setSql("quantity = quantity - " + buyCount)
                        .eq(Dish::getId, dishId)
                        .eq(Dish::getStatus, 1)
                        .ge(Dish::getQuantity, buyCount)) > 0;
                if (!deducted) {
                    TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
                    return Result.fail("insufficient stock");
                }

                // 计算单项金额并累加总金额
                BigDecimal lineAmount = dish.getPrice().multiply(BigDecimal.valueOf(buyCount.longValue()));
                totalAmount = totalAmount.add(lineAmount);
                totalCount += buyCount;

                // 构建订单项
                FoodOrderItem orderItem = new FoodOrderItem();
                orderItem.setOrderId(orderId);
                orderItem.setDishId(dishId);
                orderItem.setDishName(dish.getName());
                orderItem.setDishImage(dish.getImage());
                orderItem.setWindowId(dish.getWindowId());
                orderItem.setPrice(dish.getPrice());
                orderItem.setQuantity(buyCount);
                orderItem.setAmount(lineAmount);
                orderItems.add(orderItem);
            }

            // 保存订单主表数据
            FoodOrder foodOrder = new FoodOrder();
            foodOrder.setId(orderId);
            foodOrder.setUserId(currentUser.getId());
            foodOrder.setOrderNo(orderNo);
            foodOrder.setTotalAmount(totalAmount);
            foodOrder.setTotalCount(totalCount);
            foodOrder.setStatus(STATUS_CREATED);
            foodOrder.setPickupCode(pickupCode);
            foodOrder.setRemark(createDTO.getRemark());
            boolean saved = save(foodOrder);
            if (!saved) {
                TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
                return Result.fail("create order failed");
            }

            // 保存订单项数据
            for (FoodOrderItem orderItem : orderItems) {
                foodOrderItemMapper.insert(orderItem);
            }

            // 下单扣减积分（余额不足不允许下单）
            boolean deductedCredits = deductCreditsOnOrderCreate(currentUser.getId());
            if (!deductedCredits) {
                TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
                return Result.fail("积分不足，无法下单");
            }

            // 更新缓存和版本号
            invalidateDishCachesByDishes(dishes);
            invalidateOrderDetailCache(orderId);
            bumpUserOrderVersion(currentUser.getId());
            bumpAdminOrderVersion();
            return Result.ok(orderId);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
            return Result.fail("request timeout, please retry");
        } finally {
            unlockAll(stockLocks);
            if (userLocked && userCreateLock.isHeldByCurrentThread()) {
                userCreateLock.unlock();
            }
        }
    }

    @Override
    public Result queryMyOrders(Integer current, Integer pageSize, Integer status) {
        UserDTO currentUser = UserHolder.getUser();
        if (currentUser == null) {
            return Result.fail("please login first");
        }
        if (!isValidStatus(status)) {
            return Result.fail("invalid status");
        }

        long pageNum = normalizePage(current);
        long sizeNum = normalizePageSize(pageSize);
        String version = getUserOrderVersion(currentUser.getId());
        String cacheKey = CACHE_FOOD_ORDER_LIST_USER_KEY + currentUser.getId() + ":" + version + ":" + pageNum + ":" + sizeNum + ":" + (status == null ? "all" : status);

        String cachedJson = stringRedisTemplate.opsForValue().get(cacheKey);
        if (StrUtil.isNotBlank(cachedJson)) {
            FoodOrderPageDTO cachedPage = JSONUtil.toBean(cachedJson, FoodOrderPageDTO.class);
            if (cachedPage == null || cachedPage.getRecords() == null) {
                return Result.ok(new ArrayList<>(), 0L);
            }
            return Result.ok(cachedPage.getRecords(), cachedPage.getTotal() == null ? 0L : cachedPage.getTotal());
        }

        LambdaQueryWrapper<FoodOrder> wrapper = new LambdaQueryWrapper<FoodOrder>()
                .eq(FoodOrder::getUserId, currentUser.getId())
                .eq(status != null, FoodOrder::getStatus, status)
                .orderByDesc(FoodOrder::getId);
        Page<FoodOrder> page = page(new Page<>(pageNum, sizeNum), wrapper);
        List<FoodOrderSimpleDTO> records = page.getRecords().stream()
                .map(this::toSimpleDTO)
                .collect(Collectors.toList());

        FoodOrderPageDTO pageDTO = new FoodOrderPageDTO();
        pageDTO.setRecords(records);
        pageDTO.setTotal(page.getTotal());
        stringRedisTemplate.opsForValue().set(cacheKey, JSONUtil.toJsonStr(pageDTO), CACHE_FOOD_ORDER_TTL, TimeUnit.MINUTES);
        return Result.ok(records, page.getTotal());
    }

    @Override
    public Result queryMyOrderDetail(Long orderId) {
        UserDTO currentUser = UserHolder.getUser();
        if (currentUser == null) {
            return Result.fail("please login first");
        }
        return queryOrderDetail(orderId, currentUser.getId(), false);
    }

    @Override
    @Transactional
    public Result cancelMyOrder(Long orderId, String cancelReason) {
        UserDTO currentUser = UserHolder.getUser();
        if (currentUser == null) {
            return Result.fail("please login first");
        }
        if (orderId == null || orderId <= 0) {
            return Result.fail("order id is required");
        }

        RLock orderLock = redissonClient.getLock(LOCK_FOOD_ORDER_KEY + orderId);
        boolean locked;
        try {
            locked = orderLock.tryLock(ORDER_STATUS_LOCK_WAIT_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Result.fail("request timeout, please retry");
        }
        if (!locked) {
            return Result.fail("order is being processed, please retry later");
        }
        try {
            FoodOrder order = getById(orderId);
            if (order == null) {
                return Result.fail("order not found");
            }
            if (!currentUser.getId().equals(order.getUserId())) {
                return Result.fail("no permission");
            }
            if (order.getStatus() == null || (order.getStatus() != STATUS_CREATED && order.getStatus() != STATUS_ACCEPTED)) {
                return Result.fail("order cannot be cancelled in current status");
            }

            FoodOrder toUpdate = new FoodOrder();
            toUpdate.setId(orderId);
            toUpdate.setStatus(STATUS_CANCELLED);
            toUpdate.setCancelReason(StrUtil.isBlank(cancelReason) ? "user cancelled order" : cancelReason);
            boolean updated = updateById(toUpdate);
            if (!updated) {
                TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
                return Result.fail("cancel failed");
            }

            Result restoreResult = restoreDishStock(orderId);
            if (Boolean.FALSE.equals(restoreResult.getSuccess())) {
                TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
                return restoreResult;
            }

            invalidateOrderDetailCache(orderId);
            bumpUserOrderVersion(order.getUserId());
            bumpAdminOrderVersion();
            return Result.ok();
        } finally {
            if (orderLock.isHeldByCurrentThread()) {
                orderLock.unlock();
            }
        }
    }

    @Override
    public Result adminQueryOrders(Integer current, Integer pageSize, Integer status, Long userId, Long windowId) {
        if (!isValidStatus(status)) {
            return Result.fail("invalid status");
        }
        if (windowId != null && windowId <= 0) {
            return Result.fail("invalid window id");
        }
        if (windowId != null && windowMapper.selectById(windowId) == null) {
            return Result.fail("window not found");
        }

        long pageNum = normalizePage(current);
        long sizeNum = normalizePageSize(pageSize);
        String version = getAdminOrderVersion();
        String cacheKey = CACHE_FOOD_ORDER_LIST_ADMIN_KEY + version + ":" + pageNum + ":" + sizeNum + ":"
                + (status == null ? "all" : status) + ":" + (userId == null ? "all" : userId) + ":"
                + (windowId == null ? "all" : windowId);

        String cachedJson = stringRedisTemplate.opsForValue().get(cacheKey);
        if (StrUtil.isNotBlank(cachedJson)) {
            FoodOrderPageDTO cachedPage = JSONUtil.toBean(cachedJson, FoodOrderPageDTO.class);
            if (cachedPage == null || cachedPage.getRecords() == null) {
                return Result.ok(new ArrayList<>(), 0L);
            }
            return Result.ok(cachedPage.getRecords(), cachedPage.getTotal() == null ? 0L : cachedPage.getTotal());
        }

        LambdaQueryWrapper<FoodOrder> wrapper = new LambdaQueryWrapper<FoodOrder>()
                .eq(status != null, FoodOrder::getStatus, status)
                .eq(userId != null, FoodOrder::getUserId, userId)
                .inSql(windowId != null, FoodOrder::getId,
                        "select distinct order_id from tb_food_order_item where window_id = " + windowId)
                .orderByDesc(FoodOrder::getId);

        Page<FoodOrder> page = page(new Page<>(pageNum, sizeNum), wrapper);
        List<FoodOrderSimpleDTO> records = page.getRecords().stream()
                .map(this::toSimpleDTO)
                .collect(Collectors.toList());

        FoodOrderPageDTO pageDTO = new FoodOrderPageDTO();
        pageDTO.setRecords(records);
        pageDTO.setTotal(page.getTotal());
        stringRedisTemplate.opsForValue().set(cacheKey, JSONUtil.toJsonStr(pageDTO), CACHE_FOOD_ORDER_TTL, TimeUnit.MINUTES);
        return Result.ok(records, page.getTotal());
    }

    @Override
    public Result adminQueryOrderDetail(Long orderId) {
        return queryOrderDetail(orderId, null, true);
    }

/**
 * 管理员更新订单状态
 *
 * @param orderId 订单 ID，必须为正整数
 * @param status 目标状态值，必须是有效的状态码
 * @param cancelReason 取消原因，仅在状态变更为已取消时有效
 * @return Result 操作结果，成功返回 Result.ok()，失败返回包含错误信息的 Result.fail()
 */
@Override
@Transactional
public Result adminUpdateStatus(Long orderId, Integer status, String cancelReason) {
    // 验证订单 ID 有效性
    if (orderId == null || orderId <= 0) {
        return Result.fail("order id is required");
    }
    // 验证目标状态合法性
    if (!isValidStatus(status)) {
        return Result.fail("invalid target status");
    }

    // 获取订单分布式锁并尝试加锁
    RLock orderLock = redissonClient.getLock(LOCK_FOOD_ORDER_KEY + orderId);
    boolean locked;
    try {
        locked = orderLock.tryLock(ORDER_STATUS_LOCK_WAIT_SECONDS, TimeUnit.SECONDS);
    } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        return Result.fail("request timeout, please retry");
    }
    if (!locked) {
        return Result.fail("order is being processed, please retry later");
    }
    try {
        // 查询订单并验证是否存在
        FoodOrder order = getById(orderId);
        if (order == null) {
            return Result.fail("order not found");
        }

        // 验证状态转换的合法性
        Integer oldStatus = order.getStatus();
        if (!isValidTransition(oldStatus, status)) {
            return Result.fail("invalid status transition");
        }

        // 构建订单更新对象并设置新状态
        FoodOrder toUpdate = new FoodOrder();
        toUpdate.setId(orderId);
        toUpdate.setStatus(status);
        if (status == STATUS_CANCELLED) {
            toUpdate.setCancelReason(StrUtil.isBlank(cancelReason) ? "admin cancelled order" : cancelReason);
        }

        // 执行数据库更新操作
        boolean updated = updateById(toUpdate);
        if (!updated) {
            TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
            return Result.fail("update status failed");
        }

        // 如果订单被取消且处于已创建或已接受状态，需要恢复菜品库存
        if (status == STATUS_CANCELLED && (oldStatus == STATUS_CREATED || oldStatus == STATUS_ACCEPTED)) {
            Result restoreResult = restoreDishStock(orderId);
            if (Boolean.FALSE.equals(restoreResult.getSuccess())) {
                TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
                return restoreResult;
            }
        }

        // 更新缓存和版本号
        invalidateOrderDetailCache(orderId);
        bumpUserOrderVersion(order.getUserId());
        bumpAdminOrderVersion();
        return Result.ok();
    } finally {
        if (orderLock.isHeldByCurrentThread()) {
            orderLock.unlock();
        }
    }
}


    private Result queryOrderDetail(Long orderId, Long currentUserId, boolean adminView) {
        if (orderId == null || orderId <= 0) {
            return Result.fail("order id is required");
        }

        FoodOrderDetailDTO dto = cacheClient.queryWithMutex(
                CACHE_FOOD_ORDER_DETAIL_KEY,
                LOCK_CACHE_FOOD_ORDER_KEY,
                orderId,
                FoodOrderDetailDTO.class,
                this::loadOrderDetailForCache,
                CACHE_FOOD_ORDER_TTL,
                TimeUnit.MINUTES
        );
        if (dto == null) {
            return Result.fail("order not found");
        }
        if (!adminView && (currentUserId == null || !currentUserId.equals(dto.getUserId()))) {
            return Result.fail("no permission to view this order");
        }

        return Result.ok(dto);
    }

    private FoodOrderDetailDTO loadOrderDetailForCache(Long orderId) {
        FoodOrder order = getById(orderId);
        if (order == null) {
            return null;
        }
        List<FoodOrderItem> items = foodOrderItemMapper.selectList(new LambdaQueryWrapper<FoodOrderItem>()
                .eq(FoodOrderItem::getOrderId, orderId)
                .orderByAsc(FoodOrderItem::getId));
        return toDetailDTO(order, items);
    }

    private Map<Long, Integer> mergeItems(List<FoodOrderItemCreateDTO> items) {
        Map<Long, Integer> merged = new LinkedHashMap<>();
        for (FoodOrderItemCreateDTO item : items) {
            if (item == null || item.getDishId() == null || item.getDishId() <= 0
                    || item.getQuantity() == null || item.getQuantity() <= 0) {
                return null;
            }
            merged.put(item.getDishId(), merged.getOrDefault(item.getDishId(), 0) + item.getQuantity());
        }
        return merged;
    }

    private String buildPickupCode(String orderNo) {
        if (orderNo == null || orderNo.length() <= 6) {
            return orderNo;
        }
        return orderNo.substring(orderNo.length() - 6);
    }

    private FoodOrderSimpleDTO toSimpleDTO(FoodOrder order) {
        FoodOrderSimpleDTO dto = new FoodOrderSimpleDTO();
        dto.setId(order.getId());
        dto.setOrderNo(order.getOrderNo());
        dto.setUserId(order.getUserId());
        dto.setTotalAmount(order.getTotalAmount());
        dto.setTotalCount(order.getTotalCount());
        dto.setStatus(order.getStatus());
        dto.setPickupCode(order.getPickupCode());
        dto.setCreateTime(order.getCreateTime());
        dto.setUpdateTime(order.getUpdateTime());
        return dto;
    }

    private FoodOrderDetailDTO toDetailDTO(FoodOrder order, List<FoodOrderItem> items) {
        FoodOrderDetailDTO dto = new FoodOrderDetailDTO();
        BeanUtil.copyProperties(toSimpleDTO(order), dto);
        dto.setRemark(order.getRemark());
        dto.setCancelReason(order.getCancelReason());

        List<FoodOrderItemDTO> itemDTOS = new ArrayList<>();
        if (items != null) {
            for (FoodOrderItem item : items) {
                FoodOrderItemDTO itemDTO = new FoodOrderItemDTO();
                itemDTO.setId(item.getId());
                itemDTO.setDishId(item.getDishId());
                itemDTO.setDishName(item.getDishName());
                itemDTO.setDishImage(item.getDishImage());
                itemDTO.setWindowId(item.getWindowId());
                itemDTO.setPrice(item.getPrice());
                itemDTO.setQuantity(item.getQuantity());
                itemDTO.setAmount(item.getAmount());
                itemDTOS.add(itemDTO);
            }
        }
        dto.setItems(itemDTOS);
        return dto;
    }

    /**
     * 下单时扣除用户积分，仅当用户积分充足时扣款成功
     *
     * @param userId 用户 ID
     * @return boolean 扣款是否成功，成功返回 true，失败（积分不足或用户不存在）返回 false
     */
    private boolean deductCreditsOnOrderCreate(Long userId) {
        // 使用原子操作扣除积分：保证积分字段非空、余额充足才更新，避免并发问题
        int updated = userInfoMapper.update(null, new LambdaUpdateWrapper<UserInfo>()
                .setSql("credits = IFNULL(credits, 0) - " + ORDER_DEDUCT_CREDITS)
                .ge(UserInfo::getCredits, ORDER_DEDUCT_CREDITS)
                .eq(UserInfo::getUserId, userId));
        return updated > 0;
    }


    private Result restoreDishStock(Long orderId) {
        List<FoodOrderItem> items = foodOrderItemMapper.selectList(new LambdaQueryWrapper<FoodOrderItem>()
                .eq(FoodOrderItem::getOrderId, orderId));
        if (items == null || items.isEmpty()) {
            return Result.fail("order items not found, cannot rollback stock");
        }

        for (FoodOrderItem item : items) {
            dishMapper.update(null, new LambdaUpdateWrapper<Dish>()
                    .setSql("quantity = quantity + " + item.getQuantity())
                    .eq(Dish::getId, item.getDishId()));
        }
        invalidateDishCachesByOrderItems(items);
        return Result.ok();
    }

    private void invalidateDishCachesByDishes(List<Dish> dishes) {
        if (dishes == null || dishes.isEmpty()) {
            return;
        }
        Set<Long> dishIds = dishes.stream().map(Dish::getId).collect(Collectors.toSet());
        Set<Long> windowIds = dishes.stream().map(Dish::getWindowId).collect(Collectors.toSet());
        clearDishRelatedCache(dishIds, windowIds);
    }

    private void invalidateDishCachesByOrderItems(List<FoodOrderItem> items) {
        if (items == null || items.isEmpty()) {
            return;
        }
        Set<Long> dishIds = items.stream().map(FoodOrderItem::getDishId).collect(Collectors.toSet());
        Set<Long> windowIds = items.stream().map(FoodOrderItem::getWindowId).collect(Collectors.toSet());
        clearDishRelatedCache(dishIds, windowIds);
    }

    private void clearDishRelatedCache(Collection<Long> dishIds, Collection<Long> windowIds) {
        List<String> keys = new ArrayList<>();
        if (dishIds != null) {
            for (Long dishId : dishIds) {
                keys.add(CACHE_DISH_DETAIL_KEY + dishId);
            }
        }
        if (windowIds != null) {
            for (Long windowId : windowIds) {
                keys.add(CACHE_DISH_LIST_BY_WINDOW_KEY + windowId);
            }
        }
        if (windowIds != null && !windowIds.isEmpty()) {
            List<Window> windows = windowMapper.selectBatchIds(windowIds);
            Set<Long> canteenIds = windows.stream().map(Window::getCanteenId).collect(Collectors.toSet());
            for (Long canteenId : canteenIds) {
                keys.add(CACHE_DISH_LIST_BY_CANTEEN_KEY + canteenId);
                keys.add(CACHE_DISH_RECOMMEND_BY_CANTEEN_KEY + canteenId);
            }
        }
        keys.add(CACHE_DISH_RANDOM_RECOMMEND_KEY);
        cacheConsistencyHelper.deleteAfterCommit(keys.toArray(new String[0]));
    }

    private boolean isValidStatus(Integer status) {
        if (status == null) {
            return true;
        }
        return status == STATUS_CREATED
                || status == STATUS_ACCEPTED
                || status == STATUS_READY
                || status == STATUS_COMPLETED
                || status == STATUS_CANCELLED;
    }

    /**
     * 验证订单状态转换是否合法
     *
     * @param from 当前状态
     * @param to 目标状态
     * @return boolean 状态转换合法返回 true，否则返回 false
     */
    private boolean isValidTransition(Integer from, Integer to) {
        // 验证空值
        if (from == null || to == null) {
            return false;
        }
        // 相同状态允许转换
        if (from.equals(to)) {
            return true;
        }

        // 已创建状态可转换为已接受或已取消
        if (from == STATUS_CREATED) {
            return to == STATUS_ACCEPTED || to == STATUS_CANCELLED;
        }

        // 已接受状态可转换为待取餐或已取消
        if (from == STATUS_ACCEPTED) {
            return to == STATUS_READY || to == STATUS_CANCELLED;
        }

        // 待取餐状态只能转换为已完成
        if (from == STATUS_READY) {
            return to == STATUS_COMPLETED;
        }

        return false;
    }

    private long normalizePage(Integer current) {
        if (current == null || current < 1) {
            return 1L;
        }
        return current.longValue();
    }

    private long normalizePageSize(Integer pageSize) {
        if (pageSize == null || pageSize < 1) {
            return 10L;
        }
        if (pageSize > 100) {
            return 100L;
        }
        return pageSize.longValue();
    }

    private void invalidateOrderDetailCache(Long orderId) {
        cacheConsistencyHelper.deleteAfterCommit(CACHE_FOOD_ORDER_DETAIL_KEY + orderId);
    }

    private String getUserOrderVersion(Long userId) {
        String key = FOOD_ORDER_USER_LIST_VERSION_KEY + userId;
        String version = stringRedisTemplate.opsForValue().get(key);
        return version == null ? "0" : version;
    }

    private String getAdminOrderVersion() {
        String version = stringRedisTemplate.opsForValue().get(FOOD_ORDER_ADMIN_LIST_VERSION_KEY);
        return version == null ? "0" : version;
    }

    private void bumpUserOrderVersion(Long userId) {
        cacheConsistencyHelper.runAfterCommit(() ->
                stringRedisTemplate.opsForValue().increment(FOOD_ORDER_USER_LIST_VERSION_KEY + userId));
    }

    private void bumpAdminOrderVersion() {
        cacheConsistencyHelper.runAfterCommit(() ->
                stringRedisTemplate.opsForValue().increment(FOOD_ORDER_ADMIN_LIST_VERSION_KEY));
    }

    private void safeUnlock(RLock lock) {
        try {
            lock.unlock();
        } catch (IllegalMonitorStateException ignored) {
            // Lock may be released already due to timeout or failover, ignore unlock race.
        }
    }

    private List<RLock> tryLockAll(List<RLock> locks, long waitTime, TimeUnit unit) throws InterruptedException {
        if (locks == null || locks.isEmpty()) {
            return Collections.emptyList();
        }
        long deadlineNanos = System.nanoTime() + unit.toNanos(waitTime);
        List<RLock> acquired = new ArrayList<>(locks.size());
        for (RLock lock : locks) {
            long remainNanos = deadlineNanos - System.nanoTime();
            if (remainNanos <= 0 || !lock.tryLock(remainNanos, TimeUnit.NANOSECONDS)) {
                unlockAll(acquired);
                return Collections.emptyList();
            }
            acquired.add(lock);
        }
        return acquired;
    }

    private void unlockAll(List<RLock> locks) {
        if (locks == null || locks.isEmpty()) {
            return;
        }
        for (int i = locks.size() - 1; i >= 0; i--) {
            safeUnlock(locks.get(i));
        }
    }

    /**
     * 验证订单环境的有效性，包括位置、窗口、食堂状态等
     *
     * @param createDTO 订单创建请求参数，包含位置信息
     * @param dishes 订单中的菜品列表，用于获取窗口信息
     * @param userId 用户 ID，用于距离验证
     * @return Result 验证结果，成功返回成功，失败返回相应错误信息
     */
    private Result validateOrderEnvironment(FoodOrderCreateDTO createDTO, List<Dish> dishes, Long userId) {
        // 验证位置参数是否完整且有效
        if (createDTO.getLongitude() == null || createDTO.getLatitude() == null) {
            return Result.fail("location is required for ordering");
        }
        if (!isValidLongitude(createDTO.getLongitude()) || !isValidLatitude(createDTO.getLatitude())) {
            return Result.fail("invalid location coordinates");
        }

        // 从菜品中提取所有窗口 ID 并验证窗口有效性
        Set<Long> windowIds = dishes.stream().map(Dish::getWindowId).collect(Collectors.toSet());
        if (windowIds.isEmpty()) {
            return Result.fail("cannot resolve window info");
        }
        List<Window> windows = windowMapper.selectBatchIds(windowIds);
        if (windows == null || windows.size() != windowIds.size()) {
            return Result.fail("contains invalid windows");
        }
        // 验证所有窗口是否处于营业状态
        for (Window window : windows) {
            if (window.getStatus() == null || window.getStatus() != 1) {
                return Result.fail("window is currently closed");
            }
        }

        // 验证所有窗口是否属于同一个食堂，不支持跨食堂下单
        Set<Long> canteenIds = windows.stream().map(Window::getCanteenId).collect(Collectors.toSet());
        if (canteenIds.size() != 1) {
            return Result.fail("cross-canteen order is not supported");
        }

        // 验证食堂是否可用且坐标配置正确
        Long canteenId = canteenIds.iterator().next();
        Canteen canteen = canteenMapper.selectById(canteenId);
        if (canteen == null || canteen.getStatus() == null || canteen.getStatus() != 1) {
            return Result.fail("canteen is not available");
        }
        if (canteen.getX() == null || canteen.getY() == null) {
            return Result.fail("canteen location is invalid");
        }

        // 验证当前时间是否在食堂营业时间内
        if (!isWithinOpenHours(canteen.getOpenHours(), LocalTime.now())) {
            return Result.fail("canteen is currently closed");
        }

        // 验证用户位置与食堂的距离是否在允许范围内
        return validateDistanceWithRedisGeo(canteen, userId, createDTO.getLongitude(), createDTO.getLatitude());
    }


    private Result validateDistanceWithRedisGeo(Canteen canteen, Long userId, Double userLongitude, Double userLatitude) {
        GeoOperations<String, String> geoOps = stringRedisTemplate.opsForGeo();
        String canteenMember = "canteen:" + canteen.getId();
        String userMember = "order-check:user:" + userId + ":" + System.nanoTime();
        try {
            geoOps.add(CANTEEN_GEO_KEY, new Point(canteen.getX(), canteen.getY()), canteenMember);
            geoOps.add(CANTEEN_GEO_KEY, new Point(userLongitude, userLatitude), userMember);
            Distance distance = geoOps.distance(CANTEEN_GEO_KEY, userMember, canteenMember, Metrics.KILOMETERS);
            if (distance == null) {
                return Result.fail("location verification failed, please retry");
            }
            if (distance.getValue() > ORDER_MAX_DISTANCE_KM) {
                return Result.fail("distance to canteen is over 1km");
            }
            return Result.ok();
        } finally {
            geoOps.remove(CANTEEN_GEO_KEY, userMember);
        }
    }

    private boolean isWithinOpenHours(String openHours, LocalTime now) {
        if (StrUtil.isBlank(openHours)) {
            return false;
        }
        String normalized = openHours.replace("，", ",").replace("；", ";");
        String[] segments = normalized.split("[,;]");
        for (String segment : segments) {
            String slot = StrUtil.trim(segment);
            if (StrUtil.isBlank(slot)) {
                continue;
            }
            String[] range = slot.split("-");
            if (range.length != 2) {
                continue;
            }
            LocalTime start = parseHourMinute(range[0]);
            LocalTime end = parseHourMinute(range[1]);
            if (start == null || end == null) {
                continue;
            }
            if (isTimeInRange(now, start, end)) {
                return true;
            }
        }
        return false;
    }

    private LocalTime parseHourMinute(String value) {
        try {
            return LocalTime.parse(StrUtil.trim(value), OPEN_HOURS_FORMATTER);
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    private boolean isTimeInRange(LocalTime now, LocalTime start, LocalTime end) {
        if (start.equals(end)) {
            return true;
        }
        if (start.isBefore(end)) {
            return !now.isBefore(start) && !now.isAfter(end);
        }
        return !now.isBefore(start) || !now.isAfter(end);
    }

    private boolean isValidLongitude(Double longitude) {
        return longitude != null && longitude >= -180 && longitude <= 180;
    }

    private boolean isValidLatitude(Double latitude) {
        return latitude != null && latitude >= -90 && latitude <= 90;
    }
}
