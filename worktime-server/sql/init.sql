-- ================================================================
-- WorkTime Pro — 数据库初始化脚本
-- 版本: V1.1 | MySQL 8.0+
-- 用法: mysql -u root -p < init.sql
-- ================================================================

-- 创建数据库
CREATE DATABASE IF NOT EXISTS worktime_db
    DEFAULT CHARACTER SET utf8mb4
    DEFAULT COLLATE utf8mb4_unicode_ci;

USE worktime_db;

-- ================================================================
-- 1. 用户表
-- ================================================================
DROP TABLE IF EXISTS sys_user;
CREATE TABLE sys_user (
    id           BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    username     VARCHAR(50)  NOT NULL              COMMENT '用户名',
    password     VARCHAR(128) NOT NULL              COMMENT '密码（BCrypt加密）',
    phone        VARCHAR(20)  NOT NULL              COMMENT '手机号',
    nickname     VARCHAR(50)  DEFAULT NULL          COMMENT '昵称',
    avatar       VARCHAR(255) DEFAULT NULL          COMMENT '头像URL（OSS）',
    timezone     VARCHAR(50)  DEFAULT 'Asia/Shanghai' COMMENT 'IANA时区标识',
    status       TINYINT      DEFAULT 1             COMMENT '状态 1-启用 0-禁用',
    deleted      TINYINT      DEFAULT 0             COMMENT '逻辑删除 0-正常 1-已删除',
    create_time  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_phone   (phone),
    KEY        idx_username (username)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户表';

-- ================================================================
-- 2. 工作规则表
-- ================================================================
DROP TABLE IF EXISTS work_rule;
CREATE TABLE work_rule (
    id             BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    user_id        BIGINT UNSIGNED NOT NULL              COMMENT '用户ID',
    rule_name      VARCHAR(50)  DEFAULT '默认规则'        COMMENT '规则名称',
    start_time     TIME         NOT NULL DEFAULT '09:00:00' COMMENT '标准上班时间',
    end_time       TIME         NOT NULL DEFAULT '18:00:00' COMMENT '标准下班时间',
    rest_start     TIME         DEFAULT '12:00:00'       COMMENT '午休开始',
    rest_end       TIME         DEFAULT '13:00:00'       COMMENT '午休结束',
    buffer_minutes INT          DEFAULT 30               COMMENT '加班缓冲时间（分钟）',
    day_cut_time   TIME         DEFAULT '04:00:00'       COMMENT '日切时间（凌晨4点前的记录归属前一日）',
    work_days      VARCHAR(20)  DEFAULT '1,2,3,4,5'      COMMENT '工作日（逗号分隔，1=周一 7=周日）',
    is_default     TINYINT      DEFAULT 0                COMMENT '是否默认规则（每用户仅一条）',
    status         TINYINT      DEFAULT 1                COMMENT '状态 1-启用 0-禁用',
    create_time    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_user_id      (user_id),
    KEY idx_user_default (user_id, is_default)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='工作规则表';

-- ================================================================
-- 3. 打卡记录表
-- ================================================================
DROP TABLE IF EXISTS attendance_record;
CREATE TABLE attendance_record (
    id               BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    user_id          BIGINT UNSIGNED NOT NULL              COMMENT '用户ID',
    work_date        DATE            NOT NULL              COMMENT '业务归属日期（按日切时间计算）',
    clock_in         DATETIME        NOT NULL              COMMENT '上班打卡时间 UTC',
    clock_out        DATETIME        DEFAULT NULL          COMMENT '下班打卡时间 UTC（进行中时为NULL）',
    work_minutes     INT             DEFAULT 0             COMMENT '工作时长（分钟，已扣除午休）',
    overtime_minutes INT             DEFAULT 0             COMMENT '加班时长（分钟）',
    source           VARCHAR(20)     DEFAULT 'BUTTON'      COMMENT '来源 BUTTON-按钮打卡 MANUAL-手动录入',
    cross_day        TINYINT         DEFAULT 0             COMMENT '是否跨天 0-否 1-是',
    rule_id          BIGINT UNSIGNED DEFAULT NULL          COMMENT '使用的规则ID（用于追溯）',
    remark           VARCHAR(255)    DEFAULT NULL          COMMENT '备注',
    status           VARCHAR(20)     DEFAULT 'PENDING'     COMMENT '状态 PENDING-进行中 COMPLETED-已完成',
    create_time      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_user_date   (user_id, work_date),
    KEY idx_user_status (user_id, status),
    KEY idx_work_date   (work_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='打卡记录表';

-- ================================================================
-- 4. 每日统计汇总表（物化视图，按时汇总）
-- ================================================================
DROP TABLE IF EXISTS statistics_daily;
CREATE TABLE statistics_daily (
    id                BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    user_id           BIGINT UNSIGNED NOT NULL              COMMENT '用户ID',
    stat_date         DATE            NOT NULL              COMMENT '统计日期（按work_date）',
    attendance_count  INT             DEFAULT 0             COMMENT '打卡次数（一日多段时>1）',
    work_minutes      INT             DEFAULT 0             COMMENT '工作时长合计（分钟）',
    overtime_minutes  INT             DEFAULT 0             COMMENT '加班时长合计（分钟）',
    first_clock_in    DATETIME        DEFAULT NULL          COMMENT '当日首次上班时间',
    last_clock_out    DATETIME        DEFAULT NULL          COMMENT '当日最后下班时间',
    update_time       DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_user_date (user_id, stat_date),
    KEY        idx_stat_date (stat_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='每日统计汇总表';

-- ================================================================
-- 5. 用户设置表
-- ================================================================
DROP TABLE IF EXISTS user_setting;
CREATE TABLE user_setting (
    id                            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    user_id                       BIGINT UNSIGNED NOT NULL              COMMENT '用户ID',
    show_worktime_after_clockout  TINYINT  DEFAULT 1                    COMMENT '结束后是否显示工时 1-是 0-否',
    time_format                   VARCHAR(10)  DEFAULT '24H'            COMMENT '时间格式 24H-24小时制 12H-12小时制',
    home_calendar_view            VARCHAR(20)  DEFAULT 'TODAY'          COMMENT '首页日历视图 MONTH/WEEK/TODAY',
    default_input_mode            VARCHAR(20)  DEFAULT 'BUTTON'         COMMENT '默认录入模式 BUTTON/MANUAL',
    create_time                   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time                   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_user_id (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户设置表';

-- ================================================================
-- 6. 报表导出记录表
-- ================================================================
DROP TABLE IF EXISTS export_record;
CREATE TABLE export_record (
    id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    user_id     BIGINT UNSIGNED NOT NULL              COMMENT '用户ID',
    export_type VARCHAR(20)  NOT NULL                 COMMENT '导出维度 DAY/WEEK/MONTH/YEAR/CUSTOM',
    start_date  DATE         DEFAULT NULL             COMMENT '区间开始日期',
    end_date    DATE         DEFAULT NULL             COMMENT '区间结束日期',
    file_format VARCHAR(10)  DEFAULT 'EXCEL'          COMMENT '文件格式 EXCEL/CSV/PDF',
    file_url    VARCHAR(255) DEFAULT NULL             COMMENT '生成文件OSS URL',
    status      VARCHAR(20)  DEFAULT 'PROCESSING'     COMMENT '状态 PROCESSING/SUCCESS/FAILED',
    error_msg   VARCHAR(255) DEFAULT NULL             COMMENT '失败原因',
    create_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_user_time (user_id, create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='报表导出记录表';

-- ================================================================
-- 初始化完成
-- ================================================================
SELECT 'WorkTime Pro 数据库初始化完成' AS status;

SHOW TABLES;
