package com.iCanteen.service.impl;

import cn.hutool.core.util.BooleanUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.iCanteen.dto.CanteenCrowdDTO;
import com.iCanteen.dto.Result;
import com.iCanteen.dto.UserDTO;
import com.iCanteen.entity.Canteen;
import com.iCanteen.entity.CrowdReport;
import com.iCanteen.entity.UserInfo;
import com.iCanteen.mapper.CanteenMapper;
import com.iCanteen.mapper.CrowdReportMapper;
import com.iCanteen.mapper.UserInfoMapper;
import com.iCanteen.service.ICanteenService;
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
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static com.iCanteen.utils.RedisConstants.CACHE_CANTEEN_CROWD_LIST_KEY;
import static com.iCanteen.utils.RedisConstants.CACHE_CANTEEN_DETAIL_KEY;
import static com.iCanteen.utils.RedisConstants.CACHE_CANTEEN_LIST_KEY;
import static com.iCanteen.utils.RedisConstants.CACHE_CANTEEN_TTL;
import static com.iCanteen.utils.RedisConstants.CANTEEN_GEO_KEY;
import static com.iCanteen.utils.RedisConstants.CROWD_REPORT_LIMIT_KEY;
import static com.iCanteen.utils.RedisConstants.CROWD_REPORT_LIMIT_TTL;
import static com.iCanteen.utils.RedisConstants.LOCK_CANTEEN_KEY;

@Service
public class CanteenServiceImpl extends ServiceImpl<CanteenMapper, Canteen> implements ICanteenService {

    private static final String LIST_CACHE_SUFFIX = "all";
    private static final double REPORT_MAX_DISTANCE_METERS = 300D;
    private static final int CROWD_REPORT_REWARD_CREDITS = 10;

    @Resource
    private CrowdReportMapper crowdReportMapper;
    @Resource
    private UserInfoMapper userInfoMapper;
    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Resource
    private CacheClient cacheClient;
    @Resource
    private CacheConsistencyHelper cacheConsistencyHelper;

    @Override
    public Result queryAllCanteens() {
        List<Canteen> canteens = cacheClient.queryWithMutex(
                CACHE_CANTEEN_LIST_KEY + ":",
                LOCK_CANTEEN_KEY,
                LIST_CACHE_SUFFIX,
                List.class,
                k -> loadAllActiveCanteens(),
                CACHE_CANTEEN_TTL,
                TimeUnit.MINUTES
        );
        return Result.ok(canteens == null ? new ArrayList<>() : canteens);
    }

    @Override
    public Result queryCanteenById(Long id) {
        if (id == null) {
            return Result.fail("食堂id不能为空");
        }
        Canteen canteen = cacheClient.queryWithLogicalExpire(
                CACHE_CANTEEN_DETAIL_KEY,
                LOCK_CANTEEN_KEY,
                id,
                Canteen.class,
                this::loadActiveCanteenById,
                CACHE_CANTEEN_TTL,
                TimeUnit.MINUTES
        );
        if (canteen == null) {
            return Result.fail("食堂不存在");
        }
        return Result.ok(canteen);
    }

    @Override
    public Result queryCanteenCrowdOverview() {
        List<CanteenCrowdDTO> result = cacheClient.queryWithMutex(
                CACHE_CANTEEN_CROWD_LIST_KEY + ":",
                LOCK_CANTEEN_KEY,
                LIST_CACHE_SUFFIX,
                List.class,
                k -> loadCanteenCrowdOverview(),
                CACHE_CANTEEN_TTL,
                TimeUnit.MINUTES
        );
        return Result.ok(result == null ? new ArrayList<>() : result);
    }

    /**
     * 上报食堂拥挤等级信息
     *
     * @param canteenId 食堂 ID，不能为空
     * @param crowdLevel 拥挤等级，取值范围 0-2（0 表示空闲，1 表示适中，2 表示拥挤）
     * @param longitude 用户当前位置的经度，不能为空
     * @param latitude 用户当前位置的纬度，不能为空
     * @return Result 操作结果，成功返回成功，失败返回相应错误信息
     */
    @Override
    @Transactional
    public Result reportCrowdLevel(Long canteenId, Integer crowdLevel, Double longitude, Double latitude) {
        // 参数校验：确保所有必填参数不为空
        if (canteenId == null || crowdLevel == null || longitude == null || latitude == null) {
            return Result.fail("参数不能为空");
        }
        // 参数校验：拥挤等级仅支持 0、1、2 三个值
        if (crowdLevel < 0 || crowdLevel > 2) {
            return Result.fail("拥挤等级仅支持 0、1、2");
        }

        // 查询食堂信息并验证食堂状态是否正常
        Canteen canteen = getById(canteenId);
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

        // 频率限制：同一用户对同一食堂在指定时间内只能上报一次
        String reportLimitKey = CROWD_REPORT_LIMIT_KEY + canteenId + ":" + userId;
        Boolean firstReport = stringRedisTemplate.opsForValue()
                .setIfAbsent(reportLimitKey, "1", CROWD_REPORT_LIMIT_TTL, TimeUnit.MINUTES);
        if (!BooleanUtil.isTrue(firstReport)) {
            return Result.fail("上报过于频繁，请稍后再试");
        }

        // 创建拥挤度上报记录并保存到数据库
        CrowdReport report = new CrowdReport();
        report.setCanteenId(canteenId);
        report.setUserId(userId);
        report.setCrowdLevel(crowdLevel);
        report.setCreateTime(LocalDateTime.now());
        crowdReportMapper.insert(report);

        // 奖励用户积分
        rewardCredits(userId, CROWD_REPORT_REWARD_CREDITS);

        // 刷新食堂拥挤度统计信息并更新数据库
        refreshCrowd(canteen);
        updateById(canteen);

        // 清除食堂缓存，确保下次查询获取最新数据
        evictCanteenCache(canteenId);
        return Result.ok();
    }

