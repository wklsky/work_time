# 智能工时与加班管理 App（单机版）

# 开发计划书 V2.0

**项目名称：** 智能工时与加班管理系统（WorkTime Pro）

**对应需求文档：** PRD_V2.md V2.0

**计划版本：** V2.0

**编制日期：** 2026-11-20

**上一版本：** DevelopmentPlan.md V1.0（云端版，保留归档）

---

# 第一章 计划概述

## 1.1 编制目的

本计划书依据 `PRD_V2.md V2.0` 编制，明确 Kotlin 单机重构版的功能边界、技术架构、任务拆解、排期、里程碑与交付标准，作为研发执行依据。

## 1.2 本期核心交付

| 编号 | 能力 | 说明 |
|------|------|------|
| F-1 | 相机拍照 OCR 识别 | 拍摄考勤机屏幕，离线识别四类时间 + 类型 |
| F-2 | 识别结果确认页 | 原图 + 4 时间输入 + 类型修正 + 置信度，人工确认后入库 |
| F-3 | 本月日历首页 | 唯一主视图，月份切换、记录标记、加班色阶 |
| F-4 | 日历下方时间显示 | 上班/加班二选一，**设置中全局统一切换** |
| F-5 | 日详情页 | 四类时间 + 凭证原图 + 计算明细 + 编辑删除 |
| F-6 | 工时引擎迁移 | Kotlin 迁移 + 3 处缺陷修复 + 11 类单测 |
| F-7 | 五维度汇总 + 报表导出 | 本地查询，CSV/Excel/PDF |
| F-8 | 数据备份与恢复 | 本地导出/导入，替代云同步 |

实现期追加交付（V2.1 / V2.2，已全部完成）：

| 编号 | 能力 | 说明 |
|------|------|------|
| F-9 | 一日多段打卡 | 同类型多次打卡按时间贪心配对成多段，今日页与日详情按段展示、逐条编辑 |
| F-10 | 时间线校验 | 写入前校验上下班/加班重叠、段未闭合、结束早于开始，冲突拒绝并提示 |
| F-11 | 今日台账 | 新增「今日」Tab：状态卡 + 24 小时时间带 + 打卡时刻存根 + 今日流水 |
| F-12 | 累计工时提醒 | 阈值型系统通知，AlarmManager 调度，后台/开机/更新后仍生效，同日去重 |
| F-13 | 通用校时器 + 外观体系 | 时间带取时组件全应用复用；10 套预设配色 + 自定义配色 + 字号缩放 + 黑白模式 |
| F-14 | 习惯打卡与考勤机状态映射 | 自定义习惯打卡列表；考勤机状态文字到四类打卡类型的映射配置 |

## 1.3 总体周期

```
总工期：8 周（单人开发）
启动日期：第 1 周
可发布 APK：第 8 周末
```

---

# 第二章 技术架构

## 2.1 技术选型

| 层次 | 技术 | 版本 | 说明 |
|------|------|------|------|
| 语言 | Kotlin | 2.0+ | 全程协程 + Flow |
| UI | Jetpack Compose + Material3 | — | 声明式 UI，替代 Vue |
| 架构 | MVVM + 单向数据流 | — | ViewModel + StateFlow |
| 本地库 | Room (SQLite) | 2.6+ | 替代 MySQL，持久化打卡数据 |
| 偏好设置 | DataStore | 1.1+ | 替代 user_setting 表云端同步 |
| 相机 | CameraX | 1.3+ | 预览、拍照、图像分析 |
| OCR | ML Kit Text Recognition (Chinese) | v2 | bundled 离线模型 |
| 异步 | Coroutines + Flow | — | 所有耗时操作 |
| 图片 | Coil | 2.7 | 凭证图加载 |
| 图表 | Compose Canvas 自绘 | — | **未引入 Vico**：汇总以列表+进度条+自绘柱状呈现，减少依赖与包体 |
| 依赖注入 | 手写 `ViewModelFactory` | — | **未引入 Hilt**：依赖在 `MainActivity` 统一装配，项目规模无需注解处理器 |
| 提醒 | AlarmManager + NotificationCompat | — | **未引入 WorkManager**：精确到分钟的阈值触发用 `setExactAndAllowWhileIdle` |

