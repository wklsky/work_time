package com.worktime.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ManualRecordRequest {
    @NotBlank(message = "开始时间不能为空")
    private String clockIn;

    private String clockOut;

    private String remark;
}
