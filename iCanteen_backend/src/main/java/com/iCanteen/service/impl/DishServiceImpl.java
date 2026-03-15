package com.iCanteen.service.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.iCanteen.dto.Result;
import com.iCanteen.entity.CanteenRecommend;
import com.iCanteen.entity.Dish;
import com.iCanteen.entity.Window;
import com.iCanteen.mapper.CanteenRecommendMapper;
import com.iCanteen.mapper.DishMapper;
import com.iCanteen.mapper.WindowMapper;
import com.iCanteen.service.IDishService;
import com.iCanteen.utils.CacheClient;
import com.iCanteen.utils.CacheConsistencyHelper;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static com.iCanteen.utils.RedisConstants.CACHE_CANTEEN_TTL;
import static com.iCanteen.utils.RedisConstants.CACHE_DISH_DETAIL_KEY;
import static com.iCanteen.utils.RedisConstants.CACHE_DISH_LIST_BY_CANTEEN_KEY;
import static com.iCanteen.utils.RedisConstants.CACHE_DISH_LIST_BY_WINDOW_KEY;
import static com.iCanteen.utils.RedisConstants.CACHE_DISH_RANDOM_RECOMMEND_KEY;
import static com.iCanteen.utils.RedisConstants.CACHE_DISH_RECOMMEND_BY_CANTEEN_KEY;
import static com.iCanteen.utils.RedisConstants.CACHE_DISH_RECOMMEND_TTL;
import static com.iCanteen.utils.RedisConstants.LOCK_DISH_KEY;

@Service
public class DishServiceImpl extends ServiceImpl<DishMapper, Dish> implements IDishService {

    @Resource
    private WindowMapper windowMapper;
    @Resource
    private CanteenRecommendMapper canteenRecommendMapper;
    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Resource
    private CacheClient cacheClient;
    @Resource
    private CacheConsistencyHelper cacheConsistencyHelper;

    @Override
    public Result queryDishesByWindowId(Long windowId) {
        if (windowId == null) {
            return Result.fail("窗口id不能为空");
        }
        String listCacheKey = CACHE_DISH_LIST_BY_WINDOW_KEY + windowId;
        String cachedJson = stringRedisTemplate.opsForValue().get(listCacheKey);
        if (StrUtil.isNotBlank(cachedJson)) {
            return Result.ok(JSONUtil.toList(cachedJson, Dish.class));
        }

        List<Dish> dishes = list(new LambdaQueryWrapper<Dish>()
                .eq(Dish::getWindowId, windowId)
                .eq(Dish::getStatus, 1)
                .orderByDesc(Dish::getIsToday)
                .orderByDesc(Dish::getAvgRating)
                .orderByDesc(Dish::getRatingCount)
                .orderByAsc(Dish::getId));

        stringRedisTemplate.opsForValue().set(listCacheKey, JSONUtil.toJsonStr(dishes), CACHE_CANTEEN_TTL, TimeUnit.MINUTES);
        return Result.ok(dishes);
    }

    @Override
    public Result queryTodayDishesByCanteenId(Long canteenId) {
        if (canteenId == null) {
            return Result.fail("食堂id不能为空");
        }
        String listCacheKey = CACHE_DISH_LIST_BY_CANTEEN_KEY + canteenId;
        String cachedJson = stringRedisTemplate.opsForValue().get(listCacheKey);
        if (StrUtil.isNotBlank(cachedJson)) {
            return Result.ok(JSONUtil.toList(cachedJson, Dish.class));
        }

        List<Long> windowIds = listActiveWindowIdsByCanteen(canteenId);
        if (windowIds.isEmpty()) {
            return Result.ok(new ArrayList<>());
        }

        List<Dish> dishes = list(new LambdaQueryWrapper<Dish>()
                .in(Dish::getWindowId, windowIds)
                .eq(Dish::getIsToday, 1)
                .eq(Dish::getStatus, 1)
                .orderByDesc(Dish::getAvgRating)
                .orderByDesc(Dish::getRatingCount));

        stringRedisTemplate.opsForValue().set(listCacheKey, JSONUtil.toJsonStr(dishes), CACHE_CANTEEN_TTL, TimeUnit.MINUTES);
        return Result.ok(dishes);
    }

