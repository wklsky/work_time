package com.worktime.engine;

import com.worktime.entity.WorkRule;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * 加班计算器
 */
@Component
public class OvertimeCalculator {

    /**
     * 工作日加班计算
     * 公式: 加班 = 总时长 - 休息 - 标准工时 - 缓冲; 最小为 0
     */
    public WorkTimeResult calculateWorkday(LocalDateTime clockIn, LocalDateTime clockOut, WorkRule rule) {
        long totalMinutes = Duration.between(clockIn, clockOut).toMinutes();

        // 扣除午休（仅当工作时段跨越午休时扣除）
        long restMinutes = calculateRestOverlap(clockIn, clockOut, rule);

        long workMinutes = totalMinutes - restMinutes;

        // 标准工时 = 下班时间 - 上班时间（分钟）
        long standardMinutes = Duration.between(rule.getStartTime(), rule.getEndTime()).toMinutes();

        // 加班 = 工作 - 标准 - 缓冲
        long overtimeMinutes = Math.max(0, workMinutes - standardMinutes - rule.getBufferMinutes());

        // 加班不超过总工作分钟数
        overtimeMinutes = Math.min(overtimeMinutes, workMinutes);

        return new WorkTimeResult((int) workMinutes, (int) overtimeMinutes,
                (int) restMinutes, (int) standardMinutes, rule.getBufferMinutes());
    }

    /**
     * 周末加班计算
     * 公式: 加班 = 总时长 - 休息; 全部算加班
     */
    public WorkTimeResult calculateWeekend(LocalDateTime clockIn, LocalDateTime clockOut, WorkRule rule) {
        long totalMinutes = Duration.between(clockIn, clockOut).toMinutes();
        long restMinutes = calculateRestOverlap(clockIn, clockOut, rule);
        long workMinutes = totalMinutes - restMinutes;

        // 周末全部算加班
        return new WorkTimeResult((int) workMinutes, (int) workMinutes, (int) restMinutes, 0, 0);
    }

    /**
     * 计算午休时段实际重叠分钟数（支持部分重叠）
     */
    private long calculateRestOverlap(LocalDateTime clockIn, LocalDateTime clockOut, WorkRule rule) {
        if (rule.getRestStart() == null || rule.getRestEnd() == null) return 0;

        LocalTime restStart = rule.getRestStart();
        LocalTime restEnd = rule.getRestEnd();
        LocalTime inTime = clockIn.toLocalTime();
        LocalTime outTime = clockOut.toLocalTime();

        // 没有重叠
        if (outTime.isBefore(restStart) || inTime.isAfter(restEnd)) {
            return 0;
        }

        // 计算实际重叠
        LocalTime overlapStart = inTime.isAfter(restStart) ? inTime : restStart;
        LocalTime overlapEnd = outTime.isBefore(restEnd) ? outTime : restEnd;

        return Duration.between(overlapStart, overlapEnd).toMinutes();
    }

    /**
     * 工时计算结果
     */
    public record WorkTimeResult(
            int workMinutes,
            int overtimeMinutes,
            int restMinutes,
            int standardMinutes,
            int bufferMinutes
    ) {
        public String toFormula() {
            StringBuilder sb = new StringBuilder();
            sb.append("下班 - 上班");
            if (restMinutes > 0) sb.append(" - 午休(").append(restMinutes).append("分)");
            if (standardMinutes > 0) sb.append(" - 标准工时(").append(standardMinutes).append("分)");
            if (bufferMinutes > 0) sb.append(" - 缓冲(").append(bufferMinutes).append("分)");
            sb.append(" = 加班(").append(overtimeMinutes).append("分)");
            return sb.toString();
        }
    }
}
