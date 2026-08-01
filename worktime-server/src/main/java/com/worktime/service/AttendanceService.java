package com.worktime.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.worktime.common.BusinessException;
import com.worktime.common.ErrorCode;
import com.worktime.engine.WorkTimeCalculator;
import com.worktime.entity.*;
import com.worktime.mapper.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
@RequiredArgsConstructor
public class AttendanceService {

    private final AttendanceRecordMapper recordMapper;
    private final WorkRuleMapper workRuleMapper;
    private final StatisticsDailyMapper statisticsMapper;
    private final UserSettingMapper settingMapper;
    private final WorkTimeCalculator calculator;

    /**
     * 开始工作
     */
    @Transactional
    public AttendanceRecord clockIn(Long userId, String remark) {
        // 检查是否已有进行中的记录
        AttendanceRecord pending = recordMapper.selectPendingByUserId(userId);
        if (pending != null) {
            throw new BusinessException(ErrorCode.PENDING_EXISTS);
        }

        // 获取默认规则
        WorkRule rule = workRuleMapper.selectDefaultByUserId(userId);
        if (rule == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "未找到默认工作规则");
        }

        AttendanceRecord record = new AttendanceRecord();
        record.setUserId(userId);
        record.setClockIn(LocalDateTime.now());
        record.setSource("BUTTON");
        record.setStatus("PENDING");
        record.setRuleId(rule.getId());
        record.setRemark(remark);
        record.setWorkMinutes(0);
        record.setOvertimeMinutes(0);
        record.setCrossDay(0);
        record.setWorkDate(LocalDate.now()); // 临时，clock-out 时重新计算

