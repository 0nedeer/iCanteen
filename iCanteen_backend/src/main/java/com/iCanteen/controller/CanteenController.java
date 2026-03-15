package com.iCanteen.controller;

import cn.hutool.core.util.StrUtil;
import com.iCanteen.dto.CanteenCreateDTO;
import com.iCanteen.dto.CanteenUpdateDTO;
import com.iCanteen.dto.Result;
import com.iCanteen.entity.Canteen;
import com.iCanteen.service.ICanteenService;
import com.iCanteen.service.IDishService;
import com.iCanteen.service.IWindowService;
import com.iCanteen.utils.AdminPermissionService;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;

import static com.iCanteen.utils.RedisConstants.CACHE_CANTEEN_CROWD_LIST_KEY;
import static com.iCanteen.utils.RedisConstants.CACHE_CANTEEN_DETAIL_KEY;
import static com.iCanteen.utils.RedisConstants.CACHE_CANTEEN_LIST_KEY;
import static com.iCanteen.utils.RedisConstants.CACHE_DISH_LIST_BY_CANTEEN_KEY;
import static com.iCanteen.utils.RedisConstants.CACHE_DISH_RECOMMEND_BY_CANTEEN_KEY;
import static com.iCanteen.utils.RedisConstants.CACHE_WINDOW_LIST_BY_CANTEEN_KEY;

@RestController
@RequestMapping("/canteen")
public class CanteenController {

    @Resource
    private ICanteenService canteenService;
    @Resource
    private IDishService dishService;
    @Resource
    private IWindowService windowService;
    @Resource
    private AdminPermissionService adminPermissionService;
    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @GetMapping("/list")
    public Result queryAllCanteens() {
        return canteenService.queryAllCanteens();
    }

    @GetMapping("/crowd")
    public Result queryCanteenCrowdOverview() {
        return canteenService.queryCanteenCrowdOverview();
    }

    @GetMapping("/{id}")
    public Result queryCanteenById(@PathVariable("id") Long id) {
        return canteenService.queryCanteenById(id);
    }

    @GetMapping("/{id}/windows")
    public Result queryWindowsByCanteen(@PathVariable("id") Long id) {
        return windowService.queryWindowsByCanteenId(id);
    }

    @GetMapping("/{id}/dishes")
    public Result queryDishesByCanteen(@PathVariable("id") Long id) {
        return dishService.queryTodayDishesByCanteenId(id);
    }

    @PostMapping("/report-crowd")
    public Result reportCrowdLevel(@RequestParam("canteenId") Long canteenId,
                                   @RequestParam("crowdLevel") Integer crowdLevel,
                                   @RequestParam("longitude") Double longitude,
                                   @RequestParam("latitude") Double latitude) {
        return canteenService.reportCrowdLevel(canteenId, crowdLevel, longitude, latitude);
    }

    @PostMapping
    public Result createCanteen(@RequestBody CanteenCreateDTO dto) {
        Result authResult = adminPermissionService.ensureAdmin();
        if (authResult != null) {
            return authResult;
        }
        if (dto == null || StrUtil.isBlank(dto.getName()) || StrUtil.isBlank(dto.getAddress())
                || dto.getX() == null || dto.getY() == null) {
            return Result.fail("新增食堂参数不完整");
        }
        if (dto.getStatus() != null && dto.getStatus() != 0 && dto.getStatus() != 1) {
            return Result.fail("状态值仅支持0或1");
        }

        Canteen canteen = new Canteen();
        canteen.setName(dto.getName());
        canteen.setAddress(dto.getAddress());
        canteen.setImages(dto.getImages());
        canteen.setX(dto.getX());
        canteen.setY(dto.getY());
        canteen.setOpenHours(dto.getOpenHours());
        canteen.setStatus(dto.getStatus() == null ? 1 : dto.getStatus());
        boolean saved = canteenService.save(canteen);
        if (!saved) {
            return Result.fail("新增食堂失败");
        }

        clearCanteenCache(canteen.getId());
        return Result.ok(canteen.getId());
    }

    @PutMapping
    public Result updateCanteen(@RequestBody CanteenUpdateDTO dto) {
        Result authResult = adminPermissionService.ensureAdmin();
        if (authResult != null) {
            return authResult;
        }
        if (dto == null || dto.getId() == null) {
            return Result.fail("修改食堂必须传入id");
        }
        if (dto.getStatus() != null && dto.getStatus() != 0 && dto.getStatus() != 1) {
            return Result.fail("状态值仅支持0或1");
        }
        Canteen old = canteenService.getById(dto.getId());
        if (old == null) {
            return Result.fail("食堂不存在");
        }

        Canteen canteen = new Canteen();
        canteen.setId(dto.getId());
        canteen.setName(dto.getName());
        canteen.setAddress(dto.getAddress());
        canteen.setImages(dto.getImages());
        canteen.setX(dto.getX());
        canteen.setY(dto.getY());
        canteen.setOpenHours(dto.getOpenHours());
        canteen.setStatus(dto.getStatus());
        boolean updated = canteenService.updateById(canteen);
        if (!updated) {
            return Result.fail("修改食堂失败");
        }

        clearCanteenCache(dto.getId());
        return Result.ok();
    }

    @DeleteMapping("/{id}")
    public Result deleteCanteen(@PathVariable("id") Long id) {
        Result authResult = adminPermissionService.ensureAdmin();
        if (authResult != null) {
            return authResult;
        }
        if (id == null) {
            return Result.fail("删除食堂必须传入id");
        }
        boolean removed = canteenService.removeById(id);
        if (!removed) {
            return Result.fail("食堂不存在或删除失败");
        }
        clearCanteenCache(id);
        return Result.ok();
    }

    private void clearCanteenCache(Long canteenId) {
        stringRedisTemplate.delete(CACHE_CANTEEN_LIST_KEY);
        stringRedisTemplate.delete(CACHE_CANTEEN_CROWD_LIST_KEY);
        if (canteenId != null) {
            stringRedisTemplate.delete(CACHE_CANTEEN_DETAIL_KEY + canteenId);
            stringRedisTemplate.delete(CACHE_WINDOW_LIST_BY_CANTEEN_KEY + canteenId);
            stringRedisTemplate.delete(CACHE_DISH_LIST_BY_CANTEEN_KEY + canteenId);
            stringRedisTemplate.delete(CACHE_DISH_RECOMMEND_BY_CANTEEN_KEY + canteenId);
        }
    }
}
