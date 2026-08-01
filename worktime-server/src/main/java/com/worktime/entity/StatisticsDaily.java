package com.worktime.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("statistics_daily")
public class StatisticsDaily {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private LocalDate statDate;

    private Integer attendanceCount;

    private Integer workMinutes;

    private Integer overtimeMinutes;

    private LocalDateTime firstClockIn;

    private LocalDateTime lastClockOut;

    private LocalDateTime updateTime;
}