        recordMapper.insert(record);
        return record;
    }

    /**
     * 结束工作
     */
    @Transactional
    public Map<String, Object> clockOut(Long userId, Long recordId, String remark) {
        AttendanceRecord record;
        if (recordId != null) {
            record = recordMapper.selectById(recordId);
            if (record == null || !record.getUserId().equals(userId)) {
                throw new BusinessException(ErrorCode.RECORD_NOT_FOUND);
            }
        } else {
            record = recordMapper.selectPendingByUserId(userId);
        }

        if (record == null || !"PENDING".equals(record.getStatus())) {
            throw new BusinessException(ErrorCode.NO_PENDING_RECORD);
        }

        // 获取规则
        WorkRule rule = workRuleMapper.selectDefaultByUserId(userId);

        // 写入结束时间
        LocalDateTime clockOut = LocalDateTime.now();
        record.setClockOut(clockOut);

        // 计算工时
        if (rule != null) {
            WorkTimeCalculator.CalculationResult result =
                    calculator.calculate(record.getClockIn(), clockOut, rule);
            record.setWorkMinutes(result.workMinutes());
            record.setOvertimeMinutes(result.overtimeMinutes());
            record.setCrossDay(result.crossDay() ? 1 : 0);
            record.setWorkDate(result.workDate());
        }

        record.setStatus("COMPLETED");
        if (remark != null) record.setRemark(remark);
        recordMapper.updateById(record);

        // 异步更新统计表
        recalcStatistics(userId, record.getWorkDate());

        // 构建响应
        return buildClockOutResponse(record, rule);
    }

    /**
     * 手动录入
     */
    @Transactional
    public Object manualRecord(Long userId, String clockInStr, String clockOutStr, String remark) {
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'");
        LocalDateTime clockIn = LocalDateTime.parse(clockInStr, fmt);
        LocalDateTime clockOut = clockOutStr != null ? LocalDateTime.parse(clockOutStr, fmt) : null;

        WorkRule rule = workRuleMapper.selectDefaultByUserId(userId);

        AttendanceRecord record = new AttendanceRecord();
        record.setUserId(userId);
        record.setClockIn(clockIn);
        record.setClockOut(clockOut);
        record.setSource("MANUAL");
        record.setRemark(remark);
        record.setRuleId(rule != null ? rule.getId() : null);

        if (clockOut != null) {
            // 计算工时
            if (rule != null) {
                WorkTimeCalculator.CalculationResult result =
                        calculator.calculate(clockIn, clockOut, rule);
                record.setWorkMinutes(result.workMinutes());
                record.setOvertimeMinutes(result.overtimeMinutes());
                record.setCrossDay(result.crossDay() ? 1 : 0);
                record.setWorkDate(result.workDate());
            }
            record.setStatus("COMPLETED");
        } else {
            record.setWorkMinutes(0);
            record.setOvertimeMinutes(0);
            record.setCrossDay(0);
            record.setWorkDate(LocalDate.now());
            record.setStatus("PENDING");
        }

        recordMapper.insert(record);

        if (clockOut != null) {
            recalcStatistics(userId, record.getWorkDate());
            return buildClockOutResponse(record, rule);
        }
        return record;
    }

    /**
     * 修改打卡记录
     */
    @Transactional
    public Object updateRecord(Long userId, Long recordId, String clockInStr, String clockOutStr, String remark) {
        AttendanceRecord record = recordMapper.selectById(recordId);
        if (record == null || !record.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.RECORD_NOT_FOUND);
        }

        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'");
        if (clockInStr != null) record.setClockIn(LocalDateTime.parse(clockInStr, fmt));
        if (clockOutStr != null) record.setClockOut(LocalDateTime.parse(clockOutStr, fmt));
        if (remark != null) record.setRemark(remark);

        // 重算工时
        if (record.getClockOut() != null) {
            WorkRule rule = workRuleMapper.selectDefaultByUserId(userId);
            if (rule != null) {
                WorkTimeCalculator.CalculationResult result =
                        calculator.calculate(record.getClockIn(), record.getClockOut(), rule);
                record.setWorkMinutes(result.workMinutes());
                record.setOvertimeMinutes(result.overtimeMinutes());
                record.setCrossDay(result.crossDay() ? 1 : 0);
                record.setWorkDate(result.workDate());
            }
            record.setStatus("COMPLETED");
        }

        recordMapper.updateById(record);

        if (record.getClockOut() != null) {
            recalcStatistics(userId, record.getWorkDate());
            return buildClockOutResponse(record, workRuleMapper.selectDefaultByUserId(userId));
        }
        return record;
    }

    /**
     * 查询指定日期记录
     */
    public Map<String, Object> getRecords(Long userId, String dateStr) {
        LocalDate date = LocalDate.parse(dateStr);
        List<AttendanceRecord> records = recordMapper.selectByUserAndDate(userId, date);

        int totalWork = records.stream()
                .filter(r -> "COMPLETED".equals(r.getStatus()))
                .mapToInt(r -> r.getWorkMinutes() != null ? r.getWorkMinutes() : 0).sum();
        int totalOvertime = records.stream()
                .filter(r -> "COMPLETED".equals(r.getStatus()))
                .mapToInt(r -> r.getOvertimeMinutes() != null ? r.getOvertimeMinutes() : 0).sum();

        Map<String, Object> result = new HashMap<>();
        result.put("date", dateStr);
        result.put("records", records);
        Map<String, Object> dayTotal = new HashMap<>();
        dayTotal.put("workMinutes", totalWork);
        dayTotal.put("overtimeMinutes", totalOvertime);
        dayTotal.put("segmentCount", records.size());
        result.put("dayTotal", dayTotal);
        return result;
    }

    /**
     * 重算统计数据 (UPSERT 幂等)
     */
    @Transactional
    public void recalcStatistics(Long userId, LocalDate workDate) {
        List<AttendanceRecord> records = recordMapper.selectByUserAndDate(userId, workDate);
        List<AttendanceRecord> completed = records.stream()
                .filter(r -> "COMPLETED".equals(r.getStatus()))
                .toList();

        int workMinutes = completed.stream().mapToInt(r -> r.getWorkMinutes() != null ? r.getWorkMinutes() : 0).sum();
        int overtimeMinutes = completed.stream().mapToInt(r -> r.getOvertimeMinutes() != null ? r.getOvertimeMinutes() : 0).sum();
        LocalDateTime firstIn = completed.stream().map(AttendanceRecord::getClockIn).min(LocalDateTime::compareTo).orElse(null);
        LocalDateTime lastOut = completed.stream().map(AttendanceRecord::getClockOut).max(LocalDateTime::compareTo).orElse(null);

        StatisticsDaily stat = statisticsMapper.selectByUserAndDate(userId, workDate);
        if (stat == null) {
            stat = new StatisticsDaily();
            stat.setUserId(userId);
            stat.setStatDate(workDate);
            stat.setAttendanceCount(completed.size());
            stat.setWorkMinutes(workMinutes);
            stat.setOvertimeMinutes(overtimeMinutes);
            stat.setFirstClockIn(firstIn);
            stat.setLastClockOut(lastOut);
            statisticsMapper.insert(stat);
        } else {
            stat.setAttendanceCount(completed.size());
            stat.setWorkMinutes(workMinutes);
            stat.setOvertimeMinutes(overtimeMinutes);
            stat.setFirstClockIn(firstIn);
            stat.setLastClockOut(lastOut);
            statisticsMapper.updateById(stat);
        }
    }

    private Map<String, Object> buildClockOutResponse(AttendanceRecord record, WorkRule rule) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id", record.getId());
        data.put("clockIn", record.getClockIn());
        data.put("clockOut", record.getClockOut());
        data.put("workMinutes", record.getWorkMinutes());
        data.put("overtimeMinutes", record.getOvertimeMinutes());
        data.put("source", record.getSource());
        data.put("crossDay", record.getCrossDay());
        data.put("status", record.getStatus());
        data.put("updateTime", record.getUpdateTime());

        // breakdown
        Map<String, Object> breakdown = new HashMap<>();
        breakdown.put("regularWorkMinutes", record.getWorkMinutes() - record.getOvertimeMinutes());
        breakdown.put("restMinutes", rule != null ? getRestMinutes(rule) : 60);
        breakdown.put("overtimeMinutes", record.getOvertimeMinutes());
        breakdown.put("formula", buildFormula(record, rule));
        data.put("breakdown", breakdown);

        // today accumulated
        List<AttendanceRecord> todayRecs = recordMapper.selectByUserAndDate(
                record.getUserId(), record.getWorkDate());
        int totalWork = todayRecs.stream()
                .filter(r -> "COMPLETED".equals(r.getStatus()))
                .mapToInt(r -> r.getWorkMinutes() != null ? r.getWorkMinutes() : 0).sum();
        int totalOt = todayRecs.stream()
                .filter(r -> "COMPLETED".equals(r.getStatus()))
                .mapToInt(r -> r.getOvertimeMinutes() != null ? r.getOvertimeMinutes() : 0).sum();
        Map<String, Object> accumulated = new HashMap<>();
        accumulated.put("totalWorkMinutes", totalWork);
        accumulated.put("totalOvertimeMinutes", totalOt);
        accumulated.put("segmentCount", todayRecs.size());
        data.put("todayAccumulated", accumulated);

        return data;
    }

    private int getRestMinutes(WorkRule rule) {
        if (rule.getRestStart() == null || rule.getRestEnd() == null) return 0;
        return (int) java.time.Duration.between(rule.getRestStart(), rule.getRestEnd()).toMinutes();
    }

    private String buildFormula(AttendanceRecord record, WorkRule rule) {
        int rest = rule != null ? getRestMinutes(rule) : 60;
        int buf = rule != null ? rule.getBufferMinutes() : 30;
        return String.format("下班 - 上班 - 午休(%d分) - 标准工时(480分) - 缓冲(%d分) = 加班(%d分)",
                rest, buf, record.getOvertimeMinutes());
    }
}
