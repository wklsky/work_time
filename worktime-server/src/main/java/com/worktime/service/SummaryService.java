package com.worktime.service;

import com.worktime.common.BusinessException;
import com.worktime.common.ErrorCode;
import com.worktime.entity.AttendanceRecord;
import com.worktime.entity.StatisticsDaily;
import com.worktime.mapper.AttendanceRecordMapper;
import com.worktime.mapper.StatisticsDailyMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.WeekFields;
import java.util.*;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
public class SummaryService {

    private final StatisticsDailyMapper statisticsMapper;
    private final AttendanceRecordMapper recordMapper;
    private final RedisTemplate<String, Object> redisTemplate;

    private static final String CACHE_PREFIX = "summary:";

    // ===== 日汇总 =====
    public Map<String, Object> getDaySummary(Long userId, String dateStr) {
        LocalDate date = LocalDate.parse(dateStr);
        StatisticsDaily stat = statisticsMapper.selectByUserAndDate(userId, date);
        List<AttendanceRecord> records = recordMapper.selectByUserAndDate(userId, date);

        List<Map<String, Object>> recordList = new ArrayList<>();
        for (AttendanceRecord r : records) {
            Map<String, Object> rmap = new LinkedHashMap<>();
            rmap.put("id", r.getId());
            rmap.put("clockIn", r.getClockIn());
            rmap.put("clockOut", r.getClockOut());
            rmap.put("workMinutes", r.getWorkMinutes());
            rmap.put("overtimeMinutes", r.getOvertimeMinutes());
            rmap.put("source", r.getSource());
            rmap.put("crossDay", r.getCrossDay());
            rmap.put("remark", r.getRemark());
            recordList.add(rmap);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("dimension", "DAY");
        result.put("date", dateStr);
        result.put("workMinutes", stat != null ? stat.getWorkMinutes() : 0);
        result.put("overtimeMinutes", stat != null ? stat.getOvertimeMinutes() : 0);
        result.put("attendanceCount", stat != null ? stat.getAttendanceCount() : 0);
        result.put("records", recordList);
        return result;
    }

    // ===== 周汇总 =====
    public Map<String, Object> getWeekSummary(Long userId, String dateStr) {
        LocalDate anchor = LocalDate.parse(dateStr);
        WeekFields wf = WeekFields.of(DayOfWeek.MONDAY, 7);
        LocalDate weekStart = anchor.with(wf.dayOfWeek(), 1);
        LocalDate weekEnd = weekStart.plusDays(6);

        String cacheKey = CACHE_PREFIX + "week:" + userId + ":" + weekStart;
        @SuppressWarnings("unchecked")
        Map<String, Object> cached = (Map<String, Object>) redisTemplate.opsForValue().get(cacheKey);
        if (cached != null) return cached;

        List<StatisticsDaily> stats = statisticsMapper.selectByUserAndDateRange(userId, weekStart, weekEnd);

        int totalWork = 0, totalOt = 0, attDays = 0;
        List<Map<String, Object>> breakdown = new ArrayList<>();
        String[] names = {"周一","周二","周三","周四","周五","周六","周日"};

        for (int i = 0; i < 7; i++) {
            LocalDate d = weekStart.plusDays(i);
            StatisticsDaily s = find(stats, d);
            int wm = s != null ? s.getWorkMinutes() : 0;
            int om = s != null ? s.getOvertimeMinutes() : 0;
            totalWork += wm; totalOt += om;
            if (s != null) attDays++;

            Map<String, Object> day = new HashMap<>();
            day.put("date", d.toString());
            day.put("weekday", i + 1);
            day.put("weekdayName", names[i]);
            day.put("workMinutes", wm);
            day.put("overtimeMinutes", om);
            breakdown.add(day);
        }

        Map<String, Object> result = new HashMap<>();
        result.put("dimension", "WEEK");
        result.put("weekStart", weekStart.toString());
        result.put("weekEnd", weekEnd.toString());
        result.put("workMinutes", totalWork);
        result.put("overtimeMinutes", totalOt);
        result.put("attendanceDays", attDays);
        result.put("avgDailyWorkMinutes", attDays > 0 ? totalWork / attDays : 0);
        result.put("dailyBreakdown", breakdown);

        redisTemplate.opsForValue().set(cacheKey, result, 5, TimeUnit.MINUTES);
        return result;
    }

    // ===== 月汇总 =====
    public Map<String, Object> getMonthSummary(Long userId, int year, int month) {
        String cacheKey = CACHE_PREFIX + "month:" + userId + ":" + year + "-" + month;
        @SuppressWarnings("unchecked")
        Map<String, Object> cached = (Map<String, Object>) redisTemplate.opsForValue().get(cacheKey);
        if (cached != null) return cached;

        LocalDate start = LocalDate.of(year, month, 1);
        LocalDate end = start.withDayOfMonth(start.lengthOfMonth());
        List<StatisticsDaily> stats = statisticsMapper.selectByUserAndDateRange(userId, start, end);

        int totalWork = 0, totalOt = 0, attDays = 0;
        Map<String, Object> maxOtDay = null;
        List<Map<String, Object>> breakdown = new ArrayList<>();
        List<Integer> trend = new ArrayList<>();

        for (LocalDate d = start; !d.isAfter(end); d = d.plusDays(1)) {
            StatisticsDaily s = find(stats, d);
            int wm = s != null ? s.getWorkMinutes() : 0;
            int om = s != null ? s.getOvertimeMinutes() : 0;
            totalWork += wm; totalOt += om;
            if (s != null) attDays++;

            if (s != null && (maxOtDay == null || om > (int) maxOtDay.getOrDefault("overtimeMinutes", 0))) {
                Map<String, Object> mod = new HashMap<>();
                mod.put("date", d.toString());
                mod.put("overtimeMinutes", om);
                maxOtDay = mod;
            }

            trend.add(wm);
        }

        Map<String, Object> result = new HashMap<>();
        result.put("dimension", "MONTH");
        result.put("year", year);
        result.put("month", month);
        result.put("workMinutes", totalWork);
        result.put("overtimeMinutes", totalOt);
        result.put("attendanceDays", attDays);
        result.put("avgDailyWorkMinutes", attDays > 0 ? totalWork / attDays : 0);
        result.put("maxOvertimeDay", maxOtDay);
        result.put("dailyBreakdown", breakdown);
        Map<String, Object> trendData = new HashMap<>();
        trendData.put("standardLine", 480);
        trendData.put("values", trend);
        result.put("trend", trendData);

        redisTemplate.opsForValue().set(cacheKey, result, 5, TimeUnit.MINUTES);
        return result;
    }

    // ===== 年汇总 =====
    public Map<String, Object> getYearSummary(Long userId, int year) {
        String cacheKey = CACHE_PREFIX + "year:" + userId + ":" + year;
        @SuppressWarnings("unchecked")
        Map<String, Object> cached = (Map<String, Object>) redisTemplate.opsForValue().get(cacheKey);
        if (cached != null) return cached;

        List<Map<String, Object>> monthlyBreakdown = new ArrayList<>();
        List<Integer> trend = new ArrayList<>();
        int totalWork = 0, totalOt = 0, attDays = 0;
        int maxOtMonth = 0, maxOt = 0;

        for (int m = 1; m <= 12; m++) {
            LocalDate start = LocalDate.of(year, m, 1);
            LocalDate end = start.withDayOfMonth(start.lengthOfMonth());
            List<StatisticsDaily> stats = statisticsMapper.selectByUserAndDateRange(userId, start, end);
            int wm = stats.stream().mapToInt(s -> s.getWorkMinutes() != null ? s.getWorkMinutes() : 0).sum();
            int om = stats.stream().mapToInt(s -> s.getOvertimeMinutes() != null ? s.getOvertimeMinutes() : 0).sum();
            int ad = (int) stats.stream().filter(s -> s.getAttendanceCount() > 0).count();
            totalWork += wm; totalOt += om; attDays += ad;
            if (om > maxOt) { maxOt = om; maxOtMonth = m; }

            Map<String, Object> mb = new HashMap<>();
            mb.put("month", m); mb.put("workMinutes", wm);
            mb.put("overtimeMinutes", om); mb.put("attendanceDays", ad);
            monthlyBreakdown.add(mb);
            trend.add(wm);
        }

        Map<String, Object> result = new HashMap<>();
        result.put("dimension", "YEAR");
        result.put("year", year);
        result.put("workMinutes", totalWork);
        result.put("overtimeMinutes", totalOt);
        result.put("attendanceDays", attDays);
        result.put("avgDailyWorkMinutes", attDays > 0 ? totalWork / attDays : 0);
        Map<String, Object> maxOm = new HashMap<>();
        maxOm.put("month", maxOtMonth); maxOm.put("overtimeMinutes", maxOt);
        result.put("maxOvertimeMonth", maxOm);
        result.put("monthlyBreakdown", monthlyBreakdown);
        Map<String, Object> trendData = new HashMap<>();
        trendData.put("values", trend);
        result.put("trend", trendData);

        redisTemplate.opsForValue().set(cacheKey, result, 10, TimeUnit.MINUTES);
        return result;
    }

    // ===== 自定义区间汇总 =====
    public Map<String, Object> getCustomSummary(Long userId, String startStr, String endStr) {
        LocalDate start = LocalDate.parse(startStr);
        LocalDate end = LocalDate.parse(endStr);

        long days = end.toEpochDay() - start.toEpochDay() + 1;
        if (days > 366) throw new BusinessException(ErrorCode.DATE_RANGE_TOO_LARGE);
        if (end.isBefore(start)) throw new BusinessException(ErrorCode.END_BEFORE_START);

        List<StatisticsDaily> stats = statisticsMapper.selectByUserAndDateRange(userId, start, end);

        int totalWork = 0, totalOt = 0, attDays = 0;
        int maxOt = 0;
        String maxOtDate = "";

        for (StatisticsDaily s : stats) {
            int wm = s.getWorkMinutes() != null ? s.getWorkMinutes() : 0;
            int om = s.getOvertimeMinutes() != null ? s.getOvertimeMinutes() : 0;
            totalWork += wm; totalOt += om;
            if (s.getAttendanceCount() > 0) attDays++;
            if (om > maxOt) { maxOt = om; maxOtDate = s.getStatDate().toString(); }
        }

        Map<String, Object> result = new HashMap<>();
        result.put("dimension", "CUSTOM");
        result.put("startDate", startStr);
        result.put("endDate", endStr);
        result.put("totalDays", (int) days);
        result.put("workMinutes", totalWork);
        result.put("overtimeMinutes", totalOt);
        result.put("attendanceDays", attDays);
        result.put("avgDailyWorkMinutes", attDays > 0 ? totalWork / attDays : 0);
        Map<String, Object> mod = new HashMap<>();
        mod.put("date", maxOtDate); mod.put("overtimeMinutes", maxOt);
        result.put("maxOvertimeDay", mod);
        result.put("dailyBreakdown", Collections.emptyList());
        return result;
    }

    private StatisticsDaily find(List<StatisticsDaily> stats, LocalDate date) {
        return stats.stream().filter(s -> s.getStatDate().equals(date)).findFirst().orElse(null);
    }
}
