package com.worktime.common;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum ErrorCode {

    // 通用
    SUCCESS(200, "success"),
    BAD_REQUEST(400, "请求参数错误"),
    UNAUTHORIZED(401, "未认证"),
    FORBIDDEN(403, "无权限"),
    NOT_FOUND(404, "资源不存在"),
    CONFLICT(409, "业务冲突"),
    VALIDATION_FAILED(422, "业务校验失败"),
    INTERNAL_ERROR(500, "服务器内部错误"),

    // 用户 (1xxx)
    USER_NOT_FOUND(1001, "用户不存在"),
    PASSWORD_ERROR(1002, "密码错误"),
    PHONE_EXISTS(1003, "手机号已注册"),
    USERNAME_EXISTS(1004, "用户名已存在"),
    TOKEN_EXPIRED(1005, "Token已过期"),
    TOKEN_INVALID(1006, "Token无效"),
    USER_DISABLED(1007, "用户已被禁用"),

    // 打卡 (2xxx)
    RECORD_NOT_FOUND(2001, "打卡记录不存在"),
    PENDING_EXISTS(2002, "已存在进行中的工作记录"),
    NO_PENDING_RECORD(2003, "当前没有进行中的工作记录"),
    INVALID_CLOCK_TIME(2004, "无效的打卡时间"),
    RECORD_NOT_OWNER(2005, "无权操作此记录"),

    // 设置 (3xxx)
    INVALID_TIME_FORMAT(3001, "无效的时间格式"),
    INVALID_CALENDAR_VIEW(3002, "无效的日历视图"),
    INVALID_INPUT_MODE(3003, "无效的录入模式"),

    // 汇总 (4xxx)
    INVALID_DATE_RANGE(4001, "无效的日期区间"),
    DATE_RANGE_TOO_LARGE(4002, "日期区间超过上限"),
    END_BEFORE_START(4003, "结束日期不能早于开始日期"),

    // 导出 (5xxx)
    EXPORT_NOT_FOUND(5001, "导出记录不存在"),
    EXPORT_FAILED(5002, "导出失败"),
    ;

    private final int code;
    private final String message;
}