    @Override
    public Result getRandomRecommendedDish() {
        String cachedJson = stringRedisTemplate.opsForValue().get(CACHE_DISH_RANDOM_RECOMMEND_KEY);
        if (StrUtil.isNotBlank(cachedJson)) {
            return Result.ok(JSONUtil.toBean(cachedJson, Dish.class));
        }

        List<Dish> dishes = list(new LambdaQueryWrapper<Dish>()
                .eq(Dish::getIsToday, 1)
                .eq(Dish::getStatus, 1)
                .ge(Dish::getAvgRating, 4.5)
                .orderByDesc(Dish::getAvgRating)
                .orderByDesc(Dish::getRatingCount)
                .orderByAsc(Dish::getId));
        if (dishes.isEmpty()) {
            return Result.fail("暂无推荐菜式");
        }

        Dish randomDish = dishes.get(new Random().nextInt(dishes.size()));
        stringRedisTemplate.opsForValue().set(
                CACHE_DISH_RANDOM_RECOMMEND_KEY,
                JSONUtil.toJsonStr(randomDish),
                CACHE_DISH_RECOMMEND_TTL,
                TimeUnit.MINUTES
        );
        return Result.ok(randomDish);
    }

    @Override
    public Result queryDishById(Long id) {
        if (id == null) {
            return Result.fail("菜品id不能为空");
        }
        Dish dish = cacheClient.queryWithLogicalExpire(
                CACHE_DISH_DETAIL_KEY,
                LOCK_DISH_KEY,
                id,
                Dish.class,
                this::loadActiveDishById,
                CACHE_CANTEEN_TTL,
                TimeUnit.MINUTES
        );
        if (dish == null) {
            return Result.fail("抱歉，没有找到该菜品");
        }
        return Result.ok(dish);
    }

