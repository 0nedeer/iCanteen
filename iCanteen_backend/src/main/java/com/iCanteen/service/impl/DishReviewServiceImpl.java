package com.iCanteen.service.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.iCanteen.dto.DishReviewPageDTO;
import com.iCanteen.dto.DishReviewSubmitDTO;
import com.iCanteen.dto.Result;
import com.iCanteen.entity.Dish;
import com.iCanteen.entity.DishReview;
import com.iCanteen.entity.FoodOrderItem;
import com.iCanteen.entity.UserInfo;
import com.iCanteen.entity.Window;
import com.iCanteen.mapper.DishMapper;
import com.iCanteen.mapper.DishReviewMapper;
import com.iCanteen.mapper.FoodOrderItemMapper;
import com.iCanteen.mapper.UserInfoMapper;
import com.iCanteen.mapper.WindowMapper;
import com.iCanteen.service.IDishReviewService;
import com.iCanteen.utils.CacheConsistencyHelper;
import com.iCanteen.utils.SystemConstants;
import com.iCanteen.utils.UserHolder;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static com.iCanteen.utils.RedisConstants.CACHE_DISH_DETAIL_KEY;
import static com.iCanteen.utils.RedisConstants.CACHE_DISH_LIST_BY_CANTEEN_KEY;
import static com.iCanteen.utils.RedisConstants.CACHE_DISH_LIST_BY_WINDOW_KEY;
import static com.iCanteen.utils.RedisConstants.CACHE_DISH_RANDOM_RECOMMEND_KEY;
import static com.iCanteen.utils.RedisConstants.CACHE_DISH_RECOMMEND_BY_CANTEEN_KEY;
import static com.iCanteen.utils.RedisConstants.CACHE_DISH_REVIEW_LIST_KEY;
import static com.iCanteen.utils.RedisConstants.CACHE_DISH_REVIEW_TTL;
import static com.iCanteen.utils.RedisConstants.DISH_REVIEW_LIST_VERSION_KEY;

@Service
public class DishReviewServiceImpl extends ServiceImpl<DishReviewMapper, DishReview> implements IDishReviewService {

    private static final int CANCELLED_ORDER_STATUS = 4;
    private static final int REVIEW_REWARD_CREDITS = 3;

    @Resource
    private DishMapper dishMapper;
    @Resource
    private FoodOrderItemMapper foodOrderItemMapper;
    @Resource
    private UserInfoMapper userInfoMapper;
    @Resource
    private WindowMapper windowMapper;
    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Resource
    private CacheConsistencyHelper cacheConsistencyHelper;

    /**
     * 提交菜品评价
     *
     * @param dto 菜品评价提交数据传输对象，包含菜品 ID、评分、内容、标签和图片等信息
     * @return Result 操作结果，成功返回 Result.ok()，失败返回包含错误信息的 Result.fail()
     */
    @Override
    @Transactional
    public Result submitReview(DishReviewSubmitDTO dto) {
        // 验证必填字段是否存在
        if (dto == null || dto.getDishId() == null || dto.getRating() == null) {
            return Result.fail("dishId, rating and content are required");
        }
        // 验证评价内容格式和长度
        if (dto.getContent() == null || dto.getContent().trim().isEmpty()) {
            return Result.fail("content is required");
        }
        if (dto.getContent().length() > 512) {
            return Result.fail("content must be no more than 512 characters");
        }
        // 验证评分范围
        if (dto.getRating() < 1 || dto.getRating() > 5) {
            return Result.fail("rating must be between 1 and 5");
        }

        // 获取当前登录用户 ID
        Long userId = currentUserId();
        if (userId == null) {
            return Result.fail("please login first");
        }
        // 验证菜品是否可用
        if (!dishAvailable(dto.getDishId())) {
            return Result.fail("dish not found or unavailable");
        }
        // 验证用户是否购买过该菜品
        if (!hasOrderedDish(userId, dto.getDishId())) {
            return Result.fail("only users who have ordered this dish can review");
        }

        // 插入或更新评价记录
        upsertReview(
                dto.getDishId(),
                userId,
                dto.getRating(),
                dto.getContent().trim(),
                dto.getTags(),
                dto.getImages()
        );

        // 评价成功后奖励积分
        rewardCreditsOnReview(userId);

        // 更新菜品总体评分
        updateDishRating(dto.getDishId());

        // 清除评价和菜品相关缓存
        evictReviewAndDishCaches(dto.getDishId());
        return Result.ok();
    }

