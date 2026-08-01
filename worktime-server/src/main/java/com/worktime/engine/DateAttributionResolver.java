package com.worktime.engine;

import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * 日切时间归属判定
 * 根据用户的日切时间，将打卡记录归属到正确的业务日期
 */
@Component
public class DateAttributionResolver {

    /**
     * 根据日切时间计算业务归属日期
     * 例：日切时间 04:00，凌晨 3 点打卡归属前一天
     *
     * @param clockIn 上班时间
     * @param dayCutTime 日切时间
     * @return 业务归属日期
     */
    public LocalDate resolveWorkDate(LocalDateTime clockIn, LocalTime dayCutTime) {
        LocalTime clockInTime = clockIn.toLocalTime();
        if (clockInTime.isBefore(dayCutTime)) {
            // 日切时间前 → 归属前一天
            return clockIn.toLocalDate().minusDays(1);
        }
        return clockIn.toLocalDate();
    }

    /**
     * 判断结束时间是否跨天
     */
    public boolean isCrossDay(LocalDateTime clockIn, LocalDateTime clockOut) {
        if (clockOut == null) return false;
        // 如果结束时间小于开始时间（同一时间轴），则跨天
        // 注意：我们比较日期部分，因为用户可能选择了跨天
        return clockOut.toLocalDate().isAfter(clockIn.toLocalDate())
                || clockOut.toLocalTime().isBefore(clockIn.toLocalTime());
    }
}
