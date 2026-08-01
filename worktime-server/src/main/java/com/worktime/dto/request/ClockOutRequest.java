package com.worktime.dto.request;

import lombok.Data;

@Data
public class ClockOutRequest {
    private Long recordId;
    private String remark;
}
