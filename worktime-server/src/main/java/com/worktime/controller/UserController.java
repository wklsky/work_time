package com.worktime.controller;

import com.worktime.common.Result;
import com.worktime.dto.request.*;
import com.worktime.security.SecurityUtils;
import com.worktime.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/user")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @PostMapping("/register")
    public Result<Map<String, Object>> register(@Valid @RequestBody RegisterRequest request) {
        return Result.success(userService.register(
                request.getUsername(), request.getPassword(), request.getPhone()));
    }

    @PostMapping("/login")
    public Result<Map<String, Object>> login(@Valid @RequestBody LoginRequest request) {
        return Result.success(userService.login(request.getPhone(), request.getPassword()));
    }

    @GetMapping("/profile")
    public Result<Map<String, Object>> getProfile() {
        return Result.success(userService.getProfile(SecurityUtils.getCurrentUserId()));
    }

    @PutMapping("/profile")
    public Result<Void> updateProfile(@RequestBody UpdateProfileRequest request) {
        userService.updateProfile(SecurityUtils.getCurrentUserId(),
                request.getNickname(), request.getAvatar(), request.getTimezone());
        return Result.success();
    }

    @PutMapping("/password")
    public Result<Void> changePassword(@RequestBody Map<String, String> body) {
        userService.changePassword(SecurityUtils.getCurrentUserId(),
                body.get("oldPassword"), body.get("newPassword"));
        return Result.success();
    }

    @PostMapping("/logout")
    public Result<Void> logout() {
        return Result.success();
    }
}
