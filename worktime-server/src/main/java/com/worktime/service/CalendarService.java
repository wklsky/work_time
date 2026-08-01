package com.worktime.service;

import com.worktime.entity.AttendanceRecord;
import com.worktime.mapper.AttendanceRecordMapper;
import com.worktime.mapper.StatisticsDailyMapper;
import com.worktime.entity.StatisticsDaily;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.WeekFields;
import java.util.*;

@Service
@RequiredArgsConstructor
public class CalendarService {

    private final AttendanceRecordMapper recordMapper;
    private final StatisticsDailyMapper statisticsMapper;

    public Map<String, Object> getCalendar(Long userId, String view, String dateStr) {
        return switch (view) {
            case "MONTH" -> buildMonthView(userId, dateStr);
            case "WEEK" -> buildWeekView(userId, dateStr);
            case "TODAY" -> buildTodayView(userId, dateStr);
            default -> buildMonthView(userId, dateStr);
        };
    }

    private Map<String, Object> buildMonthView(Long userId, String dateStr) {
        LocalDate anchor = LocalDate.parse(dateStr);
        int year = anchor.getYear();
        int month = anchor.getMonthValue();

        LocalDate start = anchor.withDayOfMonth(1);
        LocalDate end = anchor.withDayOfMonth(anchor.lengthOfMonth());

        List<StatisticsDaily> stats = statisticsMapper.selectByUserAndDateRange(userId, start, end);
        Map<LocalDate, StatisticsDaily> statMap = new HashMap<>();
        for (StatisticsDaily s : stats) statMap.put(s.getStatDate(), s);

        List<Map<String, Object>> days = new ArrayList<>();
        for (LocalDate d = start; !d.isAfter(end); d = d.plusDays(1)) {
            StatisticsDaily s = statMap.get(d);
            Map<String, Object> day = new HashMap<>();
            day.put("date", d.toString());
            day.put("weekday", d.getDayOfWeek().getValue());
            day.put("hasRecord", s != null);
            day.put("workMinutes", s != null ? s.getWorkMinutes() : 0);
            day.put("overtimeMinutes", s != null ? s.getOvertimeMinutes() : 0);
            day.put("overtimeLevel", getLevel(s != null ? s.getOvertimeMinutes() : 0));
            day.put("isToday", d.equals(LocalDate.now()));
            day.put("recordCount", s != null ? s.getAttendanceCount() : 0);
            days.add(day);
        }

        Map<String, Object> result = new HashMap<>();
        result.put("view", "MONTH");
        result.put("year", year);
        result.put("month", month);
        result.put("dayCount", anchor.lengthOfMonth());
        result.put("firstDayOfWeek", start.getDayOfWeek() == DayOfWeek.SUNDAY ? 7 : start.getDayOfWeek().getValue());
        result.put("days", days);
        return result;
    }

