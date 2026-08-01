package com.worktime.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.worktime.entity.StatisticsDaily;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDate;
import java.util.List;

@Mapper
public interface StatisticsDailyMapper extends BaseMapper<StatisticsDaily> {

    @Select("SELECT * FROM statistics_daily WHERE user_id = #{userId} AND stat_date = #{statDate} LIMIT 1")
    StatisticsDaily selectByUserAndDate(@Param("userId") Long userId, @Param("statDate") LocalDate statDate);

    @Select("SELECT * FROM statistics_daily WHERE user_id = #{userId} AND stat_date >= #{startDate} AND stat_date <= #{endDate} ORDER BY stat_date")
    List<StatisticsDaily> selectByUserAndDateRange(@Param("userId") Long userId,
                                                    @Param("startDate") LocalDate startDate,
                                                    @Param("endDate") LocalDate endDate);
}
