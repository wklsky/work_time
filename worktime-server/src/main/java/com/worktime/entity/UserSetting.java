package com.worktime.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("user_setting")
public class UserSetting {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private Integer showWorktimeAfterClockout;

    private String timeFormat;

    private String homeCalendarView;

    private String defaultInputMode;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
