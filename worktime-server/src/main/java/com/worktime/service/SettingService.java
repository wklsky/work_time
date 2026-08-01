package com.worktime.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.worktime.common.BusinessException;
import com.worktime.common.ErrorCode;
import com.worktime.entity.UserSetting;
import com.worktime.entity.WorkRule;
import com.worktime.mapper.UserSettingMapper;
import com.worktime.mapper.WorkRuleMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class SettingService {

    private final UserSettingMapper settingMapper;
    private final WorkRuleMapper workRuleMapper;

    // ===== 用户设置 =====
    public UserSetting getSetting(Long userId) {
        LambdaQueryWrapper<UserSetting> q = new LambdaQueryWrapper<>();
        q.eq(UserSetting::getUserId, userId);
        UserSetting setting = settingMapper.selectOne(q);
        if (setting == null) {
            setting = initDefaultSetting(userId);
        }
        return setting;
    }

    public UserSetting updateSetting(Long userId, Boolean showWorktime, String timeFormat,
                                      String homeCalendarView, String defaultInputMode) {
        LambdaQueryWrapper<UserSetting> q = new LambdaQueryWrapper<>();
        q.eq(UserSetting::getUserId, userId);
        UserSetting setting = settingMapper.selectOne(q);
        if (setting == null) setting = initDefaultSetting(userId);

        if (showWorktime != null) setting.setShowWorktimeAfterClockout(showWorktime ? 1 : 0);
        if (timeFormat != null) {
            if (!"24H".equals(timeFormat) && !"12H".equals(timeFormat))
                throw new BusinessException(ErrorCode.INVALID_TIME_FORMAT);
            setting.setTimeFormat(timeFormat);
        }
        if (homeCalendarView != null) {
            if (!List.of("MONTH", "WEEK", "TODAY").contains(homeCalendarView))
                throw new BusinessException(ErrorCode.INVALID_CALENDAR_VIEW);
            setting.setHomeCalendarView(homeCalendarView);
        }
        if (defaultInputMode != null) {
            if (!List.of("BUTTON", "MANUAL").contains(defaultInputMode))
                throw new BusinessException(ErrorCode.INVALID_INPUT_MODE);
            setting.setDefaultInputMode(defaultInputMode);
        }

        settingMapper.updateById(setting);
        return setting;
    }

    // ===== 工作规则 =====
    public WorkRule getWorkRule(Long userId) {
        return workRuleMapper.selectDefaultByUserId(userId);
    }

    public WorkRule updateWorkRule(Long userId, String startTime, String endTime, String restStart,
                                    String restEnd, Integer bufferMinutes, String dayCutTime, String workDays) {
        WorkRule rule = workRuleMapper.selectDefaultByUserId(userId);
        if (rule == null) throw new BusinessException(ErrorCode.NOT_FOUND, "未找到默认工作规则");

        if (startTime != null) rule.setStartTime(LocalTime.parse(startTime));
        if (endTime != null) rule.setEndTime(LocalTime.parse(endTime));
        if (restStart != null) rule.setRestStart(LocalTime.parse(restStart));
        if (restEnd != null) rule.setRestEnd(LocalTime.parse(restEnd));
        if (bufferMinutes != null) rule.setBufferMinutes(bufferMinutes);
        if (dayCutTime != null) rule.setDayCutTime(LocalTime.parse(dayCutTime));
        if (workDays != null) rule.setWorkDays(workDays);

        workRuleMapper.updateById(rule);
        return rule;
    }

    private UserSetting initDefaultSetting(Long userId) {
        UserSetting s = new UserSetting();
        s.setUserId(userId);
        s.setShowWorktimeAfterClockout(1);
        s.setTimeFormat("24H");
        s.setHomeCalendarView("TODAY");
        s.setDefaultInputMode("BUTTON");
        settingMapper.insert(s);
        return s;
    }
}
