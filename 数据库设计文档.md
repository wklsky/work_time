---

# 智能工时与加班管理系统

# 数据库设计文档

**项目名称：** WorkTime Pro

**文档版本：** V1.1

**编制日期：** 2026-08-01

**数据库：** MySQL 8.0+

---

# 第一章 设计原则

## 1.1 基本原则

* 字符集：UTF8MB4，支持 Emoji
* 引擎：InnoDB
* 主键：BIGINT UNSIGNED AUTO_INCREMENT
* 时间字段：DATETIME，统一 UTC 存储
* 软删除：`deleted` TINYINT DEFAULT 0
* 索引：高频查询字段建联合索引，顺序遵循最左前缀
* 时区：用户时区存储在 `sys_user.timezone`，计算层处理
* 日期归属：按用户「日切时间」规则计算，落库到 `work_date`

---

## 1.2 命名规范

| 类型   | 规范                | 示例                    |
| ---- | ----------------- | --------------------- |
| 数据库  | worktime_db       | worktime_db           |
| 表    | 小写下划线              | attendance_record     |
| 字段   | 小写下划线              | clock_in / work_date  |
| 索引   | idx_字段名            | idx_user_id_date      |
| 唯一索引 | uk_字段名             | uk_phone              |
| 外键   | fk_表名_字段名          | fk_record_user_id     |

---

# 第二章 核心表设计

## 2.1 用户表 sys_user

**功能：** 用户账号与基础信息