## 2.2 架构分层

```
┌─────────────────────────────────────────┐
│  UI Layer (Compose, 实际包见 2.3)         │
│  HomeScreen(月历) / TodayScreen(台账) /    │
│  CameraScreen / ConfirmScreen /          │
│  ManualEntryScreen / DayDetailScreen /   │
│  SummaryScreen / HabitScreen /           │
│  WorkRulesScreen / SettingsScreen /      │
│  CustomThemeScreen / MineScreen          │
├─────────────────────────────────────────┤
│  ViewModel Layer                         │
│  各页面 ViewModel（StateFlow 暴露 UI 状态） │
│  依赖通过手写 ViewModelFactory 注入        │
├─────────────────────────────────────────┤
│  Domain Layer                            │
│  ├─ engine/  WorkTimeCalculator（编排）   │
│  │           PunchPairing（多段配对）      │
│  │           PunchTimelineValidator（校验）│
│  ├─ ocr/     OcrFieldExtractor / Matcher │
│  ├─ calculator/ 五维度汇总               │
│  ├─ export/  Csv / Xlsx / Pdf 导出       │
│  ├─ evidence/ 凭证存取与相册写入          │
│  ├─ reminder/ 提醒规划/调度/通知/开机重排  │
│  ├─ backup/  BackupManager               │
│  └─ repository/ PunchEventRepository     │
├─────────────────────────────────────────┤
│  Data Layer                              │
│  ├─ Room Database (3 表 + DAO)           │
│  ├─ DataStore (18 项设置)                │
│  └─ EvidenceStorage (私有目录 + 相册)     │
├─────────────────────────────────────────┤
│  Device Layer                            │
│  CameraX | ML Kit | MediaStore |         │
│  AlarmManager / Notification             │
└─────────────────────────────────────────┘
```

## 2.3 包结构

```
com.worktime.pro/
├── MainActivity.kt          UI 骨架、导航状态、权限申请
├── WorkTimeApplication.kt   全局容器，常驻 ReminderCoordinator
├── ui/
│   ├── ViewModelFactory.kt  手写 ViewModel 工厂
│   ├── LocalProviders.kt    CompositionLocal（主题/设置注入）
│   ├── today/          TodayScreen / TodayViewModel / DayTape（24h 时间带）
│   ├── home/           首页月历 HomeScreen / HomeViewModel
│   ├── camera/         拍照 CameraScreen + 识别确认 ConfirmScreen
│   ├── manual/         手动录入 ManualEntryScreen / ManualViewModel
│   ├── detail/         日详情 DayDetailScreen / DayDetailViewModel
│   ├── summary/        统计汇总 SummaryScreen / SummaryViewModel
│   ├── habit/          习惯打卡 HabitScreen / HabitViewModel
│   ├── rules/          工作规则 WorkRulesScreen / WorkRulesViewModel
│   ├── settings/       设置目录 SettingsScreen + 二级页 SettingsSubScreens +
│   │                   共享组件 SettingsComponents / SettingsViewModel + BackupViewModel
│   ├── mine/           「我的」入口聚合 MineScreen / MineViewModel
│   ├── theme/          Theme.kt / CustomPalette.kt / CustomThemeScreen
│   └── components/     TimePickerDialog（校时器）/ ColorPickerDialog / EvidenceViewer /
│                       WindowAdaptive（窗口档位与内容限宽容器）
├── domain/
│   ├── engine/         WorkTimeCalculator（编排）、PunchPairing（多段配对）、
│   │                   PunchTimelineValidator（写入校验）、OvertimeCalculator、
│   │                   WorkdayClassifier、DateAttributionResolver
│   ├── ocr/            OcrFieldExtractor、StatusMapping（考勤机状态映射）
│   ├── calculator/     SummaryCalculator（五维度汇总）
│   ├── export/         ReportExporter、XlsxWriter、PdfReportWriter
│   ├── evidence/       EvidenceStore（私有目录）、GalleryPublisher（写相册）
│   ├── reminder/       ReminderPlanner / ReminderScheduler / ReminderReceiver /
│   │                   ReminderNotification / ReminderCoordinator / ReminderBootReceiver / ReminderType
│   ├── backup/         BackupManager
│   ├── model/          Models / SummaryModels / DayTotals
│   └── repository/     WorkTimeRepository
├── data/
│   ├── db/             Entities / Daos / Converters / SessionMappers / WorkTimeDatabase
│   └── datastore/      SettingsDataStore / PunchHabit
└── util/               TimeFormatter / Constants / ColorContrast / DateTicker
```