    @Override
    public Result queryRecommendedDishByCanteenId(Long canteenId) {
        if (canteenId == null) {
            return Result.fail("食堂id不能为空");
        }

        String cacheKey = CACHE_DISH_RECOMMEND_BY_CANTEEN_KEY + canteenId;
        String cachedJson = stringRedisTemplate.opsForValue().get(cacheKey);
        if (StrUtil.isNotBlank(cachedJson)) {
            return Result.ok(JSONUtil.toList(cachedJson, Dish.class));
        }

        List<Long> windowIds = listActiveWindowIdsByCanteen(canteenId);
        if (windowIds.isEmpty()) {
            return Result.fail("该食堂暂无推荐菜品");
        }

        List<Dish> allActiveDishes = list(new LambdaQueryWrapper<Dish>()
                .in(Dish::getWindowId, windowIds)
                .eq(Dish::getStatus, 1)
                .orderByDesc(Dish::getAvgRating)
                .orderByDesc(Dish::getRatingCount)
                .orderByAsc(Dish::getId));
        if (allActiveDishes.isEmpty()) {
            return Result.fail("该食堂暂无推荐菜品");
        }

        CanteenRecommend recommend = canteenRecommendMapper.selectById(canteenId);
        if (recommend == null || recommend.getDishId1() == null || recommend.getDishId2() == null || recommend.getDishId3() == null) {
            return Result.fail("推荐菜品暂未设定");
        }

        List<Long> manualIds = Arrays.asList(recommend.getDishId1(), recommend.getDishId2(), recommend.getDishId3());
        Set<Long> manualIdSet = new LinkedHashSet<>(manualIds);
        if (manualIdSet.size() != 3) {
            return Result.fail("推荐菜品设定有误");
        }

        List<Dish> manualDishesRaw = list(new LambdaQueryWrapper<Dish>()
                .in(Dish::getId, manualIds)
                .in(Dish::getWindowId, windowIds)
                .eq(Dish::getStatus, 1));
        if (manualDishesRaw.size() != 3) {
            return Result.fail("推荐菜品设定有误");
        }

        Map<Long, Dish> manualMap = new HashMap<>(3);
        for (Dish dish : manualDishesRaw) {
            manualMap.put(dish.getId(), dish);
        }
        List<Dish> manualOrdered = new ArrayList<>(3);
        for (Long manualId : manualIds) {
            Dish dish = manualMap.get(manualId);
            if (dish == null) {
                return Result.fail("推荐菜品设定有误");
            }
            manualOrdered.add(dish);
        }

        List<Dish> nonManualCandidates = allActiveDishes.stream()
                .filter(d -> !manualIdSet.contains(d.getId()))
                .collect(Collectors.toList());
        if (nonManualCandidates.isEmpty()) {
            return Result.fail("推荐菜品数量不足");
        }

        BigDecimal bestAvailableScore = nonManualCandidates.get(0).getAvgRating() == null
                ? BigDecimal.ZERO : nonManualCandidates.get(0).getAvgRating();
        List<Dish> randomPool = nonManualCandidates.stream()
                .filter(d -> {
                    BigDecimal score = d.getAvgRating() == null ? BigDecimal.ZERO : d.getAvgRating();
                    return score.compareTo(bestAvailableScore) == 0;
                })
                .collect(Collectors.toList());

        Dish autoDish = randomPool.get(new Random().nextInt(randomPool.size()));

        List<Dish> result = new ArrayList<>(4);
        result.add(autoDish);
        result.addAll(manualOrdered);

        if (result.stream().map(Dish::getId).collect(Collectors.toSet()).size() != 4) {
            return Result.fail("推荐菜品数量不足");
        }

        stringRedisTemplate.opsForValue().set(cacheKey, JSONUtil.toJsonStr(result), CACHE_DISH_RECOMMEND_TTL, TimeUnit.MINUTES);
        return Result.ok(result);
    }

    @Override
    @Transactional
    public Result setManualRecommendedDishes(Long canteenId, List<Long> dishIds) {
        if (canteenId == null) {
            return Result.fail("食堂id不能为空");
        }
        if (dishIds == null || dishIds.size() != 3) {
            return Result.fail("至少需要三道菜品id");
        }
        Set<Long> uniqueIds = new LinkedHashSet<>(dishIds);
        if (uniqueIds.size() != 3) {
            return Result.fail("菜品id不能一致");
        }
        if (uniqueIds.stream().anyMatch(id -> id == null || id <= 0)) {
            return Result.fail("菜品必须存在e");
        }

        List<Long> windowIds = listActiveWindowIdsByCanteen(canteenId);
        if (windowIds.isEmpty()) {
            return Result.fail("食堂或者窗口不存在");
        }

        List<Dish> dishes = list(new LambdaQueryWrapper<Dish>()
                .in(Dish::getId, uniqueIds)
                .in(Dish::getWindowId, windowIds)
                .eq(Dish::getStatus, 1));
        if (dishes.size() != 3) {
            return Result.fail("所有菜品必须属于同一食堂");
        }

        List<Long> orderedIds = new ArrayList<>(uniqueIds);
        canteenRecommendMapper.upsert(canteenId, orderedIds.get(0), orderedIds.get(1), orderedIds.get(2));
        cacheConsistencyHelper.deleteAfterCommit(CACHE_DISH_RECOMMEND_BY_CANTEEN_KEY + canteenId);
        return Result.ok("success");
    }