    private List<Canteen> loadAllActiveCanteens() {
        List<Canteen> canteens = list(new LambdaQueryWrapper<Canteen>()
                .eq(Canteen::getStatus, 1)
                .orderByAsc(Canteen::getId));
        for (Canteen canteen : canteens) {
            refreshCrowd(canteen);
        }
        canteens.sort(Comparator
                .comparing((Canteen c) -> c.getCrowdLevel() == null ? Integer.MAX_VALUE : c.getCrowdLevel())
                .thenComparing(c -> c.getCrowdScore() == null ? Integer.MAX_VALUE : c.getCrowdScore())
                .thenComparing(c -> c.getId() == null ? Long.MAX_VALUE : c.getId()));
        return canteens;
    }

    private Canteen loadActiveCanteenById(Long id) {
        Canteen canteen = getById(id);
        if (canteen == null || canteen.getStatus() == null || canteen.getStatus() != 1) {
            return null;
        }
        refreshCrowd(canteen);
        return canteen;
    }

    private List<CanteenCrowdDTO> loadCanteenCrowdOverview() {
        List<Canteen> canteens = list(new LambdaQueryWrapper<Canteen>()
                .eq(Canteen::getStatus, 1)
                .orderByAsc(Canteen::getId));

        List<CanteenCrowdDTO> result = new ArrayList<>(canteens.size());
        for (Canteen canteen : canteens) {
            refreshCrowd(canteen);
            CanteenCrowdDTO dto = new CanteenCrowdDTO();
            dto.setCanteenId(canteen.getId());
            dto.setName(canteen.getName());
            dto.setCrowdLevel(canteen.getCrowdLevel());
            dto.setCrowdScore(canteen.getCrowdScore());
            result.add(dto);
        }

        result.sort(Comparator
                .comparing((CanteenCrowdDTO c) -> c.getCrowdLevel() == null ? Integer.MAX_VALUE : c.getCrowdLevel())
                .thenComparing(c -> c.getCrowdScore() == null ? Integer.MAX_VALUE : c.getCrowdScore())
                .thenComparing(c -> c.getCanteenId() == null ? Long.MAX_VALUE : c.getCanteenId()));
        return result;
    }

    private void refreshCrowd(Canteen canteen) {
        LocalDateTime thirtyMinutesAgo = LocalDateTime.now().minusMinutes(30);
        List<CrowdReport> reports = crowdReportMapper.selectList(
                new LambdaQueryWrapper<CrowdReport>()
                        .eq(CrowdReport::getCanteenId, canteen.getId())
                        .ge(CrowdReport::getCreateTime, thirtyMinutesAgo)
        );
        if (reports.isEmpty()) {
            return;
        }

        int totalScore = 0;
        for (CrowdReport report : reports) {
            totalScore += report.getCrowdLevel() * 33 + 33;
        }

        int avgScore = totalScore / reports.size();
        canteen.setCrowdScore(avgScore);
        if (avgScore < 40) {
            canteen.setCrowdLevel(0);
        } else if (avgScore < 70) {
            canteen.setCrowdLevel(1);
        } else {
            canteen.setCrowdLevel(2);
        }
    }

    private void evictCanteenCache(Long canteenId) {
        if (canteenId == null) {
            cacheConsistencyHelper.deleteAfterCommit(CACHE_CANTEEN_LIST_KEY + ":" + LIST_CACHE_SUFFIX,
                    CACHE_CANTEEN_CROWD_LIST_KEY + ":" + LIST_CACHE_SUFFIX);
            return;
        }
        cacheConsistencyHelper.deleteAfterCommit(
                CACHE_CANTEEN_LIST_KEY + ":" + LIST_CACHE_SUFFIX,
                CACHE_CANTEEN_CROWD_LIST_KEY + ":" + LIST_CACHE_SUFFIX,
                CACHE_CANTEEN_DETAIL_KEY + canteenId
        );
    }

    private boolean isWithinDistanceMetersByGeo(Long canteenId,
                                                double userLongitude,
                                                double userLatitude,
                                                double canteenLongitude,
                                                double canteenLatitude,
                                                double maxDistanceMeters) {
        GeoOperations<String, String> geoOps = stringRedisTemplate.opsForGeo();
        String canteenMember = "canteen:" + canteenId;
        String userMember = "crowd-report:user:" + System.nanoTime();
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
