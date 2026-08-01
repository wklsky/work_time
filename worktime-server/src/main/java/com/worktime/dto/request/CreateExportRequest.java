package com.worktime.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class CreateExportRequest {
    @NotBlank
    private String exportType;

    private String startDate;
    private String endDate;

    @NotBlank
    private String fileFormat;
}
