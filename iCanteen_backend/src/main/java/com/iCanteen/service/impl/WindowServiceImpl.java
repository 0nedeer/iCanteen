package com.iCanteen.service.impl;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.iCanteen.dto.Result;
import com.iCanteen.dto.UserDTO;
import com.iCanteen.entity.Canteen;
import com.iCanteen.entity.UserInfo;
import com.iCanteen.entity.WaitTimeReport;
import com.iCanteen.entity.Window;
import com.iCanteen.mapper.CanteenMapper;
import com.iCanteen.mapper.UserInfoMapper;
import com.iCanteen.mapper.WaitTimeReportMapper;
import com.iCanteen.mapper.WindowMapper;
import com.iCanteen.service.IWindowService;
import com.iCanteen.utils.CacheClient;
import com.iCanteen.utils.CacheConsistencyHelper;
import com.iCanteen.utils.UserHolder;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.geo.Distance;
import org.springframework.data.geo.Metrics;
import org.springframework.data.geo.Point;
import org.springframework.data.redis.core.GeoOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static com.iCanteen.utils.RedisConstants.CACHE_CANTEEN_TTL;
import static com.iCanteen.utils.RedisConstants.CANTEEN_GEO_KEY;
import static com.iCanteen.utils.RedisConstants.CACHE_WINDOW_DETAIL_KEY;
import static com.iCanteen.utils.RedisConstants.CACHE_WINDOW_LIST_BY_CANTEEN_KEY;
import static com.iCanteen.utils.RedisConstants.LOCK_WINDOW_KEY;
import static com.iCanteen.utils.RedisConstants.WAIT_REPORT_LIMIT_KEY;
import static com.iCanteen.utils.RedisConstants.WAIT_REPORT_LIMIT_TTL;

@Service
public class WindowServiceImpl extends ServiceImpl<WindowMapper, Window> implements IWindowService {

    private static final double REPORT_MAX_DISTANCE_METERS = 300D;
    private static final int WAIT_TIME_REPORT_REWARD_CREDITS = 20;

    @Resource
    private WaitTimeReportMapper waitTimeReportMapper;
    @Resource
    private CanteenMapper canteenMapper;
    @Resource
    private UserInfoMapper userInfoMapper;
    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Resource
    private CacheClient cacheClient;
    @Resource
    private CacheConsistencyHelper cacheConsistencyHelper;

    @Override
    public Result queryWindowsByCanteenId(Long canteenId) {
        if (canteenId == null) {
            return Result.fail("食堂id不能为空");
        }
        String cacheKey = CACHE_WINDOW_LIST_BY_CANTEEN_KEY + canteenId;
        String cachedJson = stringRedisTemplate.opsForValue().get(cacheKey);
        if (StrUtil.isNotBlank(cachedJson)) {
            return Result.ok(JSONUtil.toList(cachedJson, Window.class));
        }

        List<Window> windows = list(new LambdaQueryWrapper<Window>()
                .eq(Window::getCanteenId, canteenId)
                .eq(Window::getStatus, 1)
                .orderByAsc(Window::getSort)
                .orderByAsc(Window::getId));
        for (Window window : windows) {
            refreshWaitTime(window);
        }
        stringRedisTemplate.opsForValue().set(cacheKey, JSONUtil.toJsonStr(windows), CACHE_CANTEEN_TTL, TimeUnit.MINUTES);
        return Result.ok(windows);
    }

    @Override
    public Result queryWindowById(Long id) {
        if (id == null) {
            return Result.fail("窗口id不能为空");
        }
        Window window = cacheClient.queryWithLogicalExpire(
                CACHE_WINDOW_DETAIL_KEY,
                LOCK_WINDOW_KEY,
                id,
                Window.class,
                this::loadActiveWindowById,
                CACHE_CANTEEN_TTL,
                TimeUnit.MINUTES
        );
        if (window == null) {
            return Result.fail("窗口不存在");
        }
        return Result.ok(window);
    }

