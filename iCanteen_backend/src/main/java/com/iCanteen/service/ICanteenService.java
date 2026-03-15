package com.iCanteen.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.iCanteen.dto.Result;
import com.iCanteen.entity.Canteen;


public interface ICanteenService extends IService<Canteen> {

    Result queryAllCanteens();

    Result queryCanteenById(Long id);

    Result queryCanteenCrowdOverview();

    Result reportCrowdLevel(Long canteenId, Integer crowdLevel, Double longitude, Double latitude);
}

