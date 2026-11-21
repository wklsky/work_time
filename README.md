# WorkTime Pro · 智能工时与加班管理系统（单机版）

[![Version](https://img.shields.io/badge/version-2.0.0-blue)](https://github.com)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.0+-purple)](https://kotlinlang.org/)
[![Jetpack Compose](https://img.shields.io/badge/Compose-Material3-green)](https://developer.android.com/jetpack/compose)

面向个人的**离线**工时与加班记录工具。拍摄考勤机屏幕，本地 OCR 自动识别上班、下班、加班到、加班退四类时间，自动计算工时，以今日台账与本月日历为核心视图查看与分析。

**零后端、零服务器、零网络请求（未申请 INTERNET 权限），数据完全留在手机本地。**

> 最近更新：2026-11-21 · 设置改为多级目录、补齐多尺寸屏幕适配、新增 GitHub Release 自动更新 · 单元测试 74 项全绿

---

## 项目结构

```
f:/MyApplication/work_time/
├── README.md                       # 本文件
├── PRD_V2.md                       # 产品需求文档 V2.0（含落地增量）
├── DevelopmentPlan_V2.md           # 开发计划书 V2.0（含完成状态）
├── DatabaseDesign_V2.md            # 数据库设计文档 V2.0
├── OptimizationPlan_V3.md          # V3.0 优化升级方案（缺陷审计 / 体验升级 / 设计规范）
└── worktime-android/               # Kotlin 单机版 Android 工程
    └── app/src/
        ├── main/java/com/worktime/pro/
        │   ├── MainActivity.kt          # 导航骨架（底部/侧边自适应）+ 子页面路由 + 依赖注入
        │   ├── WorkTimeApplication.kt   # 全局容器，常驻提醒协调器
        │   ├── domain/
        │   │   ├── engine/              # 工时引擎、多段配对、时间线校验
        │   │   ├── ocr/                 # OCR 字段抽取与状态映射
        │   │   ├── reminder/            # 累计工时提醒（规划/调度/通知/开机重排）
        │   │   ├── repository/          # 事件聚合与重算入口
        │   │   ├── calculator/          # 五维度汇总计算
        │   │   ├── export/              # CSV / XLSX / PDF 导出
        │   │   ├── evidence/            # 凭证存取与相册写入
        │   │   ├── backup/              # 本地 JSON 备份与恢复
        │   │   └── model/               # 领域模型
        │   ├── data/db/                 # Room 实体 / DAO / 数据库
        │   ├── data/datastore/          # DataStore 设置与打卡习惯
        │   ├── ui/                      # Compose 界面（见下）
        │   └── util/                    # 时间格式、对比度、常量、日期计时器
        └── test/                        # 单元测试（7 个测试类 / 60 个用例）
```

UI 包：`today`（今日台账+时间带）、`home`（月历）、`camera`（拍照+确认）、`manual`（手动录入）、
`detail`（日详情）、`summary`（统计汇总）、`habit`（习惯打卡）、`rules`（工作规则）、
`settings`（设置目录 + 5 个二级页 + 备份）、`theme`（主题与自定义配色）、
`components`（校时器/取色器/凭证查看器/窗口自适应）、`mine`（我的）。

> 旧版云端架构（Vue 前端 + Spring Boot 后端）已移除。V2.0 全部功能已落地：数据层、工时引擎、OCR 识别、
> 今日台账、日历、统计汇总、报表导出、凭证与相册、工时提醒、数据备份。

## 构建与测试

```bash
cd worktime-android

# 运行单元测试（工时引擎、多段配对、时间线校验、口径、OCR、汇总、习惯、版本比较，共 74 项）
gradlew.bat :app:testDebugUnitTest

# 构建 Debug APK（输出：app/build/outputs/apk/debug/工时助手.apk）
gradlew.bat :app:assembleDebug

# 编译并安装到已连接的设备
gradlew.bat :app:installDebug

# 清理构建缓存后全量重编
gradlew.bat clean :app:assembleDebug
```

环境要求：JDK 17、Android SDK Platform 35 / Build-Tools 35、Gradle 9.x（已含 Wrapper）。

## 安装

```bash
# 安装/覆盖安装
adb install -r worktime-android/app/build/outputs/apk/debug/工时助手.apk

# 启动
adb shell am start -n com.worktime.pro/.MainActivity
```

- Debug 包使用自动生成的调试签名，可直接安装；`applicationId` 为 `com.worktime.pro`。
- Debug 与 Release 签名不同，**不能互相覆盖**，切换时需先卸载。

## 发布与自动更新

应用通过 GitHub Release 自查更新（`wklsky/work_time`，公开仓库），发布后老版本用户下次启动即收到提示。

### 1. 生成发布密钥（仅首次）

密钥丢失意味着已装用户永远无法升级，务必离线备份：

```bash
keytool -genkeypair -v -keystore keystore/worktime-release.jks ^
  -alias worktime -keyalg RSA -keysize 2048 -validity 10000
```

在项目根目录建 `keystore.properties`（已在 `.gitignore` 中，不会入库）：

```properties
storeFile=keystore/worktime-release.jks
storePassword=你的口令
keyAlias=worktime
keyPassword=你的口令
```

没有该文件时 `assembleRelease` 回落 debug 签名，仅够本地跑通流程。

### 2. 打 Release 包

```bash
gradlew.bat :app:assembleRelease
# 产物：app/build/outputs/apk/release/app-release.apk
```

### 3. 发布到 GitHub

1. 先改 `versionCode`（每次 +1）与 `versionName`（如 2.1.1）
2. Releases → Draft a new release，tag 填 `v2.1.1`（必须与 `versionName` 一致；大小写 `V` 也兼容）
3. 上传产物并命名为 `WorkTime.apk`——客户端按 `UpdateConfig.APK_ASSET_NAME` 精确匹配，
   改资产名就必须同步改该常量，否则 `releases/latest/download/` 固定直链兜底会 404
4. 点 Publish release（草稿状态与预发布态 API 都查不到，务必勾选 latest）

### 4. 更新机制

| 场景 | 行为 |
|------|------|
| 冷启动 | 24 小时内最多查一次；有新版本弹窗，已是最新或请求失败均静默 |
| 我的 → 检查更新 | 跳过节流，明确提示「已是最新」或失败原因 |
| 下载 | 系统 `DownloadManager` 下载到应用私有目录，通知栏可见进度，断点续传 |
| 安装 | 首次需授权「安装未知应用」，之后下载完成自动拉起系统安装器 |

> 未认证的 GitHub API 按 IP 限流 60 次/小时，共享出口 IP 下可能返回 403，已做静默降级。部分网络环境直连 GitHub 不稳定时，检查更新会提示失败但不影响其他功能。

---

## 技术栈（V2.0）

| 层次 | 技术 | 说明 |
|------|------|------|
| 语言 | Kotlin 2.0+ | 协程 + Flow |
| UI | Jetpack Compose + Material3 | 声明式 UI，`material-icons-extended` |
| 导航 | Navigation Compose 2.8 | 三 Tab + 子页面路由 |
| 架构 | MVVM + 单向数据流 | ViewModel + StateFlow，手写 `ViewModelFactory` 注入 |
| 本地库 | Room 2.6.1（KSP） | 3 张表持久化打卡、工时段与规则 |
| 设置 | DataStore Preferences 1.1 | 18 项本地偏好 |
| 相机 | CameraX 1.3.4 | 预览、拍照、图像分析 |
| OCR | ML Kit Text Recognition (Chinese) 16.0.1 | bundled 离线中文识别 |
| 图片 | Coil 2.7 | 凭证图加载 |
| 图表 | Compose Canvas 自绘 | 未引入第三方图表库，包体更小 |
| 提醒 | AlarmManager + Notification | 无 WorkManager 依赖，开机/更新后自动重排 |
| 更新 | GitHub Releases API + DownloadManager | 原生 `HttpURLConnection` + `org.json`，未新增网络库 |

---

## 核心功能

**记录**
- **拍照识别**：拍摄考勤机屏幕，离线 OCR 识别四类时间 + 类型，确认页人工核对后入库
- **手动录入**：打卡类型分格 + 打卡时刻大读数，点击读数进入「校时」表盘选择
- **多段打卡**：同一天可多次上班/下班、加班到/加班退，按时间自动配对成多段
- **默认打卡动作**：今日页上班与加班到两种起始动作都开放时按设置预选，只剩唯一下一步（下班/加班退）时以状态为准
- **时间线校验**：上班与加班不可重叠，同类段闭合后才能开始下一段，冲突写入直接拒绝并提示
- **校时器**：24 小时圆形表盘，拖动时针/分针校准，跟随 12/24 小时设置，全应用统一复用

**查看与分析**
- **今日台账**：状态与实时累计 + 24 小时时间带（计划班次/已记录段/当前时刻指针）+ 打卡时刻存根 + 今日流水
- **本月日历**：一次查询聚合整月，时长标签 + 加班色阶 + 缺卡提示，左右滑动切换月份
- **日历时间显示**：每日下方显示上班时间或加班时间，在设置中**全局统一切换**
- **日详情页**：按时间展示多段打卡 + 凭证原图全屏查看 + 工时计算明细，删除前二次确认
- **工时计算**：四类时间计算引擎（含跨天、午休、一日多段），正常与加班口径互不重叠
- **统计汇总**：日 / 星期 / 月 / 年 / 自定义 五维度本地汇总，自定义区间在弹层内就地编辑
- **报表导出**：CSV / 标准 XLSX / PDF，按格式设置 MIME
- **习惯打卡**：自定义习惯列表与打卡记录

**提醒与规则**
- **工时提醒**：按加班 / 正常工时 / 净投入设定累计阈值（如加班满 7 小时），达到后发送系统通知；
  退到后台由系统闹钟触发，开机/应用更新后自动重排，同一天同一阈值不重复通知
- **工作规则**：上下班、午休、加班缓冲、日切时间、工作日可配置，支持按新规则重算历史
- **考勤机状态映射**：自定义状态文字到打卡类型的映射，适配不同品牌考勤机

**外观与数据**
- **外观**：浅色/深色/黑白 + 10 套预设配色 + 自定义配色（品牌/工时/加班/背景/卡片/文字）+ 字号缩放
- **设置目录**：「我的 → 设置」为目录页，分 5 个入口（打卡与识别 / 显示与外观 / 提醒 / 考勤机状态映射 / 数据管理），
  每行直接显示当前值摘要，子页与三级页返回逐级回退
- **凭证与相册**：确认保存后可写入系统相册 `Pictures/工时助手`，设置可关闭
- **数据备份**：本地 JSON 导出 / 导入，**不含照片**；照片只进相册与应用私有目录

**界面与适配**
- **导航自适应**：窗口宽 ≥600dp（平板、折叠展开、手机横屏）自动把三 Tab 收进左侧侧边导航，窄屏保持底部导航
- **内容限宽**：全部页面单列内容限宽 640dp 居中，平板与横屏下不把正文拉成整屏宽
- **极端尺寸**：320dp 极窄屏叠加系统字体 1.3x 仍不截断、不越界；页面统一消费系统栏 insets，全面屏与手势导航不遮挡内容

**更新**
- **自动检查**：冷启动 24 小时内查一次 GitHub Release，按 tag 与 `versionName` 比对，有新版本才弹窗
- **下载安装**：`DownloadManager` 后台下载并可继续使用应用，完成后自动拉起系统安装器
- **手动检查**：「我的 → 检查更新」跳过节流，明确提示已是最新或失败原因

---

## 权限说明

| 权限 | 用途 | 备注 |
|------|------|------|
| `CAMERA` | 拍摄考勤机屏幕 | 拒绝后仍可使用手动录入 |
| `READ_MEDIA_IMAGES`（API 33+）/ `READ_EXTERNAL_STORAGE`（≤32） | 读取相册图片备用 | 不申请写外部存储权限 |
| `POST_NOTIFICATIONS`（API 33+） | 发送工时提醒 | 开启提醒时申请，拒绝则提示且不阻塞其他功能 |
| `RECEIVE_BOOT_COMPLETED` | 开机后重排提醒闹钟 | 仅做提醒重排，不后台自启业务 |
| `INTERNET` | 读取 GitHub Release 版本信息、下载更新包 | 仅更新检查使用，不上传任何数据 |
| `REQUEST_INSTALL_PACKAGES` | 安装已下载的更新包 | Android 8.0+ 需用户先允许「安装未知应用」 |

**除更新检查外全流程离线**：联网只用于读取版本号与下载安装包，打卡记录、照片、规则配置均不出本机。

---

## 环境要求

| 组件 | 最低版本 |
|------|---------|
| Android Studio | Ladybug 或更高 |
| JDK | 17 |
| Android SDK | API 26（Android 8.0）+，compileSdk 35 |
| Kotlin | 2.0+ |

---

## 文档索引

- [产品需求文档 V2.0](PRD_V2.md) — 功能范围、OCR 抽取规则、验收标准（含 V2.1/V2.2 落地增量）
- [开发计划书 V2.0](DevelopmentPlan_V2.md) — 技术架构、WBS 完成状态、关键技术方案、风险
- [数据库设计文档 V2.0](DatabaseDesign_V2.md) — 点段分离模型、DAO、设置项、备份格式
- [优化升级方案 V3.0](OptimizationPlan_V3.md) — 代码体检缺陷审计、功能升级、设计规范与验收

---

## 许可证

内部项目，仅供学习与个人使用。
