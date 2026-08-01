package com.worktime.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;
import java.time.LocalTime;

@Data
@TableName("work_rule")
public class WorkRule {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private String ruleName;

    private LocalTime startTime;

    private LocalTime endTime;

    private LocalTime restStart;

    private LocalTime restEnd;

    private Integer bufferMinutes;

    private LocalTime dayCutTime;

    private String workDays;

    private Integer isDefault;

    private Integer status;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
