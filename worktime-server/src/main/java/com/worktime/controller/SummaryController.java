package com.worktime.controller;

import com.worktime.common.Result;
import com.worktime.security.SecurityUtils;
import com.worktime.service.SummaryService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/summary")
@RequiredArgsConstructor
public class SummaryController {

    private final SummaryService summaryService;

    @GetMapping("/day")
    public Result<Map<String, Object>> getDay(@RequestParam String date) {
        return Result.success(summaryService.getDaySummary(SecurityUtils.getCurrentUserId(), date));
    }

    @GetMapping("/week")
    public Result<Map<String, Object>> getWeek(@RequestParam String date) {
        return Result.success(summaryService.getWeekSummary(SecurityUtils.getCurrentUserId(), date));
    }

    @GetMapping("/month")
    public Result<Map<String, Object>> getMonth(@RequestParam int year, @RequestParam int month) {
        return Result.success(summaryService.getMonthSummary(SecurityUtils.getCurrentUserId(), year, month));
    }

    @GetMapping("/year")
    public Result<Map<String, Object>> getYear(@RequestParam int year) {
        return Result.success(summaryService.getYearSummary(SecurityUtils.getCurrentUserId(), year));
    }

    @GetMapping("/custom")
    public Result<Map<String, Object>> getCustom(@RequestParam String startDate,
                                                  @RequestParam String endDate) {
        return Result.success(summaryService.getCustomSummary(SecurityUtils.getCurrentUserId(), startDate, endDate));
    }

    @GetMapping("/quick")
    public Result<Map<String, Object>> getQuick(@RequestParam String preset) {
        // 快捷区间映射
        java.time.LocalDate today = java.time.LocalDate.now();
        String start, end;
        switch (preset) {
            case "LAST_7_DAYS":
                end = today.toString();
                start = today.minusDays(6).toString();
                break;
            case "LAST_30_DAYS":
                end = today.toString();
                start = today.minusDays(29).toString();
                break;
            case "LAST_90_DAYS":
                end = today.toString();
                start = today.minusDays(89).toString();
                break;
            case "THIS_QUARTER":
                int q = (today.getMonthValue() - 1) / 3;
                start = java.time.LocalDate.of(today.getYear(), q * 3 + 1, 1).toString();
                end = java.time.LocalDate.of(today.getYear(), q * 3 + 3, 1)
                        .withDayOfMonth(java.time.LocalDate.of(today.getYear(), q * 3 + 3, 1).lengthOfMonth()).toString();
                break;
            default:
                end = today.toString();
                start = today.minusDays(6).toString();
        }
        return Result.success(summaryService.getCustomSummary(SecurityUtils.getCurrentUserId(), start, end));
    }
}
