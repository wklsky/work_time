package com.worktime.engine;

import org.springframework.stereotype.Component;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 工作日 vs 周末判定
 */
@Component
public class WorkdayClassifier {

    /**
     * 判断是否为工作日
     * @param date 日期
     * @param workDays 工作日配置，如 "1,2,3,4,5"
     */
    public boolean isWorkday(LocalDate date, String workDays) {
        Set<Integer> workDaySet = Arrays.stream(workDays.split(","))
                .map(Integer::parseInt)
                .collect(Collectors.toSet());
        // DayOfWeek: MONDAY=1 ... SUNDAY=7
        return workDaySet.contains(date.getDayOfWeek().getValue());
    }

    /**
     * 判断是否为周末
     */
    public boolean isWeekend(LocalDate date, String workDays) {
        return !isWorkday(date, workDays);
    }
}
