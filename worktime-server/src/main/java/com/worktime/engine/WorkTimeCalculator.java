package com.worktime.engine;

import com.worktime.entity.WorkRule;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;

/**
 * 工时计算引擎 - 主入口
 *
 * 支持三种场景:
 * 1. 工作日: 总工时 - 休息 - 标准 - 缓冲 = 加班
 * 2. 周末: 总工时 - 休息 = 加班 (全部算加班)
 * 3. 跨天: 自动检测并调整日期归属
 */
@Component
@RequiredArgsConstructor
public class WorkTimeCalculator {

    private final WorkdayClassifier workdayClassifier;
    private final OvertimeCalculator overtimeCalculator;
    private final DateAttributionResolver dateAttributionResolver;

    /**
     * 计算工时
     *
     * @param clockIn  上班时间 (UTC)
     * @param clockOut 下班时间 (UTC)
     * @param rule     用户的工作规则
     * @return 计算结果
     */
    public CalculationResult calculate(LocalDateTime clockIn, LocalDateTime clockOut, WorkRule rule) {
        // 1. 判定业务归属日期
        LocalDate workDate = dateAttributionResolver.resolveWorkDate(clockIn, rule.getDayCutTime());

        // 2. 检测跨天
        boolean crossDay = dateAttributionResolver.isCrossDay(clockIn, clockOut);

        // 3. 判断工作日/周末
        boolean isWorkday = workdayClassifier.isWorkday(workDate, rule.getWorkDays());

        // 4. 计算工时
        OvertimeCalculator.WorkTimeResult result;
        if (isWorkday) {
            result = overtimeCalculator.calculateWorkday(clockIn, clockOut, rule);
        } else {
            result = overtimeCalculator.calculateWeekend(clockIn, clockOut, rule);
        }

        return new CalculationResult(
                workDate,
                crossDay,
                result.workMinutes(),
                result.overtimeMinutes(),
                result.restMinutes(),
                result.standardMinutes(),
                result.bufferMinutes(),
                result.toFormula()
        );
    }

    /**
     * 综合计算结果
     */
    public record CalculationResult(
            LocalDate workDate,
            boolean crossDay,
            int workMinutes,
            int overtimeMinutes,
            int restMinutes,
            int standardMinutes,
            int bufferMinutes,
            String formula
    ) {}
}
