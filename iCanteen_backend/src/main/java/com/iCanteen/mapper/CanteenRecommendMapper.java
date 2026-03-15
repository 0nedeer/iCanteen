package com.iCanteen.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.iCanteen.entity.CanteenRecommend;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface CanteenRecommendMapper extends BaseMapper<CanteenRecommend> {

    @Insert("INSERT INTO tb_canteen_recommend(canteen_id, dish_id1, dish_id2, dish_id3) " +
            "VALUES(#{canteenId}, #{dishId1}, #{dishId2}, #{dishId3}) " +
            "ON DUPLICATE KEY UPDATE " +
            "dish_id1 = VALUES(dish_id1), dish_id2 = VALUES(dish_id2), dish_id3 = VALUES(dish_id3)")
    int upsert(@Param("canteenId") Long canteenId,
               @Param("dishId1") Long dishId1,
               @Param("dishId2") Long dishId2,
               @Param("dishId3") Long dishId3);
}
