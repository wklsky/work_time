# WorkTime Pro · 智能工时与加班管理系统

[![Version](https://img.shields.io/badge/version-1.1.0-blue)](https://github.com)
[![Java](https://img.shields.io/badge/Java-17-orange)](https://openjdk.org/)
[![Vue](https://img.shields.io/badge/Vue-3.x-green)](https://vuejs.org/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.2-brightgreen)](https://spring.io/)

面向个人和团队的工时记录、加班时间统计、时间分析平台。支持自动打卡、手动词条、多维度汇总和报表导出。

---

## 项目结构

```
F:/App/
├── README.md                    # 本文件
├── PRD.md                       # 产品需求文档 V1.1
├── DevelopmentPlan.md           # 开发计划书
├── API_Design.md                # API 接口设计文档 (26 个接口)
├── 数据库设计文档.md               # 数据库设计文档 (6 张核心表)
│
├── worktime-app/                # 📱 移动端 (Uni-App + Vue 3 + TS)
│   └── src/
│       ├── api/                 #   6 个 API 模块
│       ├── mock/                #   开发用 Mock 系统 (7 个处理器)
│       ├── stores/              #   Pinia 状态管理
│       ├── composables/         #   Vue 3 组合式函数
│       ├── components/          #   14 个 UI 组件
│       ├── pages/               #   15 个页面
│       ├── types/               #   TypeScript 类型定义
│       └── utils/               #   工具函数库
│
└── worktime-server/             # 🖥️ 后端服务 (Spring Boot + MyBatis Plus)
    └── src/main/java/com/worktime/
        ├── config/              #   应用配置 (Security/MyBatis/Redis/CORS)
        ├── security/            #   JWT 认证与鉴权
        ├── common/              #   统一响应、异常处理、错误码
        ├── entity/              #   6 个数据实体
        ├── mapper/              #   MyBatis Plus 数据访问层
        ├── dto/request/         #   9 个请求 DTO
        ├── engine/              #   ⭐ 工时计算引擎
        ├── service/             #   业务逻辑层 (5 个 Service)
        └── controller/          #   REST 接口层 (6 个 Controller)
```

---

## 技术栈

### 移动端 (`worktime-app/`)

| 技术          | 版本    | 说明           |
| ----------- | ----- | ------------ |
| Uni-App     | 3.x   | 跨端框架 (Android/iOS/小程序) |
| Vue         | 3.x   | 组合式 API      |
| Pinia       | 2.x   | 状态管理         |
| TypeScript  | 5.x   | 类型安全         |
| ECharts     | 5.x   | 图表可视化        |
| SCSS        | —     | 样式预处理        |

### 后端 (`worktime-server/`)

| 技术              | 版本    | 说明       |
| --------------- | ----- | -------- |
| Java            | 17    | 运行环境     |
| Spring Boot     | 3.2   | 应用框架     |
| Spring Security | 6.x   | 认证与鉴权    |
| MyBatis Plus    | 3.5   | ORM 框架   |
| MySQL           | 8.0   | 关系数据库    |
| Redis           | 7.x   | 缓存与 Token 管理 |
| JWT (jjwt)     | 0.12  | 无状态认证    |
| Lombok          | —     | 代码简化     |

---

## 快速开始

### 1. 克隆并安装依赖

```bash
# 移动端
cd worktime-app
npm install

# 后端
cd worktime-server
mvn clean install
```

### 2. 配置数据库

确保 MySQL 8.0+ 和 Redis 7.x 已启动，然后执行建表脚本：

```bash
mysql -u root -p < worktime-server/src/main/resources/db/schema.sql
```

修改 `worktime-server/src/main/resources/application-dev.yml` 中的数据库连接信息。

### 3. 启动后端

```bash
cd worktime-server
mvn spring-boot:run
# 服务启动在 http://localhost:8080
```

### 4. 启动前端

```bash
cd worktime-app

# H5 浏览器预览
npm run dev:h5

# 微信小程序
npm run dev:mp-weixin

# Android/iOS App
npm run dev:app
```

> 开发模式下前端默认使用 Mock 数据（`VITE_USE_MOCK=true`），无需后端即可运行。
> 如需连接后端，设置 `VITE_USE_MOCK=false` 并确保后端已启动。

### 5. 测试账号

Mock 模式默认账号：

| 字段 | 值 |
| --- | --- |
| 手机号 | `13800138000` |
| 密码 | `Abc@123456` |

---

## 核心功能

### 📱 移动端

| 模块 | 功能 |
| --- | --- |
| **首页** | 今日日期、开始/结束工作、实时计时、工时自动展示 |
| **日历** | 月/星期/今日 三种视图，加班色阶标记，左右滑动切换 |
| **打卡** | 按钮模式（服务端时间）+ 手动模式（时间选择器），自由切换 |
| **统计** | 月度/年度数据可视化趋势图 |
| **我的** | 日/周/月/年/自定义 五维度工时汇总 |
| **设置** | 时间进制 (24H/12H)、工时显示开关、日历默认视图、工作规则 |
| **报表** | 异步导出 Excel/CSV/PDF，轮询状态，下载文件 |

### 🖥️ 后端

| 模块 | 接口数 | 说明 |
| --- | --- | --- |
| 用户中心 | 6 | 注册、登录 (JWT)、资料、密码 |
| 首页 | 1 | 今日数据聚合 |
| 打卡 | 6 | 上下班、手动录入、修改、日历 |
| 汇总统计 | 6 | 日/周/月/年/自定义/快捷区间 |
| 设置 | 4 | 用户设置 + 工作规则 |
| 报表 | 3 | 异步导出创建/状态/历史 |

---

## 工时计算引擎 ⭐

核心业务逻辑位于 `worktime-server/src/main/java/com/worktime/engine/`：

```
                   ┌─────────────────────┐
                   │   WorkTimeCalculator │  ← 主入口
                   └──────────┬──────────┘
                              │
          ┌───────────────────┼───────────────────┐
          │                   │                   │
          ▼                   ▼                   ▼
┌─────────────────┐ ┌─────────────────┐ ┌─────────────────┐
│ WorkdayClassifier│ │OvertimeCalculator│ │DateAttribution  │
│ 工作日/周末判定    │ │ 加班分钟计算      │ │Resolver         │
│                  │ │                  │ │ 日切时间归属     │
└─────────────────┘ └─────────────────┘ └─────────────────┘
```

**三种计算公式：**

| 场景 | 公式 |
| --- | --- |
| 工作日 | 加班 = 总时长 − 午休 − 标准工时(480分) − 缓冲(30分) |
| 周末 | 加班 = 总时长 − 午休 (全部算加班) |
| 跨天 | 自动检测 clock_out < clock_in，按次日处理 |

---

## 测试

### 后端单元测试

工时引擎覆盖 **11 类场景**：正常上下班、迟到、早退、跨天、周末、一日多段、手动输入、手动修改重算、12/24小时制转换、自定义区间边界。

### 启动测试

```bash
cd worktime-server
mvn test
```

---

## 环境要求

| 组件 | 最低版本 |
| --- | --- |
| Node.js | 18+ |
| Java | 17+ |
| MySQL | 8.0+ |
| Redis | 7.x |
| Maven | 3.8+ |
| HBuilderX | 最新版 (仅 App 打包需要) |

---

## 文档索引

- [产品需求文档 (PRD)](PRD.md) — 功能范围、验收标准
- [开发计划书](DevelopmentPlan.md) — 任务分解、排期、风险
- [API 接口设计文档](API_Design.md) — 26 个 REST API 详细规范
- [数据库设计文档](数据库设计文档.md) — 6 张表 DDL、索引策略、Redis 缓存键设计

---

## 许可证

内部项目，仅供学习与团队使用。