    private Map<String, Object> buildWeekView(Long userId, String dateStr) {
        LocalDate anchor = LocalDate.parse(dateStr);
        WeekFields wf = WeekFields.of(DayOfWeek.MONDAY, 7);
        LocalDate weekStart = anchor.with(wf.dayOfWeek(), 1);
        LocalDate weekEnd = weekStart.plusDays(6);

        List<AttendanceRecord> records = recordMapper.selectByUserAndDateRange(userId, weekStart, weekEnd);
        Map<LocalDate, List<AttendanceRecord>> grouped = new LinkedHashMap<>();
        for (LocalDate d = weekStart; !d.isAfter(weekEnd); d = d.plusDays(1)) grouped.put(d, new ArrayList<>());
        for (AttendanceRecord r : records) {
            if (grouped.containsKey(r.getWorkDate())) {
                grouped.get(r.getWorkDate()).add(r);
            }
        }

        List<Map<String, Object>> days = new ArrayList<>();
        int totalWork = 0, totalOt = 0, attendanceDays = 0;
        String[] names = {"周一","周二","周三","周四","周五","周六","周日"};

        int i = 0;
        for (Map.Entry<LocalDate, List<AttendanceRecord>> entry : grouped.entrySet()) {
            LocalDate d = entry.getKey();
            List<AttendanceRecord> recs = entry.getValue();
            int wm = recs.stream().mapToInt(r -> r.getWorkMinutes() != null ? r.getWorkMinutes() : 0).sum();
            int om = recs.stream().mapToInt(r -> r.getOvertimeMinutes() != null ? r.getOvertimeMinutes() : 0).sum();

            Map<String, Object> day = new HashMap<>();
            day.put("date", d.toString());
            day.put("weekday", d.getDayOfWeek().getValue());
            day.put("weekdayName", names[i++]);
            day.put("hasRecord", !recs.isEmpty());
            day.put("workMinutes", wm);
            day.put("overtimeMinutes", om);
            day.put("firstClockIn", recs.isEmpty() ? null : recs.get(0).getClockIn());
            day.put("lastClockOut", recs.isEmpty() ? null : recs.get(recs.size()-1).getClockOut());
            day.put("isToday", d.equals(LocalDate.now()));
            day.put("workPercentage", wm > 0 ? Math.min(100, wm * 100 / 480) : 0);
            day.put("records", recs);
            days.add(day);

            totalWork += wm; totalOt += om;
            if (!recs.isEmpty()) attendanceDays++;
        }

        Map<String, Object> result = new HashMap<>();
        result.put("view", "WEEK");
        result.put("weekStart", weekStart.toString());
        result.put("weekEnd", weekEnd.toString());
        result.put("days", days);
        Map<String, Object> wt = new HashMap<>();
        wt.put("workMinutes", totalWork);
        wt.put("overtimeMinutes", totalOt);
        wt.put("attendanceDays", attendanceDays);
        wt.put("avgDailyMinutes", attendanceDays > 0 ? totalWork / attendanceDays : 0);
        result.put("weekTotal", wt);
        return result;
    }

    private Map<String, Object> buildTodayView(Long userId, String dateStr) {
        LocalDate date = LocalDate.parse(dateStr);
        List<AttendanceRecord> recs = recordMapper.selectByUserAndDate(userId, date);
        boolean working = recs.stream().anyMatch(r -> "PENDING".equals(r.getStatus()));
        int workMins = recs.stream().filter(r -> "COMPLETED".equals(r.getStatus()))
                .mapToInt(r -> r.getWorkMinutes() != null ? r.getWorkMinutes() : 0).sum();
        int otMins = recs.stream().filter(r -> "COMPLETED".equals(r.getStatus()))
                .mapToInt(r -> r.getOvertimeMinutes() != null ? r.getOvertimeMinutes() : 0).sum();

        Map<String, Object> result = new HashMap<>();
        result.put("view", "TODAY");
        result.put("date", dateStr);
        result.put("weekday", getChineseWeekday(date.getDayOfWeek()));
        Map<String, Object> timeline = new LinkedHashMap<>();
        timeline.put("standardStart", "09:00");
        timeline.put("standardEnd", "18:00");
        timeline.put("restStart", "12:00");
        timeline.put("restEnd", "13:00");
        result.put("timeline", timeline);
        result.put("records", recs);
        result.put("currentStatus", working ? "WORKING" : (recs.isEmpty() ? "NOT_STARTED" : "COMPLETED"));
        Map<String, Object> acc = new HashMap<>();
        acc.put("totalWorkMinutes", workMins);
        acc.put("totalOvertimeMinutes", otMins);
        acc.put("segmentCount", recs.size());
        result.put("todayAccumulated", acc);
        return result;
    }

    private String getLevel(int overtimeMinutes) {
        if (overtimeMinutes <= 0) return null;
        if (overtimeMinutes <= 60) return "LOW";
        if (overtimeMinutes <= 180) return "MEDIUM";
        return "HIGH";
    }

    private String getChineseWeekday(DayOfWeek dow) {
        return switch (dow) {
            case MONDAY -> "星期一"; case TUESDAY -> "星期二";
            case WEDNESDAY -> "星期三"; case THURSDAY -> "星期四";
            case FRIDAY -> "星期五"; case SATURDAY -> "星期六";
            case SUNDAY -> "星期日";
        };
    }
}
