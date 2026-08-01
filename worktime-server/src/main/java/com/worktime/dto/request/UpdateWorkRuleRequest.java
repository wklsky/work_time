package com.worktime.dto.request;

import lombok.Data;

@Data
public class UpdateWorkRuleRequest {
    private String startTime;
    private String endTime;
    private String restStart;
    private String restEnd;
    private Integer bufferMinutes;
    private String dayCutTime;
    private String workDays;
}