> **导航骨架（`MainActivity`）**：窗口宽度 ≥600dp（平板、折叠展开、手机横屏）把三 Tab 收进左侧 `NavigationRail`，
> 纵向空间还给内容；窄屏保持底部 `NavigationBar`。内层统一套 `AdaptiveContent`，所有路由单列内容限宽 640dp 居中，
> 避免平板上正文行被拉到整屏宽。窗口档位由 `rememberAppWindowSize()` 依
> `screenWidthDp` 判定（COMPACT <600 / MEDIUM 600–839 / EXPANDED ≥840），未引入 `window-size-class` 依赖。

---

# 第三章 任务分解（WBS）

### 完成状态总览（2026-11-20 复核）

| 阶段 | 任务区间 | 状态 |
|------|----------|------|
| 一 基础架构 | K-01 ~ K-04 | 已完成 |
| 二 工时引擎 | K-05 ~ K-09 | 已完成（单测扩展至 60 个用例，含多段与时间线校验） |
| 三 OCR 识别 | K-10 ~ K-18 | 已完成 |
| 四 首页与详情 | K-19 ~ K-26 | 已完成（另新增今日台账与多段展示） |
| 五 汇总设置备份 | K-27 ~ K-32 | 已完成 |
| 六 测试发布 | K-33 ~ K-37 | 已完成至「可发布 Debug APK」；Release 签名待补 |

实现期追加、未在原 WBS 中的任务：

| 任务号 | 任务 | 说明 |
|--------|------|------|
| A-01 | 一日多段打卡（写入口径 + 重算） | 追加式写入，按时间贪心配对 |
| A-02 | 时间线校验器 + 单测 | `PunchTimelineValidator`，四个写入口统一调用 |
| A-03 | 今日台账页 + DayTape 时间带 | 状态卡、时间带、打卡存根、今日流水 |
| A-04 | 累计工时提醒（规划/调度/通知/重排） | 7 个文件，含开机与应用更新重排 |
| A-05 | 通用校时器 TimePickerDialog | 替换 Material TimePicker |
| A-06 | 外观体系（10 套预设 + 自定义 + 字号） | 含 WCAG 对比度提示 |
| A-07 | 习惯打卡与考勤机状态映射 | 对应 PRD 落地增量 |
| A-08 | 凭证写系统相册 GalleryPublisher | 备份仍不含照片 |

## 3.1 阶段一：基础架构（W1）

| 任务号 | 任务 | 工作量（人日） | 依赖 |
|--------|------|---------:|------|
| K-01 | Gradle 工程搭建、Compose 主题、权限申请框架 | 1.5 | — |
| K-02 | Room 数据库（3 实体 + DAO + Migration） | 2 | K-01 |
| K-03 | DataStore 设置封装 | 1 | K-01 |
| K-04 | 常量与工具类（TimeFormatter / Constants） | 0.5 | K-01 |