    @Override
    public Result queryReviewsByDishId(Long dishId, Integer current) {
        if (currentUserId() == null) {
            return Result.fail("please login first");
        }
        if (dishId == null) {
            return Result.fail("dishId is required");
        }
        if (current == null || current < 1) {
            current = 1;
        }

        String version = getDishReviewVersion(dishId);
        String cacheKey = CACHE_DISH_REVIEW_LIST_KEY + dishId + ":" + version + ":" + current;
        String cachedJson = stringRedisTemplate.opsForValue().get(cacheKey);
        if (StrUtil.isNotBlank(cachedJson)) {
            DishReviewPageDTO cachedPage = JSONUtil.toBean(cachedJson, DishReviewPageDTO.class);
            if (cachedPage == null || cachedPage.getRecords() == null) {
                return Result.ok(new java.util.ArrayList<>(), 0L);
            }
            return Result.ok(cachedPage.getRecords(), cachedPage.getTotal() == null ? 0L : cachedPage.getTotal());
        }

        Page<DishReview> page = new Page<>(current, SystemConstants.DEFAULT_PAGE_SIZE);
        Page<DishReview> reviewPage = page(page, new LambdaQueryWrapper<DishReview>()
                .eq(DishReview::getDishId, dishId)
                .orderByDesc(DishReview::getCreateTime));

        DishReviewPageDTO pageDTO = new DishReviewPageDTO();
        pageDTO.setRecords(reviewPage.getRecords());
        pageDTO.setTotal(reviewPage.getTotal());
        stringRedisTemplate.opsForValue().set(cacheKey, JSONUtil.toJsonStr(pageDTO), CACHE_DISH_REVIEW_TTL, TimeUnit.MINUTES);
        return Result.ok(reviewPage.getRecords(), reviewPage.getTotal());
    }

    @Override
    public Result queryMyReviews(Integer current) {
        Long userId = currentUserId();
        if (userId == null) {
            return Result.fail("please login first");
        }
        if (current == null || current < 1) {
            current = 1;
        }

        Page<DishReview> page = new Page<>(current, SystemConstants.DEFAULT_PAGE_SIZE);
        Page<DishReview> reviewPage = page(page, new LambdaQueryWrapper<DishReview>()
                .eq(DishReview::getUserId, userId)
                .orderByDesc(DishReview::getCreateTime));
        return Result.ok(reviewPage.getRecords(), reviewPage.getTotal());
    }

    @Override
    @Transactional
    public Result deleteMyReview(Long reviewId) {
        Long userId = currentUserId();
        if (userId == null) {
            return Result.fail("please login first");
        }
        if (reviewId == null) {
            return Result.fail("reviewId is required");
        }

        DishReview review = getById(reviewId);
        if (review == null) {
            return Result.fail("review not found");
        }
        if (!userId.equals(review.getUserId())) {
            return Result.fail("no permission to delete this review");
        }

        Long dishId = review.getDishId();
        boolean removed = removeById(reviewId);
        if (!removed) {
            return Result.fail("delete review failed");
        }

        updateDishRating(dishId);
        evictReviewAndDishCaches(dishId);
        return Result.ok();
    }

    private Long currentUserId() {
        if (UserHolder.getUser() == null) {
            return null;
        }
        return UserHolder.getUser().getId();
    }

    private boolean dishAvailable(Long dishId) {
        Dish dish = dishMapper.selectById(dishId);
        return dish != null && dish.getStatus() != null && dish.getStatus() == 1;
    }

    private boolean hasOrderedDish(Long userId, Long dishId) {
        QueryWrapper<FoodOrderItem> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("dish_id", dishId)
                .inSql("order_id", "SELECT id FROM tb_food_order WHERE user_id = " + userId + " AND status <> " + CANCELLED_ORDER_STATUS);
        return foodOrderItemMapper.selectCount(queryWrapper) > 0;
    }

    private void upsertReview(
            Long dishId,
            Long userId,
            Integer rating,
            String content,
            String tags,
            String images
    ) {
        DishReview existed = getOne(new LambdaQueryWrapper<DishReview>()
                .eq(DishReview::getDishId, dishId)
                .eq(DishReview::getUserId, userId), false);
        LocalDateTime now = LocalDateTime.now();

        if (existed != null) {
            existed.setRating(rating);
            existed.setContent(content);
            existed.setTags(tags);
            existed.setImages(images);
            existed.setUpdateTime(now);
            updateById(existed);
            return;
        }

        DishReview review = new DishReview();
        review.setDishId(dishId);
        review.setUserId(userId);
        review.setRating(rating);
        review.setContent(content);
        review.setTags(tags);
        review.setImages(images);
        review.setCreateTime(now);
        review.setUpdateTime(now);
        try {
            save(review);
        } catch (DuplicateKeyException e) {
            DishReview latest = getOne(new LambdaQueryWrapper<DishReview>()
                    .eq(DishReview::getDishId, dishId)
                    .eq(DishReview::getUserId, userId), false);
            if (latest != null) {
                latest.setRating(rating);
                latest.setContent(content);
                latest.setTags(tags);
                latest.setImages(images);
                latest.setUpdateTime(now);
                updateById(latest);
            } else {
                throw e;
            }
        }
    }