    /**
     * 上报窗口排队等待时间信息
     *
     * @param windowId 窗口 ID，不能为空
     * @param waitTime 排队等待时间（单位：分钟），不能为空且不能小于 0
     * @param longitude 用户当前位置的经度，不能为空
     * @param latitude 用户当前位置的纬度，不能为空
     * @return Result 操作结果，成功返回成功，失败返回相应错误信息
     */
    @Override
    @Transactional
    public Result reportWaitTime(Long windowId, Integer waitTime, Double longitude, Double latitude) {
        // 参数校验：确保所有必填参数不为空
        if (windowId == null || waitTime == null || longitude == null || latitude == null) {
            return Result.fail("参数不能为空");
        }
        // 参数校验：排队时长不能为负数
        if (waitTime < 0) {
            return Result.fail("排队时长不能小于 0");
        }

        // 查询窗口信息并验证窗口状态是否正常
        Window window = getById(windowId);
        if (window == null || window.getStatus() == null || window.getStatus() != 1) {
            return Result.fail("窗口不存在");
        }

        // 查询窗口所属食堂并验证食堂状态是否正常
        Canteen canteen = canteenMapper.selectById(window.getCanteenId());
        if (canteen == null || canteen.getStatus() == null || canteen.getStatus() != 1) {
            return Result.fail("食堂不存在");
        }
        // 验证食堂是否已配置坐标信息
        if (canteen.getX() == null || canteen.getY() == null) {
            return Result.fail("食堂坐标未配置，无法上报");
        }
        // 验证用户位置是否在食堂有效上报范围内（300 米）
        if (!isWithinDistanceMetersByGeo(canteen.getId(), longitude, latitude, canteen.getX(), canteen.getY(), REPORT_MAX_DISTANCE_METERS)) {
            return Result.fail("距离食堂超过 300m，无法上报");
        }

        // 获取当前登录用户信息
        UserDTO currentUser = UserHolder.getUser();
        if (currentUser == null) {
            return Result.fail("请先登录");
        }
        Long userId = currentUser.getId();

        // 频率限制：同一用户对同一窗口在 30 分钟内只能上报一次
        String reportLimitKey = WAIT_REPORT_LIMIT_KEY + windowId + ":" + userId;
        Boolean firstReport = stringRedisTemplate.opsForValue()
                .setIfAbsent(reportLimitKey, "1", WAIT_REPORT_LIMIT_TTL, TimeUnit.MINUTES);
        if (!BooleanUtil.isTrue(firstReport)) {
            return Result.fail("30 分钟内只能上报一次");
        }

        // 创建排队时间上报记录并保存到数据库
        WaitTimeReport report = new WaitTimeReport();
        report.setWindowId(windowId);
        report.setUserId(userId);
        report.setWaitTime(waitTime);
        report.setCreateTime(LocalDateTime.now());
        waitTimeReportMapper.insert(report);

        // 奖励用户积分
        rewardCredits(userId, WAIT_TIME_REPORT_REWARD_CREDITS);

        // 刷新窗口排队时间统计信息并更新数据库
        refreshWaitTime(window);
        updateById(window);

        // 清除窗口缓存，确保下次查询获取最新数据
        evictWindowCache(windowId, window.getCanteenId());
        return Result.ok();
    }


    private Window loadActiveWindowById(Long id) {
        Window window = getById(id);
        if (window == null || window.getStatus() == null || window.getStatus() != 1) {
            return null;
        }
        refreshWaitTime(window);
        return window;
    }

    private void refreshWaitTime(Window window) {
        LocalDateTime thirtyMinutesAgo = LocalDateTime.now().minusMinutes(30);
        List<WaitTimeReport> reports = waitTimeReportMapper.selectList(
                new LambdaQueryWrapper<WaitTimeReport>()
                        .eq(WaitTimeReport::getWindowId, window.getId())
                        .ge(WaitTimeReport::getCreateTime, thirtyMinutesAgo)
        );
        if (reports.isEmpty()) {
            return;
        }

        int totalWaitTime = 0;
        for (WaitTimeReport report : reports) {
            totalWaitTime += report.getWaitTime();
        }

        int avgWaitTime = totalWaitTime / reports.size();
        window.setWaitTime(avgWaitTime);
        if (avgWaitTime < 5) {
            window.setWaitTimeLevel(0);
        } else if (avgWaitTime <= 10) {
            window.setWaitTimeLevel(1);
        } else {
            window.setWaitTimeLevel(2);
        }
    }

    private void evictWindowCache(Long windowId, Long canteenId) {
        if (windowId != null && canteenId != null) {
            cacheConsistencyHelper.deleteAfterCommit(
                    CACHE_WINDOW_DETAIL_KEY + windowId,
                    CACHE_WINDOW_LIST_BY_CANTEEN_KEY + canteenId
            );
            return;
        }
        if (windowId != null) {
            cacheConsistencyHelper.deleteAfterCommit(CACHE_WINDOW_DETAIL_KEY + windowId);
        }
        if (canteenId != null) {
            cacheConsistencyHelper.deleteAfterCommit(CACHE_WINDOW_LIST_BY_CANTEEN_KEY + canteenId);
        }
    }

    private boolean isWithinDistanceMetersByGeo(Long canteenId,
                                                double userLongitude,
                                                double userLatitude,
                                                double canteenLongitude,
                                                double canteenLatitude,
                                                double maxDistanceMeters) {
        GeoOperations<String, String> geoOps = stringRedisTemplate.opsForGeo();
        String canteenMember = "canteen:" + canteenId;
        String userMember = "wait-report:user:" + System.nanoTime();
        try {
            geoOps.add(CANTEEN_GEO_KEY, new Point(canteenLongitude, canteenLatitude), canteenMember);
            geoOps.add(CANTEEN_GEO_KEY, new Point(userLongitude, userLatitude), userMember);
            Distance distance = geoOps.distance(CANTEEN_GEO_KEY, userMember, canteenMember, Metrics.KILOMETERS);
            if (distance == null) {
                return false;
            }
            return distance.getValue() <= maxDistanceMeters / 1000D;
        } finally {
            geoOps.remove(CANTEEN_GEO_KEY, userMember);
        }
    }

    private void rewardCredits(Long userId, int rewardCredits) {
        int updated = userInfoMapper.update(null, new LambdaUpdateWrapper<UserInfo>()
                .setSql("credits = IFNULL(credits, 0) + " + rewardCredits)
                .eq(UserInfo::getUserId, userId));
        if (updated > 0) {
            return;
        }

        UserInfo userInfo = new UserInfo();
        userInfo.setUserId(userId);
        userInfo.setCredits(rewardCredits);
        try {
            userInfoMapper.insert(userInfo);
        } catch (DuplicateKeyException e) {
            userInfoMapper.update(null, new LambdaUpdateWrapper<UserInfo>()
                    .setSql("credits = IFNULL(credits, 0) + " + rewardCredits)
                    .eq(UserInfo::getUserId, userId));
        }
    }
}