阶段一小计：**5 人日**

## 3.2 阶段二：工时引擎迁移（W2）

| 任务号 | 任务 | 工作量（人日） | 依赖 |
|--------|------|---------:|------|
| K-05 | WorkTimeCalculator 迁移（编排器） | 1 | K-04 |
| K-06 | OvertimeCalculator 迁移 + 跨天午休修复 + 负值保护 | 2 | K-05 |
| K-07 | WorkdayClassifier / DateAttributionResolver 迁移 | 1 | K-05 |
| K-08 | 四类时间新场景计算逻辑（场景 A/B/C/D） | 1.5 | K-06 |
| K-09 | 引擎单元测试（11 类场景，覆盖率 ≥ 85%） | 2 | K-08 |

阶段二小计：**7.5 人日**

## 3.3 阶段三：OCR 识别链路（W3–W4）

| 任务号 | 任务 | 工作量（人日） | 依赖 |
|--------|------|---------:|------|
| K-10 | CameraX 预览 + 拍照 + 取景框 UI | 2 | K-01 |
| K-11 | 图像预处理（裁剪 / 灰度 / 对比度 / 旋转） | 2 | K-10 |
| K-12 | ML Kit 离线中文识别集成 | 1.5 | K-11 |
| K-13 | PunchTypeMatcher 类型关键词匹配器 | 2 | K-12 |
| K-14 | OcrFieldExtractor 时间/日期抽取 + 坐标配对 | 2 | K-13 |
| K-15 | 置信度分级逻辑（高/中/低） | 1 | K-14 |
| K-16 | 识别结果确认页（原图 + 4 时间 + 类型修正 + 重拍） | 3 | K-15 |
| K-17 | 凭证照片存储（压缩 + 私有目录 + 路径入库） | 1.5 | K-16 |
| K-18 | OCR 抽取规则单元测试（真实考勤机样本） | 2 | K-14 |

阶段三小计：**17 人日**

## 3.4 阶段四：首页与详情（W5–W6）

| 任务号 | 任务 | 工作量（人日） | 依赖 |
|--------|------|---------:|------|
| K-19 | 首页月历组件（网格 + 月份切换 + 今天高亮） | 2.5 | K-02 |
| K-20 | 记录圆点标记 + 加班色阶 | 1.5 | K-19 |
| K-21 | 日历下方时间显示（读取设置，全局统一） | 1.5 | K-03、K-19 |
| K-22 | 首页底部操作栏（拍照识别 / 手动录入） | 0.5 | K-19 |
| K-23 | 日详情页（四类时间 + 来源标识 + 计算明细） | 2 | K-02、K-08 |
| K-24 | 凭证原图查看页 | 1 | K-17、K-23 |
| K-25 | 编辑 / 删除时间点 + 重算触发 | 2 | K-23 |
| K-26 | 手动录入页（类型选择 + 时间选择 + 跨天提示） | 2 | K-02 |

阶段四小计：**13 人日**

## 3.5 阶段五：汇总、设置与备份（W7）

| 任务号 | 任务 | 工作量（人日） | 依赖 |
|--------|------|---------:|------|
| K-27 | 设置页（显示 / 打卡 / 规则 / 数据管理） | 2 | K-03 |
| K-28 | 「日历下方显示内容」配置项 + 全局刷新 | 1 | K-27、K-21 |
| K-29 | 五维度汇总（日/周/月/年/自定义） | 3 | K-02 |
| K-30 | 统计图表（柱状图 / 趋势图） | 2 | K-29 |
| K-31 | 报表导出（CSV / Excel / PDF + 系统分享） | 2 | K-29 |
| K-32 | 数据备份导出 / 导入恢复 | 2 | K-02、K-03 |

阶段五小计：**12 人日**

## 3.6 阶段六：测试与发布（W8）

