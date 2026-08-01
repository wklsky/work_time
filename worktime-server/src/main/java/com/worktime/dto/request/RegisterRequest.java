package com.worktime.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class RegisterRequest {
    @NotBlank @Size(min = 4, max = 20)
    @Pattern(regexp = "^[a-zA-Z0-9_]+$")
    private String username;

    @NotBlank @Size(min = 8, max = 20)
    private String password;

    @NotBlank @Pattern(regexp = "^1[3-9]\\d{9}$")
    private String phone;
}
