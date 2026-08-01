package com.worktime.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.worktime.common.Result;
import com.worktime.entity.*;
import com.worktime.mapper.AttendanceRecordMapper;
import com.worktime.mapper.UserSettingMapper;
import com.worktime.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.*;

@RestController
@RequestMapping("/api/index")
@RequiredArgsConstructor
public class IndexController {

    private final AttendanceRecordMapper recordMapper;
    private final UserSettingMapper settingMapper;

    @GetMapping("/today")
    public Result<Map<String, Object>> getToday() {
        Long userId = SecurityUtils.getCurrentUserId();
        LocalDate today = LocalDate.now();

        // 获取当日记录
        List<AttendanceRecord> records = recordMapper.selectByUserAndDate(userId, today);

        AttendanceRecord pending = records.stream()
                .filter(r -> "PENDING".equals(r.getStatus())).findFirst().orElse(null);
        List<AttendanceRecord> completed = records.stream()
                .filter(r -> "COMPLETED".equals(r.getStatus())).toList();

        String clockStatus;
        if (pending != null) clockStatus = "WORKING";
        else if (!completed.isEmpty()) clockStatus = "COMPLETED";
        else clockStatus = "NOT_STARTED";

        int totalWork = completed.stream().mapToInt(r -> r.getWorkMinutes() != null ? r.getWorkMinutes() : 0).sum();
        int totalOt = completed.stream().mapToInt(r -> r.getOvertimeMinutes() != null ? r.getOvertimeMinutes() : 0).sum();

        // 日期信息
        Map<String, Object> dateInfo = new LinkedHashMap<>();
        dateInfo.put("date", today.toString());
        dateInfo.put("weekday", getWeekday(today));
        dateInfo.put("weekdayNum", today.getDayOfWeek().getValue());
        dateInfo.put("isToday", true);

        // 今日汇总
        Map<String, Object> todaySummary = new LinkedHashMap<>();
        todaySummary.put("totalWorkMinutes", totalWork);
        todaySummary.put("totalOvertimeMinutes", totalOt);
        todaySummary.put("segmentCount", records.size());
        todaySummary.put("firstClockIn", completed.isEmpty() ? null : completed.get(0).getClockIn());
        todaySummary.put("lastClockOut", completed.isEmpty() ? null : completed.get(completed.size() - 1).getClockOut());

        // 用户设置 — user_setting 的主键是 id, 不是 user_id
        LambdaQueryWrapper<UserSetting> settingQuery = new LambdaQueryWrapper<>();
        settingQuery.eq(UserSetting::getUserId, userId);
        UserSetting setting = settingMapper.selectOne(settingQuery);

        Map<String, Object> data = new HashMap<>();
        data.put("dateInfo", dateInfo);
        data.put("clockStatus", clockStatus);
        data.put("currentRecord", pending);
        data.put("todaySummary", todaySummary);
        data.put("todayRecords", records);

        Map<String, Object> us = new HashMap<>();
        us.put("showWorktimeAfterClockout", setting != null && setting.getShowWorktimeAfterClockout() == 1);
        us.put("timeFormat", setting != null ? setting.getTimeFormat() : "24H");
        us.put("homeCalendarView", setting != null ? setting.getHomeCalendarView() : "TODAY");
        us.put("defaultInputMode", setting != null ? setting.getDefaultInputMode() : "BUTTON");
        data.put("userSetting", us);

        return Result.success(data);
    }

    private String getWeekday(LocalDate date) {
        return switch (date.getDayOfWeek()) {
            case MONDAY -> "星期一"; case TUESDAY -> "星期二"; case WEDNESDAY -> "星期三";
            case THURSDAY -> "星期四"; case FRIDAY -> "星期五"; case SATURDAY -> "星期六";
            case SUNDAY -> "星期日";
        };
    }
}