| 任务号 | 任务 | 工作量（人日） | 依赖 |
|--------|------|---------:|------|
| K-33 | 真机功能测试（多机型） | 2 | 全部 |
| K-34 | OCR 识别准确率调优（样本采集 + 规则迭代） | 2 | K-18 |
| K-35 | 缺陷修复 + 回归测试 | 1.5 | K-33 |
| K-36 | 离线验证（飞行模式全流程） | 0.5 | K-33 |
| K-37 | 签名打包 + 发布准备 | 1 | K-35 |

阶段六小计：**7 人日**

## 3.7 工作量汇总

| 阶段 | 人日 |
|------|------:|
| 一 基础架构 | 5 |
| 二 工时引擎 | 7.5 |
| 三 OCR 识别 | 17 |
| 四 首页与详情 | 13 |
| 五 汇总设置备份 | 12 |
| 六 测试发布 | 7 |
| **合计** | **61.5 人日** |

> 单人开发，按每周 5 个工作日计算，约 8 周（含缓冲）。OCR 阶段工作量最大，是本项目的核心难点。

---

# 第四章 排期计划

| 阶段 | 周期 | 主要内容 | 里程碑 |
|------|------|---------|--------|
| 一 | W1 | 工程搭建、Room、DataStore、工具类 | M1：数据层可用 |
| 二 | W2 | 工时引擎迁移 + 缺陷修复 + 单测 | M2：引擎测试全绿 |
| 三 | W3–W4 | CameraX + ML Kit + 字段抽取 + 确认页 | M3：拍照识别链路打通 |
| 四 | W5–W6 | 月历首页 + 下方显示 + 日详情 + 手动录入 | M4：主界面完成 |
| 五 | W7 | 设置 + 汇总 + 报表 + 备份 | M5：功能冻结 |
| 六 | W8 | 真机测试 + 识别调优 + 打包 | M6：可发布 APK |

> **实际执行**：六个里程碑均已达成，另追加「优化期」（多段打卡、时间线校验、今日台账、工时提醒、
> 校时器、外观体系）并已验收。当前产物为 Debug APK，Release 签名配置未纳入本期范围。

---

# 第五章 关键技术方案要点

## 5.1 OCR 类型与时间配对（核心难点）

```
// 业务规则：考勤机屏幕上类型词与时间通常在同行或紧邻行
// 按 OCR 文字行的 boundingBox 计算垂直距离，≤1.5 倍行高视为可配对
val candidates = textLines
    .filter { matchesTypeKeyword(it.text) || matchesTimePattern(it.text) }
val anchored = candidates.map { line ->
    val type = detectType(line.text)                    // 类型锚点
    val time = extractNearestTime(line, candidates)     // 最近时间
    PunchCandidate(type, time, confidence(type, time))
}
```

置信度规则：
- 高：类型关键词明确 + 时间格式合法 + 同行
- 中：有类型无同行时间，或有时间无类型关键词
- 低：仅"加班"歧义词 + 推测时间段

## 5.2 日历下方显示全局切换

- 设置项存于 DataStore，Key = `calendar_display_mode`
- ViewModel 以 StateFlow 订阅，配置变更时全局刷新所有日期格子的显示
- 不采用每格独立状态（与用户决策一致），避免交互混乱

## 5.3 工时引擎缺陷修复

| 缺陷 | 方案 |
|------|------|
| `calculateRestOverlap` 只比 `LocalTime` | 改用 `LocalDateTime` 区间求交，支持跨天 |
| `overlapEnd < overlapStart` 负时长 | 求交前判空，`max(0, ...)` |
| 480/30 硬编码散落 | 提取 `Constants.STANDARD_MINUTES`、从规则读取缓冲 |

## 5.4 凭证与数据安全

- 照片压缩后存于应用私有目录 `evidence/`，DB 只存路径
- 确认保存时按设置另写一份到系统相册 `Pictures/工时助手`（`MediaStore`，无需写存储权限）
- 删除记录时联动删除私有目录中的照片
- **备份固定不含照片**：体积小、无隐私扩散，恢复后失效路径显示占位图

