package com.worktime.controller;

import com.worktime.common.Result;
import com.worktime.dto.request.ClockOutRequest;
import com.worktime.dto.request.ManualRecordRequest;
import com.worktime.dto.request.UpdateRecordRequest;
import com.worktime.security.SecurityUtils;
import com.worktime.service.AttendanceService;
import com.worktime.service.CalendarService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/attendance")
@RequiredArgsConstructor
public class AttendanceController {

    private final AttendanceService attendanceService;
    private final CalendarService calendarService;

    @PostMapping("/clock-in")
    public Result<Object> clockIn(@RequestBody(required = false) Map<String, String> body) {
        String remark = body != null ? body.get("remark") : null;
        return Result.success(attendanceService.clockIn(SecurityUtils.getCurrentUserId(), remark));
    }

    @PostMapping("/clock-out")
    public Result<Map<String, Object>> clockOut(@RequestBody(required = false) ClockOutRequest request) {
        Long recordId = request != null ? request.getRecordId() : null;
        String remark = request != null ? request.getRemark() : null;
        return Result.success(attendanceService.clockOut(SecurityUtils.getCurrentUserId(), recordId, remark));
    }

    @PostMapping("/manual")
    public Result<Object> manualRecord(@Valid @RequestBody ManualRecordRequest request) {
        return Result.success(attendanceService.manualRecord(
                SecurityUtils.getCurrentUserId(),
                request.getClockIn(), request.getClockOut(), request.getRemark()));
    }

    @PutMapping("/{id}")
    public Result<Object> updateRecord(@PathVariable Long id, @RequestBody UpdateRecordRequest request) {
        return Result.success(attendanceService.updateRecord(
                SecurityUtils.getCurrentUserId(), id,
                request.getClockIn(), request.getClockOut(), request.getRemark()));
    }

    @GetMapping("/records")
    public Result<Map<String, Object>> getRecords(@RequestParam String date) {
        return Result.success(attendanceService.getRecords(SecurityUtils.getCurrentUserId(), date));
    }

    @GetMapping("/calendar")
    public Result<Map<String, Object>> getCalendar(@RequestParam(defaultValue = "TODAY") String view,
                                                    @RequestParam String date) {
        return Result.success(calendarService.getCalendar(SecurityUtils.getCurrentUserId(), view, date));
    }
}