    /**
     * 更新菜品的平均评分和评价数量
     *
     * @param dishId 菜品 ID
     */
    private void updateDishRating(Long dishId) {
        // 构建查询统计该菜品的平均评分和评价数量
        QueryWrapper<DishReview> queryWrapper = new QueryWrapper<>();
        queryWrapper.select("COALESCE(AVG(rating), 0) AS avg_rating", "COUNT(1) AS rating_count")
                .eq("dish_id", dishId);
        Map<String, Object> aggregate = getMap(queryWrapper);

        // 解析聚合结果
        BigDecimal avgRating = BigDecimal.ZERO;
        int ratingCount = 0;
        if (aggregate != null) {
            avgRating = toBigDecimal(aggregate.get("avg_rating")).setScale(1, RoundingMode.HALF_UP);
            ratingCount = toInt(aggregate.get("rating_count"));
        }

        // 更新菜品表中的评分统计信息
        Dish dish = new Dish();
        dish.setId(dishId);
        dish.setAvgRating(avgRating);
        dish.setRatingCount(ratingCount);
        dishMapper.updateById(dish);
    }

    private BigDecimal toBigDecimal(Object value) {
        if (value == null) {
            return BigDecimal.ZERO;
        }
        if (value instanceof BigDecimal) {
            return (BigDecimal) value;
        }
        if (value instanceof Number) {
            return BigDecimal.valueOf(((Number) value).doubleValue());
        }
        return new BigDecimal(value.toString());
    }

    private int toInt(Object value) {
        if (value == null) {
            return 0;
        }
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        return Integer.parseInt(value.toString());
    }

    private String getDishReviewVersion(Long dishId) {
        String version = stringRedisTemplate.opsForValue().get(DISH_REVIEW_LIST_VERSION_KEY + dishId);
        return version == null ? "0" : version;
    }

    private void bumpDishReviewVersion(Long dishId) {
        stringRedisTemplate.opsForValue().increment(DISH_REVIEW_LIST_VERSION_KEY + dishId);
    }

    private void evictReviewAndDishCaches(Long dishId) {
        cacheConsistencyHelper.runAfterCommit(() -> bumpDishReviewVersion(dishId));
        List<String> keys = new ArrayList<>();
        keys.add(CACHE_DISH_DETAIL_KEY + dishId);
        keys.add(CACHE_DISH_RANDOM_RECOMMEND_KEY);
        Dish dish = dishMapper.selectById(dishId);
        if (dish == null || dish.getWindowId() == null) {
            cacheConsistencyHelper.deleteAfterCommit(keys.toArray(new String[0]));
            return;
        }
        keys.add(CACHE_DISH_LIST_BY_WINDOW_KEY + dish.getWindowId());

        Window window = windowMapper.selectById(dish.getWindowId());
        if (window != null && window.getCanteenId() != null) {
            keys.add(CACHE_DISH_LIST_BY_CANTEEN_KEY + window.getCanteenId());
            keys.add(CACHE_DISH_RECOMMEND_BY_CANTEEN_KEY + window.getCanteenId());
        }
        cacheConsistencyHelper.deleteAfterCommit(keys.toArray(new String[0]));
    }

    private void rewardCreditsOnReview(Long userId) {
        int updated = userInfoMapper.update(null, new LambdaUpdateWrapper<UserInfo>()
                .setSql("credits = IFNULL(credits, 0) + " + REVIEW_REWARD_CREDITS)
                .eq(UserInfo::getUserId, userId));
        if (updated > 0) {
            return;
        }

        UserInfo userInfo = new UserInfo();
        userInfo.setUserId(userId);
        userInfo.setCredits(REVIEW_REWARD_CREDITS);
        try {
            userInfoMapper.insert(userInfo);
        } catch (DuplicateKeyException e) {
            userInfoMapper.update(null, new LambdaUpdateWrapper<UserInfo>()
                    .setSql("credits = IFNULL(credits, 0) + " + REVIEW_REWARD_CREDITS)
                    .eq(UserInfo::getUserId, userId));
        }
    }
}