## 5.5 一日多段配对与写入校验

```
写入链路：UI → WorkTimeRepository.addPunch / addPunches / updatePunch（writeMutex 串行化）
        → PunchTimelineValidator.validate(既有事件 + 新事件)   // 拒绝则返回 Result.failure(中文提示)，不落库
        → insert 追加
        → PunchPairing.pair(当日全部开始卡, 结束卡)   // 贪心：开始卡 + 其后最近的结束卡
        → deleteByDate(work_session) + 重算写回
```

- 校验规则：同类段未闭合不得再开同类段；上班段未闭合不得开加班段，反之亦然；结束必须晚于开始
- 孤立结束卡（只有下班没有上班）不拒绝，作为旧数据/分步补录兼容，不参与配对
- 同一分钟的结束与开始判定为新一段（`start >= end` 视为开放段）
- 未闭合段不写表，仅内存参与「今日实时累计」与提醒计算

## 5.6 工时提醒调度

- `ReminderPlanner` 纯函数：输入今日事件 + 工时段 + 阈值，输出「下一次触发时刻」；若 `lastReminderKey` 已是当日该键则返回 null（同日去重）
- `ReminderScheduler` 用 `AlarmManager.setAndAllowWhileIdle`（`RTC_WAKEUP`）排定 `PendingIntent`，失败仅记录不崩溃
- `ReminderCoordinator` 挂在 Application 作用域，持续观察今日数据并在设置变更时重排——**不依赖页面存活**
- `ReminderReceiver` 收到广播后直接发通知，并把 `日期:类型:阈值` 写入 `lastReminderKey` 完成去重标记
- `ReminderBootReceiver` 处理 `BOOT_COMPLETED` 与 `MY_PACKAGE_REPLACED`，开机与覆盖安装后重排
- 通知渠道 `IMPORTANCE_HIGH`；Android 13+ 缺少 `POST_NOTIFICATIONS` 时静默降级，不抛异常

## 5.7 今日时间带渲染

- 24 小时线性映射到画布宽度，`Canvas` 绘制刻度与段条，避免引入图表库
- 三类图层：计划班次（虚线描边）、已结算段（实心，加班用加班色）、未闭合段（降透明度）
- 当前时刻墨针用 `DateTicker` 分钟级刷新，页面不可见时停止刷新
- 拖动指针即校时：松手回写分钟值，打卡成功后指针回到「现在」

---

# 第六章 风险与应对

| 风险 | 等级 | 影响 | 应对措施 |
|------|------|------|---------|
| OCR 类型识别依赖考勤机屏幕版式 | 高 | 识别错误率高 | 确认页强制人工核对；W3 前采集 20+ 真实考勤机样本验证抽取规则 |
| 单机数据丢失无云端兜底 | 高 | 数据不可恢复 | W7 必须完成备份导出/导入；引导用户定期备份 |
| 跨天 + 午休计算边界复杂 | 中 | 工时错误 | 引擎单测先于 UI 完成，11 类场景全覆盖 |
| ML Kit 模型增大 APK | 低 | 包体积 +10~20MB | 接受，换取完全离线 |
| 拍照反光/模糊导致识别失败 | 中 | 用户体验差 | 取景框引导 + 运动模糊检测 + 重拍提示 |
| 考勤机型号多、版式差异大 | 中 | 抽取规则不通用 | 抽取规则配置化，设置中提供**考勤机状态文字映射**；确认页强制人工核对 |
| 国产 ROM 省电策略杀后台/限制精确闹钟 | 中 | 提醒不按时触发 | 用 `setAndAllowWhileIdle` 而非精确闹钟；开机与更新后重排；引导用户关闭电池优化 |
| 一日多段数据打乱旧口径 | 中 | 工时统计错误 | 写入前统一时间线校验 + 60 个单测覆盖多段/边界/跨天；重算按天全量重来 |
| 备份不含照片导致恢复后凭证缺失 | 低 | 详情页图片打不开 | 明确提示"备份不含照片"；相册另存一份；失效路径显示占位图 |
| 自定义配色可读性差 | 低 | 文字看不清 | WCAG 对比度计算并给出提示，支持一键恢复默认配色 |