```sql
CREATE TABLE `sys_user` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  `username` VARCHAR(50) NOT NULL COMMENT '用户名',
  `password` VARCHAR(128) NOT NULL COMMENT '密码（加密）',
  `phone` VARCHAR(20) NOT NULL COMMENT '手机号',
  `nickname` VARCHAR(50) DEFAULT NULL COMMENT '昵称',
  `avatar` VARCHAR(255) DEFAULT NULL COMMENT '头像 URL',
  `timezone` VARCHAR(50) DEFAULT 'Asia/Shanghai' COMMENT '用户时区',
  `status` TINYINT DEFAULT 1 COMMENT '状态 1-启用 0-禁用',
  `deleted` TINYINT DEFAULT 0 COMMENT '删除标识',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_phone` (`phone`),
  KEY `idx_username` (`username`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户表';
```

**字段说明：**

| 字段         | 类型            | 必填 | 说明                                |
| ---------- | ------------- | -- | --------------------------------- |
| id         | BIGINT        | 是  | 用户唯一标识                            |
| username   | VARCHAR(50)   | 是  | 登录用户名                             |
| password   | VARCHAR(128)  | 是  | BCrypt 加密后密码                      |
| phone      | VARCHAR(20)   | 是  | 手机号，唯一                            |
| nickname   | VARCHAR(50)   | 否  | 显示昵称                              |
| avatar     | VARCHAR(255)  | 否  | 头像 URL（OSS）                       |
| timezone   | VARCHAR(50)   | 是  | IANA 时区标识，默认 Asia/Shanghai       |
| status     | TINYINT       | 是  | 1-启用，0-禁用                         |
| deleted    | TINYINT       | 是  | 软删除标识                             |
| create_time | DATETIME     | 是  | 创建时间 UTC                          |
| update_time | DATETIME     | 是  | 更新时间 UTC                          |

---

## 2.2 工作规则表 work_rule

**功能：** 用户个性化工作时间规则

```sql
CREATE TABLE `work_rule` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  `user_id` BIGINT UNSIGNED NOT NULL COMMENT '用户ID',
  `rule_name` VARCHAR(50) DEFAULT '默认规则' COMMENT '规则名称',
  `start_time` TIME NOT NULL DEFAULT '09:00:00' COMMENT '标准上班时间',
  `end_time` TIME NOT NULL DEFAULT '18:00:00' COMMENT '标准下班时间',
  `rest_start` TIME DEFAULT '12:00:00' COMMENT '午休开始',
  `rest_end` TIME DEFAULT '13:00:00' COMMENT '午休结束',
  `buffer_minutes` INT DEFAULT 30 COMMENT '加班缓冲时间（分钟）',
  `day_cut_time` TIME DEFAULT '04:00:00' COMMENT '日切时间（跨天记录归属判定）',
  `work_days` VARCHAR(20) DEFAULT '1,2,3,4,5' COMMENT '工作日（1-7，周一到周日）',
  `is_default` TINYINT DEFAULT 0 COMMENT '是否默认规则',
  `status` TINYINT DEFAULT 1 COMMENT '状态 1-启用 0-禁用',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_user_default` (`user_id`, `is_default`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='工作规则表';
```

**字段说明：**

| 字段             | 类型           | 必填 | 说明                               |
| -------------- | ------------ | -- | -------------------------------- |
| id             | BIGINT       | 是  | 规则唯一标识                           |
| user_id        | BIGINT       | 是  | 关联用户                             |
| rule_name      | VARCHAR(50)  | 否  | 规则名称（支持多规则场景）                    |
| start_time     | TIME         | 是  | 标准上班时间，如 09:00:00                |
| end_time       | TIME         | 是  | 标准下班时间，如 18:00:00                |
| rest_start     | TIME         | 否  | 午休开始                             |
| rest_end       | TIME         | 否  | 午休结束                             |
| buffer_minutes | INT          | 是  | 加班缓冲时间（分钟），工作日下班后该时长内不算加班        |
| day_cut_time   | TIME         | 是  | 日切时间，如 04:00，凌晨 4 点前的记录归属前一自然日   |
| work_days      | VARCHAR(20)  | 是  | 工作日，逗号分隔，1=周一，7=周日               |
| is_default     | TINYINT      | 是  | 是否用户默认规则，每用户仅一条                  |
| status         | TINYINT      | 是  | 启用状态                             |

**业务规则：**
* 每用户至少一条 `is_default=1` 的规则
* 规则修改仅对修改后新增记录生效，不回溯历史

---

## 2.3 打卡记录表 attendance_record

**功能：** 存储每次上下班打卡记录

```sql
CREATE TABLE `attendance_record` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  `user_id` BIGINT UNSIGNED NOT NULL COMMENT '用户ID',
  `work_date` DATE NOT NULL COMMENT '业务归属日期（按日切时间计算）',
  `clock_in` DATETIME NOT NULL COMMENT '上班打卡时间 UTC',
  `clock_out` DATETIME DEFAULT NULL COMMENT '下班打卡时间 UTC',
  `work_minutes` INT DEFAULT 0 COMMENT '工作时长（分钟）',
  `overtime_minutes` INT DEFAULT 0 COMMENT '加班时长（分钟）',
  `source` VARCHAR(20) DEFAULT 'BUTTON' COMMENT '来源 BUTTON-按钮 MANUAL-手动',
  `cross_day` TINYINT DEFAULT 0 COMMENT '是否跨天 0-否 1-是',
  `rule_id` BIGINT UNSIGNED DEFAULT NULL COMMENT '使用的规则ID',
  `remark` VARCHAR(255) DEFAULT NULL COMMENT '备注',
  `status` VARCHAR(20) DEFAULT 'PENDING' COMMENT '状态 PENDING-进行中 COMPLETED-已完成',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_user_date` (`user_id`, `work_date`),
  KEY `idx_user_status` (`user_id`, `status`),
  KEY `idx_work_date` (`work_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='打卡记录表';
```

**字段说明：**

| 字段               | 类型            | 必填 | 说明                                    |
| ---------------- | ------------- | -- | ------------------------------------- |
| id               | BIGINT        | 是  | 记录唯一标识                                |
| user_id          | BIGINT        | 是  | 关联用户                                  |
| work_date        | DATE          | 是  | 业务归属日期，按用户日切时间规则计算，非自然日             |
| clock_in         | DATETIME      | 是  | 上班打卡时间 UTC                            |
| clock_out        | DATETIME      | 否  | 下班打卡时间 UTC，进行中时为 NULL                |
| work_minutes     | INT           | 是  | 工作时长（分钟），扣除午休                         |
| overtime_minutes | INT           | 是  | 加班时长（分钟），按规则计算                        |
| source           | VARCHAR(20)   | 是  | BUTTON=按钮打卡，MANUAL=手动录入               |
| cross_day        | TINYINT       | 是  | 是否跨天（下班时间 < 上班时间）                     |
| rule_id          | BIGINT        | 否  | 使用的规则 ID，用于追溯                         |
| remark           | VARCHAR(255)  | 否  | 用户手动备注                                |
| status           | VARCHAR(20)   | 是  | PENDING=进行中，COMPLETED=已完成             |

**索引说明：**
* `idx_user_date`：用户 + 日期，首页与日历高频查询
* `idx_user_status`：用户 + 状态，查询进行中记录
* `idx_work_date`：按日期范围查询，汇总任务使用

**业务规则：**
* 一个用户可有多条 PENDING 记录（一日多段工时）
* `work_date` 在 clock_out 时由后端按 `day_cut_time` 规则计算写入
* 跨天记录：`cross_day=1`，如 22:00 → 次日 02:00
* 修改记录后触发 `statistics_daily` 重算

---

## 2.4 统计汇总表 statistics_daily

**功能：** 按日汇总物化表，支持高速查询

```sql
CREATE TABLE `statistics_daily` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  `user_id` BIGINT UNSIGNED NOT NULL COMMENT '用户ID',
  `stat_date` DATE NOT NULL COMMENT '统计日期',
  `attendance_count` INT DEFAULT 0 COMMENT '打卡次数（一日多段）',
  `work_minutes` INT DEFAULT 0 COMMENT '工作时长合计（分钟）',
  `overtime_minutes` INT DEFAULT 0 COMMENT '加班时长合计（分钟）',
  `first_clock_in` DATETIME DEFAULT NULL COMMENT '首次上班时间',
  `last_clock_out` DATETIME DEFAULT NULL COMMENT '最后下班时间',
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_date` (`user_id`, `stat_date`),
  KEY `idx_stat_date` (`stat_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='每日统计汇总表';
```

**字段说明：**

| 字段               | 类型       | 必填 | 说明                       |
| ---------------- | -------- | -- | ------------------------ |
| id               | BIGINT   | 是  | 主键                       |
| user_id          | BIGINT   | 是  | 用户 ID                    |
| stat_date        | DATE     | 是  | 统计日期（按 work_date）       |
| attendance_count | INT      | 是  | 当日打卡次数（一日多段时 > 1）        |
| work_minutes     | INT      | 是  | 当日工作时长合计（分钟）             |
| overtime_minutes | INT      | 是  | 当日加班时长合计（分钟）             |
| first_clock_in   | DATETIME | 否  | 当日首次上班时间（用于展示）           |
| last_clock_out   | DATETIME | 否  | 当日最后下班时间（用于展示）           |
| update_time      | DATETIME | 是  | 更新时间                     |

**写入时机：**
* 用户结束工作（clock_out）后，异步任务写入或更新该日记录
* 手动修改 `attendance_record` 后，触发对应 `stat_date` 重算
* 唯一键 `uk_user_date` 保证幂等

**查询优势：**
* 日汇总：直接查一条
* 周汇总：7 条聚合
* 月汇总：~30 条聚合
* 年汇总：365 条聚合
* 自定义区间：按区间 scan，加 Redis 缓存

---

## 2.5 用户设置表 user_setting

**功能：** 用户个性化显示与行为配置

```sql
CREATE TABLE `user_setting` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  `user_id` BIGINT UNSIGNED NOT NULL COMMENT '用户ID',
  `show_worktime_after_clockout` TINYINT DEFAULT 1 COMMENT '结束工作后是否显示工时 1-是 0-否',
  `time_format` VARCHAR(10) DEFAULT '24H' COMMENT '时间格式 24H-24小时制 12H-12小时制',
  `home_calendar_view` VARCHAR(20) DEFAULT 'TODAY' COMMENT '首页日历视图 MONTH-月 WEEK-星期 TODAY-今日',
  `default_input_mode` VARCHAR(20) DEFAULT 'BUTTON' COMMENT '默认录入模式 BUTTON-按钮 MANUAL-手动',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户设置表';
```

**字段说明：**

| 字段                          | 类型          | 必填 | 默认值    | 说明                     |
| --------------------------- | ----------- | -- | ------ | ---------------------- |
| id                          | BIGINT      | 是  | -      | 主键                     |
| user_id                     | BIGINT      | 是  | -      | 用户 ID，唯一               |
| show_worktime_after_clockout | TINYINT     | 是  | 1      | 结束工作后首页是否显示工时卡片        |
| time_format                 | VARCHAR(10) | 是  | 24H    | 24H=24 小时制，12H=12 小时制  |
| home_calendar_view          | VARCHAR(20) | 是  | TODAY  | MONTH/WEEK/TODAY       |
| default_input_mode          | VARCHAR(20) | 是  | BUTTON | BUTTON=按钮，MANUAL=手动输入 |

**业务规则：**
* 每用户一条，注册时初始化
* 修改后云端同步，本地缓存优先读
* 离线可改，联网后按 `update_time` 合并

---

## 2.6 报表导出记录表 export_record

**功能：** 报表导出任务与文件追踪

```sql
CREATE TABLE `export_record` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  `user_id` BIGINT UNSIGNED NOT NULL COMMENT '用户ID',
  `export_type` VARCHAR(20) NOT NULL COMMENT '导出类型 DAY/WEEK/MONTH/YEAR/CUSTOM',
  `start_date` DATE DEFAULT NULL COMMENT '区间开始日期',
  `end_date` DATE DEFAULT NULL COMMENT '区间结束日期',
  `file_format` VARCHAR(10) DEFAULT 'EXCEL' COMMENT '文件格式 EXCEL/CSV/PDF',
  `file_url` VARCHAR(255) DEFAULT NULL COMMENT '文件URL（OSS）',
  `status` VARCHAR(20) DEFAULT 'PROCESSING' COMMENT '状态 PROCESSING/SUCCESS/FAILED',
  `error_msg` VARCHAR(255) DEFAULT NULL COMMENT '失败原因',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_user_time` (`user_id`, `create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='报表导出记录表';
```

**字段说明：**

| 字段          | 类型            | 必填 | 说明                              |
| ----------- | ------------- | -- | ------------------------------- |
| id          | BIGINT        | 是  | 主键                              |
| user_id     | BIGINT        | 是  | 用户 ID                           |
| export_type | VARCHAR(20)   | 是  | 导出维度：DAY/WEEK/MONTH/YEAR/CUSTOM |
| start_date  | DATE          | 否  | 自定义区间起始                         |
| end_date    | DATE          | 否  | 自定义区间结束                         |
| file_format | VARCHAR(10)   | 是  | EXCEL/CSV/PDF                   |
| file_url    | VARCHAR(255)  | 否  | 生成文件 OSS URL                    |
| status      | VARCHAR(20)   | 是  | PROCESSING/SUCCESS/FAILED       |
| error_msg   | VARCHAR(255)  | 否  | 失败原因                            |

**业务规则：**
* 导出任务异步，前端轮询 status
* 文件 7 天后自动删除（OSS 生命周期）

---

# 第三章 索引策略

## 3.1 索引清单

| 表                    | 索引名称              | 字段                        | 类型  | 说明          |
| -------------------- | ----------------- | ------------------------- | --- | ----------- |
| sys_user             | uk_phone          | phone                     | 唯一  | 手机号登录       |
| sys_user             | idx_username      | username                  | 普通  | 用户名查询       |
| work_rule            | idx_user_id       | user_id                   | 普通  | 用户规则列表      |
| work_rule            | idx_user_default  | user_id, is_default       | 普通  | 查默认规则       |
| attendance_record    | idx_user_date     | user_id, work_date        | 普通  | 首页日历高频      |
| attendance_record    | idx_user_status   | user_id, status           | 普通  | 查进行中记录      |
| attendance_record    | idx_work_date     | work_date                 | 普通  | 汇总任务扫描      |
| statistics_daily     | uk_user_date      | user_id, stat_date        | 唯一  | 汇总幂等        |
| statistics_daily     | idx_stat_date     | stat_date                 | 普通  | 按日期范围查询     |
| user_setting         | uk_user_id        | user_id                   | 唯一  | 每用户唯一设置     |
| export_record        | idx_user_time     | user_id, create_time      | 普通  | 导出历史列表      |

---

## 3.2 索引设计原则

* 高频查询字段建索引
* 联合索引遵循最左前缀原则
* 唯一约束用唯一索引
* 区分度低的字段（如 status）不单独建索引，与高区分度字段组合
* 避免过多索引影响写性能

---

# 第四章 数据迁移方案

## 4.1 V1.0 → V1.1 变更清单

### 4.1.1 表结构变更

**attendance_record 新增字段：**

```sql
ALTER TABLE `attendance_record`
ADD COLUMN `work_date` DATE NOT NULL COMMENT '业务归属日期' AFTER `user_id`,
ADD COLUMN `cross_day` TINYINT DEFAULT 0 COMMENT '是否跨天' AFTER `overtime_minutes`,
ADD COLUMN `remark` VARCHAR(255) DEFAULT NULL COMMENT '备注' AFTER `rule_id`,
ADD KEY `idx_work_date` (`work_date`);
```

**statistics_daily 新增字段：**

```sql
ALTER TABLE `statistics_daily`
ADD COLUMN `work_minutes` INT DEFAULT 0 COMMENT '工作时长合计' AFTER `attendance_count`,
ADD COLUMN `first_clock_in` DATETIME DEFAULT NULL COMMENT '首次上班时间' AFTER `overtime_minutes`,
ADD COLUMN `last_clock_out` DATETIME DEFAULT NULL COMMENT '最后下班时间' AFTER `first_clock_in`;
```

---

### 4.1.2 新增表

```sql
-- 用户设置表
CREATE TABLE `user_setting` (...);

-- 导出记录表
CREATE TABLE `export_record` (...);
```

---

### 4.1.3 数据回填脚本

**回填 attendance_record.work_date：**

```sql
-- 根据 clock_in 与用户日切时间规则计算 work_date
-- 示例：日切时间 04:00
UPDATE attendance_record ar
INNER JOIN work_rule wr ON ar.user_id = wr.user_id AND wr.is_default = 1
SET ar.work_date = CASE
  WHEN TIME(CONVERT_TZ(ar.clock_in, '+00:00', wr.timezone)) < wr.day_cut_time
    THEN DATE_SUB(DATE(CONVERT_TZ(ar.clock_in, '+00:00', wr.timezone)), INTERVAL 1 DAY)
  ELSE DATE(CONVERT_TZ(ar.clock_in, '+00:00', wr.timezone))
END
WHERE ar.work_date IS NULL;
```

**回填 statistics_daily.work_minutes：**

```sql
UPDATE statistics_daily sd
INNER JOIN (
  SELECT user_id, stat_date, SUM(work_minutes) AS total_work
  FROM attendance_record
  WHERE status = 'COMPLETED'
  GROUP BY user_id, work_date
) ar ON sd.user_id = ar.user_id AND sd.stat_date = ar.stat_date
SET sd.work_minutes = ar.total_work;
```

**初始化 user_setting：**

```sql
INSERT INTO user_setting (user_id)
SELECT id FROM sys_user WHERE deleted = 0
ON DUPLICATE KEY UPDATE user_id = user_id;
```

---

## 4.2 迁移步骤

1. 预发环境执行 ALTER TABLE（避开业务高峰）
2. 执行回填脚本，比对数据正确性
3. 保留回滚脚本（DROP COLUMN / DELETE）
4. 生产环境维护窗口迁移
5. 上线后观察慢查询日志

---

# 第五章 性能优化建议

## 5.1 查询优化

| 场景      | 优化手段                            |
| ------- | ------------------------------- |
| 首页今日数据  | Redis 缓存 `index:today:{user_id}` |
| 日历月视图   | `idx_user_date` + 预加载 30 天       |
| 年汇总     | `statistics_daily` + Redis 10 分钟缓存 |
| 自定义长区间  | 限制 366 天 + 结果缓存                  |

---

## 5.2 写入优化

| 场景     | 优化手段                     |
| ------ | ------------------------ |
| 结束工作后汇总 | 异步消息队列（RabbitMQ / Kafka） |
| 批量重算   | 分页处理，避免长事务               |

---

## 5.3 容量规划

**单用户年数据量估算：**
* attendance_record：365 × 2 = 730 行（一日多段场景）
* statistics_daily：365 行
* 10 万用户年增长：~1 亿行

**建议：**
* 按月分表（attendance_record_202608）
* 超 2 年数据归档到 OSS

---

# 第六章 安全与权限

## 6.1 数据隔离

* 所有查询必须带 `user_id` WHERE 条件
* 后端校验 JWT token 中 user_id 与请求参数一致
* 禁止跨用户查询

---

## 6.2 敏感字段

| 字段       | 处理                |
| -------- | ----------------- |
| password | BCrypt 不可逆加密      |
| phone    | 日志脱敏，仅显示 `138****1234` |

---

## 6.3 备份策略

* 全量备份：每日凌晨 2:00
* 增量备份：每小时
* Binlog 保留 7 天
* 跨地域容灾备份

---

# 第七章 附录

## 7.1 建库脚本

```sql
CREATE DATABASE IF NOT EXISTS worktime_db
  DEFAULT CHARACTER SET utf8mb4
  DEFAULT COLLATE utf8mb4_unicode_ci;
```

---

## 7.2 常用查询示例

**查询用户当月出勤天数与工时：**

```sql
SELECT
  COUNT(DISTINCT stat_date) AS days,
  SUM(work_minutes) AS total_work,
  SUM(overtime_minutes) AS total_overtime
FROM statistics_daily
WHERE user_id = ?
  AND stat_date >= '2026-08-01'
  AND stat_date < '2026-09-01';
```

**查询进行中的打卡记录：**

```sql
SELECT * FROM attendance_record
WHERE user_id = ?
  AND status = 'PENDING'
ORDER BY clock_in DESC
LIMIT 1;
```

---

## 7.3 数据关系总览

```text
sys_user (1) ──────< (N) work_rule          ── 一个用户可配置多条工作规则
sys_user (1) ──────< (N) attendance_record   ── 一个用户有多条打卡记录
sys_user (1) ──────< (N) statistics_daily    ── 一个用户每天一条汇总记录
sys_user (1) ──────  (1)  user_setting       ── 一个用户一条设置记录
sys_user (1) ──────< (N) export_record       ── 一个用户可导出多次
work_rule (1) ──────< (N) attendance_record  ── 一条规则被多条打卡记录引用（通过 rule_id）
attendance_record ────> statistics_daily     ── 打卡记录变更触发汇总重算（异步）
```

**核心数据流：**

```text
用户打卡 (attendance_record)
    │
    ├── clock_in  ──> 写入记录 (status=PENDING)
    │
    └── clock_out ──> 工时引擎计算 work_minutes / overtime_minutes
                          │
                          ├── 更新 attendance_record (status=COMPLETED)
                          │
                          └── 异步汇总 ──> statistics_daily (UPSERT by uk_user_date)
                                               │
                                               └── 供日/周/月/年/自定义查询
```

---

## 7.4 Redis 缓存键设计

### 7.4.1 缓存键规范

| 缓存键模式 | 类型 | TTL | 说明 |
| --- | --- | --- | --- |
| `index:today:{userId}` | Hash | 60s | 首页今日数据缓存 |
| `summary:day:{userId}:{date}` | String(JSON) | 300s | 日汇总缓存 |
| `summary:week:{userId}:{weekStart}` | String(JSON) | 300s | 周汇总缓存 |
| `summary:month:{userId}:{yearMonth}` | String(JSON) | 300s | 月汇总缓存 |
| `summary:year:{userId}:{year}` | String(JSON) | 600s | 年汇总缓存 |
| `summary:custom:{userId}:{hash}` | String(JSON) | 600s | 自定义区间缓存 (hash = MD5(start+end)) |
| `calendar:{userId}:{view}:{key}` | String(JSON) | 120s | 日历数据缓存 |
| `setting:{userId}` | Hash | 3600s | 用户设置缓存 |
| `setting:workrule:{userId}` | String(JSON) | 3600s | 工作规则缓存 |
| `export:status:{exportId}` | String(JSON) | 600s | 导出任务状态缓存 |

### 7.4.2 缓存失效策略

| 触发事件 | 失效的缓存键 |
| --- | --- |
| 开始工作 (clock-in) | `index:today:{userId}`, `calendar:{userId}:*` |
| 结束工作 (clock-out) | `index:today:{userId}`, `summary:*:{userId}:*`, `calendar:{userId}:*` |
| 手动录入 / 修改记录 | `index:today:{userId}`, `summary:*:{userId}:*`, `calendar:{userId}:*` |
| 更新设置 | `setting:{userId}` |
| 更新工作规则 | `setting:workrule:{userId}` |

**失效方式：** 记录变更时通过消息队列发送 `cache.invalidate` 事件，由缓存服务统一处理。写入时采用先更新 DB 再删除缓存的模式 (Cache-Aside)。

---

## 7.5 常用查询补充

**查询月视图日历数据（单次查询 31 天打点）：**

```sql
SELECT work_date, SUM(work_minutes) AS total_work, SUM(overtime_minutes) AS total_overtime,
       COUNT(*) AS record_count, MIN(clock_in) AS first_clock_in, MAX(clock_out) AS last_clock_out
FROM attendance_record
WHERE user_id = ?
  AND work_date >= '2026-08-01'
  AND work_date < '2026-09-01'
  AND status = 'COMPLETED'
GROUP BY work_date;
```

**查询一日多段工时详情：**

```sql
SELECT id, clock_in, clock_out, work_minutes, overtime_minutes, source, cross_day, remark, status
FROM attendance_record
WHERE user_id = ?
  AND work_date = '2026-08-01'
ORDER BY clock_in ASC;
```

**查询自定义区间汇总（带 366 天限制）：**

```sql
SELECT COUNT(DISTINCT stat_date) AS days,
       SUM(work_minutes) AS total_work,
       SUM(overtime_minutes) AS total_overtime
FROM statistics_daily
WHERE user_id = ?
  AND stat_date >= '2026-01-01'
  AND stat_date <= '2026-06-30';
```

**查询用户当日累计工时（用于首页展示）：**

```sql
SELECT COALESCE(SUM(work_minutes), 0) AS today_total_work,
       COALESCE(SUM(overtime_minutes), 0) AS today_total_overtime,
       COUNT(*) AS segment_count,
       MIN(clock_in) AS first_clock_in,
       MAX(clock_out) AS last_clock_out
FROM attendance_record
WHERE user_id = ?
  AND work_date = '2026-08-01'
  AND status = 'COMPLETED';
```

**查询进行中的记录（防止重复打卡校验）：**

```sql
SELECT id, clock_in, source, TIMESTAMPDIFF(MINUTE, clock_in, UTC_TIMESTAMP()) AS elapsed_minutes
FROM attendance_record
WHERE user_id = ?
  AND status = 'PENDING'
ORDER BY clock_in DESC
LIMIT 1;
```

---

## 7.6 数据字典速查表

| 表名 | 行数预估 (10万用户/年) | 读写比 | 核心索引 | 分区建议 |
| --- | --- | --- | --- | --- |
| sys_user | 10万 | 90:10 | uk_phone | 无 |
| work_rule | 10-20万 | 95:5 | idx_user_id | 无 |
| attendance_record | ~7300万 | 60:40 | idx_user_date, idx_work_date | 按月 (attendance_record_YYYYMM) |
| statistics_daily | ~3650万 | 95:5 | uk_user_date | 无 |
| user_setting | 10万 | 80:20 | uk_user_id | 无 |
| export_record | ~50万 | 90:10 | idx_user_time | 无 |

---

## 7.7 字段与 PRD 功能模块映射

| PRD 功能 | 关联表 | 关键字段 |
| --- | --- | --- |
| 用户注册登录 | sys_user | phone, password, username |
| 首页今日日期 | attendance_record | work_date (按日切时间计算) |
| 开始/结束工作 (按钮) | attendance_record | clock_in, clock_out, source=BUTTON, status |
| 手动录入时间 | attendance_record | clock_in, clock_out, source=MANUAL |
| 修改记录重算 | attendance_record | clock_in, clock_out → 触发 work_minutes/overtime_minutes 重算 |
| 跨天工时 | attendance_record | cross_day, clock_in, clock_out |
| 工时显示开关 | user_setting | show_worktime_after_clockout |
| 时间进制 | user_setting | time_format (24H/12H) |
| 日历视图选择 | user_setting | home_calendar_view (MONTH/WEEK/TODAY) |
| 默认录入模式 | user_setting | default_input_mode (BUTTON/MANUAL) |
| 工作规则配置 | work_rule | start_time, end_time, rest_start, rest_end, buffer_minutes, day_cut_time, work_days |
| 日/周/月/年汇总 | statistics_daily | stat_date, work_minutes, overtime_minutes, attendance_count |
| 自定义区间汇总 | statistics_daily | stat_date 范围查询 (≤366天) |
| 报表导出 | export_record | export_type, start_date, end_date, file_format, file_url, status |
| 一日多段工时 | attendance_record | 同一 user_id + work_date 多条 COMPLETED 记录 |
| 加班计算 | attendance_record + work_rule | overtime_minutes = f(clock_in, clock_out, buffer_minutes, work_days) |

---

**文档编制完成**