    @Override
    @Transactional
    public Result createDish(Dish dish) {
        if (dish == null) {
            return Result.fail("创建菜品失败");
        }
        if (dish.getId() != null) {
            return Result.fail("创建菜品不需要id");
        }
        if (dish.getWindowId() == null) {
            return Result.fail("窗口id为必填信息");
        }
        Window window = windowMapper.selectById(dish.getWindowId());
        if (window == null) {
            return Result.fail("没有找到该窗口");
        }

        boolean saved = save(dish);
        if (!saved) {
            return Result.fail("创建菜品失败!");
        }

        clearDishCache(dish.getId(), dish.getWindowId(), window.getCanteenId());
        return Result.ok(dish.getId());
    }

    @Override
    @Transactional
    public Result updateDish(Dish dish) {
        if (dish == null || dish.getId() == null) {
            return Result.fail("菜品id为必填信息");
        }
        Dish oldDish = getById(dish.getId());
        if (oldDish == null) {
            return Result.fail("没有找到该菜品");
        }

        Window newWindow = null;
        if (dish.getWindowId() != null) {
            newWindow = windowMapper.selectById(dish.getWindowId());
            if (newWindow == null) {
                return Result.fail("没有找到该窗口");
            }
        }

        boolean updated = updateById(dish);
        if (!updated) {
            return Result.fail("更新菜品失败");
        }

        Long oldCanteenId = findCanteenIdByWindowId(oldDish.getWindowId());
        Long newWindowId = dish.getWindowId() == null ? oldDish.getWindowId() : dish.getWindowId();
        Long newCanteenId = newWindow != null ? newWindow.getCanteenId() : oldCanteenId;

        clearDishCache(dish.getId(), oldDish.getWindowId(), oldCanteenId);
        if (newWindowId != null && !newWindowId.equals(oldDish.getWindowId())) {
            clearDishCache(dish.getId(), newWindowId, newCanteenId);
        } else if (newCanteenId != null && (oldCanteenId == null || !newCanteenId.equals(oldCanteenId))) {
            clearDishCache(dish.getId(), newWindowId, newCanteenId);
        }
        return Result.ok();
    }

    @Override
    @Transactional
    public Result deleteDish(Long id) {
        if (id == null) {
            return Result.fail("菜品id为必填信息");
        }
        Dish oldDish = getById(id);
        if (oldDish == null) {
            return Result.fail("没有找到该菜品");
        }

        boolean removed = removeById(id);
        if (!removed) {
            return Result.fail("删除菜品失败");
        }

        Long canteenId = findCanteenIdByWindowId(oldDish.getWindowId());
        clearDishCache(id, oldDish.getWindowId(), canteenId);
        return Result.ok();
    }

    private List<Long> listActiveWindowIdsByCanteen(Long canteenId) {
        return windowMapper.selectList(new LambdaQueryWrapper<Window>()
                        .eq(Window::getCanteenId, canteenId)
                        .eq(Window::getStatus, 1))
                .stream()
                .map(Window::getId)
                .collect(Collectors.toList());
    }

    private Long findCanteenIdByWindowId(Long windowId) {
        if (windowId == null) {
            return null;
        }
        Window window = windowMapper.selectById(windowId);
        return window == null ? null : window.getCanteenId();
    }

    private Dish loadActiveDishById(Long dishId) {
        Dish dish = getById(dishId);
        if (dish == null || dish.getStatus() == null || dish.getStatus() != 1) {
            return null;
        }
        return dish;
    }

    private void clearDishCache(Long dishId, Long windowId, Long canteenId) {
        List<String> keys = new ArrayList<>();
        if (dishId != null) {
            keys.add(CACHE_DISH_DETAIL_KEY + dishId);
        }
        if (windowId != null) {
            keys.add(CACHE_DISH_LIST_BY_WINDOW_KEY + windowId);
        }
        if (canteenId != null) {
            keys.add(CACHE_DISH_LIST_BY_CANTEEN_KEY + canteenId);
            keys.add(CACHE_DISH_RECOMMEND_BY_CANTEEN_KEY + canteenId);
        }
        keys.add(CACHE_DISH_RANDOM_RECOMMEND_KEY);
        cacheConsistencyHelper.deleteAfterCommit(keys.toArray(new String[0]));
    }
}