---

# 第七章 质量与验收

## 7.1 提测标准

- 工时引擎单测覆盖率 ≥ 85%，11 类场景全部通过
- OCR 抽取规则测试通过（真实考勤机样本）
- 无 P0 / P1 缺陷

**实际执行结果**：7 个单元测试类共 **60 个用例全部通过**（`gradlew :app:testDebugUnitTest`），覆盖工时引擎、
多段配对、时间线校验、工时口径、OCR 抽取、汇总计算、习惯打卡；`assembleDebug` 编译零错误，
仅保留 1 条历史 `menuAnchor` 弃用警告。

## 7.2 上线标准

| 项目 | 标准 |
|------|------|
| OCR 识别耗时 | ≤ 2 秒（中端机型） |
| OCR 类型识别准确率 | ≥ 85%（样本集，人工确认后达 100%） |
| 日历月视图渲染 | ≤ 500ms |
| 汇总查询 | ≤ 300ms |
| 兼容性 | Android 8.0（API 26）+ |
| 离线 | 飞行模式下全流程可用 |

## 7.3 功能验收清单

对应 PRD_V2.md 第五章，逐项验收（此处从略，详见 PRD_V2.md）。

验收补充（优化期增量，均已通过真机验证）：

- 多段打卡：同日两段上下班正确配对，正常工时合计正确
- 时间线校验：段未闭合、上班与加班交叉、结束早于开始三类冲突均被拒绝且有中文提示
- 今日台账：时间带三层图层正确区分计划/已结算/进行中，墨针每分钟移动
- 工时提醒：阈值达成后收到通知，同日不重复；关闭开关后取消排定；开机后仍可触发
- 校时器：拖动校准精度到分钟，取消不改值，12/24 小时显示跟随设置
- 外观：10 套预设深浅两版、自定义配色低对比度提示、字号 0.9/1.0/1.15 无截断
- 备份：导出 JSON 不含照片，导入后数据一致且 `lastReminderKey` 被重置
- 设置信息架构（V2.3）：目录页两分区 5 入口摘要正确；子页 / 三级页与系统返回键逐级回退；状态映射独立成页后 0–9 十行直接铺开
- 多尺寸适配（V2.3）：360×640、320×568（系统字体 1.3x）、640×360 横屏、800×1280 平板四档逐页 `uiautomator dump` 无越界；≥600dp 出现左侧导航，内容按 640dp 限宽居中

---

# 第八章 交付物清单

| 类别 | 交付物 | 阶段 | 状态 |
|------|--------|------|------|
| 文档 | PRD_V2.md、DevelopmentPlan_V2.md、DatabaseDesign_V2.md、OptimizationPlan_V3.md、README.md | W1 / 优化期 | 已交付 |
| 设计 | Compose UI（今日台账 / 月历 / 确认页 / 详情页 / 汇总 / 设置 / 外观） | W1 / 优化期 | 已交付 |
| 代码 | Kotlin 单机 App 完整源码（`worktime-android/`，71 个 Kotlin 源文件） | 全周期 | 已交付 |
| 测试 | 7 个单测类 / 60 个用例；OCR 抽取测试 | W8 | 已交付 |
| 成果 | Debug APK：`app/build/outputs/apk/debug/app-debug.apk`（可直接安装） | W8 | 已交付 |
| 待办 | Release 签名配置与正式包分发 | 后续 | 未开始 |
| 归档 | V1.1 全部文档与源码保留 | — | 已保留 |

---

**文档编制完成**
