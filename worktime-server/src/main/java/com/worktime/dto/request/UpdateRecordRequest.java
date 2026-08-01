package com.worktime.dto.request;

import lombok.Data;

@Data
public class UpdateRecordRequest {
    private String clockIn;
    private String clockOut;
    private String remark;
}
