package com.worktime.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("export_record")
public class ExportRecord {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private String exportType;

    private LocalDate startDate;

    private LocalDate endDate;

    private String fileFormat;

    private String fileUrl;

    private String status;

    private String errorMsg;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
