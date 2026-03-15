package com.iCanteen.controller;

import cn.hutool.core.util.StrUtil;
import com.iCanteen.dto.Result;
import com.iCanteen.dto.WindowCreateDTO;
import com.iCanteen.dto.WindowUpdateDTO;
import com.iCanteen.entity.Window;
import com.iCanteen.service.IDishService;
import com.iCanteen.service.IWindowService;
import com.iCanteen.utils.AdminPermissionService;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;

import static com.iCanteen.utils.RedisConstants.CACHE_DISH_LIST_BY_WINDOW_KEY;
import static com.iCanteen.utils.RedisConstants.CACHE_WINDOW_DETAIL_KEY;
import static com.iCanteen.utils.RedisConstants.CACHE_WINDOW_LIST_BY_CANTEEN_KEY;

@RestController
@RequestMapping("/window")
public class WindowController {

    @Resource
    private IWindowService windowService;
    @Resource
    private IDishService dishService;
    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Resource
    private AdminPermissionService adminPermissionService;

    @GetMapping("/list")
    public Result queryWindowsByCanteenId(@RequestParam("canteenId") Long canteenId) {
        return windowService.queryWindowsByCanteenId(canteenId);
    }

    @GetMapping("/{id}")
    public Result queryWindowById(@PathVariable("id") Long id) {
        return windowService.queryWindowById(id);
    }

    @GetMapping("/{id}/dishes")
    public Result queryDishesByWindowId(@PathVariable("id") Long id) {
        return dishService.queryDishesByWindowId(id);
    }

    @PostMapping("/report-wait-time")
    public Result reportWaitTime(@RequestParam("windowId") Long windowId,
                                 @RequestParam("waitTime") Integer waitTime,
                                 @RequestParam("longitude") Double longitude,
                                 @RequestParam("latitude") Double latitude) {
        return windowService.reportWaitTime(windowId, waitTime, longitude, latitude);
    }

    @PostMapping
    public Result createWindow(@RequestBody WindowCreateDTO dto) {
        Result authResult = adminPermissionService.ensureAdmin();
        if (authResult != null) {
            return authResult;
        }
        if (dto == null || dto.getCanteenId() == null || StrUtil.isBlank(dto.getName())) {
            return Result.fail("新增窗口参数不完整");
        }
        if (dto.getStatus() != null && dto.getStatus() != 0 && dto.getStatus() != 1) {
            return Result.fail("状态值仅支持0或1");
        }

        Window window = new Window();
        window.setCanteenId(dto.getCanteenId());
        window.setName(dto.getName());
        window.setDescription(dto.getDescription());
        window.setWaitTime(dto.getWaitTime() == null ? 0 : dto.getWaitTime());
        window.setWaitTimeLevel(dto.getWaitTimeLevel() == null ? 0 : dto.getWaitTimeLevel());
        window.setStatus(dto.getStatus() == null ? 1 : dto.getStatus());
        window.setSort(dto.getSort() == null ? 0 : dto.getSort());
        boolean saved = windowService.save(window);
        if (!saved) {
            return Result.fail("新增窗口失败");
        }

        stringRedisTemplate.delete(CACHE_WINDOW_LIST_BY_CANTEEN_KEY + window.getCanteenId());
        return Result.ok(window.getId());
    }

    @PutMapping
    public Result updateWindow(@RequestBody WindowUpdateDTO dto) {
        Result authResult = adminPermissionService.ensureAdmin();
        if (authResult != null) {
            return authResult;
        }
        if (dto == null || dto.getId() == null) {
            return Result.fail("修改窗口必须传入id");
        }
        if (dto.getStatus() != null && dto.getStatus() != 0 && dto.getStatus() != 1) {
            return Result.fail("状态值仅支持0或1");
        }

        Window oldWindow = windowService.getById(dto.getId());
        if (oldWindow == null) {
            return Result.fail("窗口不存在");
        }

        Window window = new Window();
        window.setId(dto.getId());
        window.setCanteenId(dto.getCanteenId());
        window.setName(dto.getName());
        window.setDescription(dto.getDescription());
        window.setWaitTime(dto.getWaitTime());
        window.setWaitTimeLevel(dto.getWaitTimeLevel());
        window.setStatus(dto.getStatus());
        window.setSort(dto.getSort());

        boolean updated = windowService.updateById(window);
        if (!updated) {
            return Result.fail("修改窗口失败");
        }

        stringRedisTemplate.delete(CACHE_WINDOW_DETAIL_KEY + dto.getId());
        stringRedisTemplate.delete(CACHE_DISH_LIST_BY_WINDOW_KEY + dto.getId());
        if (dto.getCanteenId() != null) {
            stringRedisTemplate.delete(CACHE_WINDOW_LIST_BY_CANTEEN_KEY + dto.getCanteenId());
        }
        if (oldWindow.getCanteenId() != null) {
            stringRedisTemplate.delete(CACHE_WINDOW_LIST_BY_CANTEEN_KEY + oldWindow.getCanteenId());
        }
        return Result.ok();
    }

    @DeleteMapping("/{id}")
    public Result deleteWindow(@PathVariable("id") Long id) {
        Result authResult = adminPermissionService.ensureAdmin();
        if (authResult != null) {
            return authResult;
        }
        if (id == null) {
            return Result.fail("删除窗口必须传入id");
        }

        Window oldWindow = windowService.getById(id);
        if (oldWindow == null) {
            return Result.fail("窗口不存在");
        }

        boolean removed = windowService.removeById(id);
        if (!removed) {
            return Result.fail("删除窗口失败");
        }

        stringRedisTemplate.delete(CACHE_WINDOW_DETAIL_KEY + id);
        stringRedisTemplate.delete(CACHE_DISH_LIST_BY_WINDOW_KEY + id);
        if (oldWindow.getCanteenId() != null) {
            stringRedisTemplate.delete(CACHE_WINDOW_LIST_BY_CANTEEN_KEY + oldWindow.getCanteenId());
        }
        return Result.ok();
    }
}
