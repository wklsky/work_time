package com.worktime.dto.request;

import lombok.Data;

@Data
public class UpdateSettingRequest {
    private Boolean showWorktimeAfterClockout;
    private String timeFormat;
    private String homeCalendarView;
    private String defaultInputMode;
}
