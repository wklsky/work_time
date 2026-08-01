package com.worktime.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.worktime.common.BusinessException;
import com.worktime.common.ErrorCode;
import com.worktime.common.Result;
import com.worktime.dto.request.CreateExportRequest;
import com.worktime.entity.ExportRecord;
import com.worktime.mapper.ExportRecordMapper;
import com.worktime.security.SecurityUtils;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.*;

@RestController
@RequestMapping("/api/export")
@RequiredArgsConstructor
public class ExportController {

    private final ExportRecordMapper exportMapper;

    @PostMapping
    public Result<Map<String, Object>> createExport(@Valid @RequestBody CreateExportRequest request) {
        Long userId = SecurityUtils.getCurrentUserId();

        ExportRecord record = new ExportRecord();
        record.setUserId(userId);
        record.setExportType(request.getExportType());
        record.setFileFormat(request.getFileFormat());
        record.setStatus("PROCESSING");
        if (request.getStartDate() != null) record.setStartDate(LocalDate.parse(request.getStartDate()));
        if (request.getEndDate() != null) record.setEndDate(LocalDate.parse(request.getEndDate()));
        exportMapper.insert(record);

        Map<String, Object> result = new HashMap<>();
        result.put("exportId", record.getId());
        result.put("status", "PROCESSING");
        result.put("estimatedSeconds", 30);
        return Result.success(result);
    }

    @GetMapping("/{exportId}")
    public Result<ExportRecord> getExportStatus(@PathVariable Long exportId) {
        Long userId = SecurityUtils.getCurrentUserId();
        ExportRecord record = exportMapper.selectById(exportId);
        if (record == null || !record.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.EXPORT_NOT_FOUND);
        }
        return Result.success(record);
    }

    @GetMapping("/history")
    public Result<Map<String, Object>> getHistory(@RequestParam(defaultValue = "1") int page,
                                                   @RequestParam(defaultValue = "10") int pageSize) {
        Long userId = SecurityUtils.getCurrentUserId();
        LambdaQueryWrapper<ExportRecord> q = new LambdaQueryWrapper<>();
        q.eq(ExportRecord::getUserId, userId).orderByDesc(ExportRecord::getCreateTime);
        IPage<ExportRecord> result = exportMapper.selectPage(new Page<>(page, pageSize), q);

        Map<String, Object> data = new HashMap<>();
        data.put("records", result.getRecords());
        data.put("total", result.getTotal());
        data.put("page", result.getCurrent());
        data.put("pageSize", result.getSize());
        data.put("totalPages", result.getPages());
        return Result.success(data);
    }
}
