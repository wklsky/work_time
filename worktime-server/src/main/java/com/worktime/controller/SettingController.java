package com.worktime.controller;

import com.worktime.common.Result;
import com.worktime.dto.request.UpdateSettingRequest;
import com.worktime.dto.request.UpdateWorkRuleRequest;
import com.worktime.security.SecurityUtils;
import com.worktime.service.SettingService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/setting")
@RequiredArgsConstructor
public class SettingController {

    private final SettingService settingService;

    @GetMapping
    public Result<?> getSetting() {
        return Result.success(settingService.getSetting(SecurityUtils.getCurrentUserId()));
    }

    @PutMapping
    public Result<?> updateSetting(@RequestBody UpdateSettingRequest request) {
        return Result.success(settingService.updateSetting(
                SecurityUtils.getCurrentUserId(),
                request.getShowWorktimeAfterClockout(),
                request.getTimeFormat(),
                request.getHomeCalendarView(),
                request.getDefaultInputMode()));
    }

    @GetMapping("/work-rule")
    public Result<?> getWorkRule() {
        return Result.success(settingService.getWorkRule(SecurityUtils.getCurrentUserId()));
    }

    @PutMapping("/work-rule")
    public Result<?> updateWorkRule(@RequestBody UpdateWorkRuleRequest request) {
        return Result.success(settingService.updateWorkRule(
                SecurityUtils.getCurrentUserId(),
                request.getStartTime(), request.getEndTime(),
                request.getRestStart(), request.getRestEnd(),
                request.getBufferMinutes(), request.getDayCutTime(),
                request.getWorkDays()));
    }
}
