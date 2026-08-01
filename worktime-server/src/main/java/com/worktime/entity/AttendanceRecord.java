package com.worktime.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("attendance_record")
public class AttendanceRecord {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private LocalDate workDate;

    private LocalDateTime clockIn;

    private LocalDateTime clockOut;

    private Integer workMinutes;

    private Integer overtimeMinutes;

    private String source;

    private Integer crossDay;

    private Long ruleId;

    private String remark;

    private String status;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
