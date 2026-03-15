package com.iCanteen.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.iCanteen.dto.Result;
import com.iCanteen.entity.Window;

public interface IWindowService extends IService<Window> {

    Result queryWindowsByCanteenId(Long canteenId);

    Result queryWindowById(Long id);

    Result reportWaitTime(Long windowId, Integer waitTime, Double longitude, Double latitude);
}

