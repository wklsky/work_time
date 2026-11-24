# WorkTime Pro · 智能工时与加班管理系统（单机版）

[![Version](https://img.shields.io/badge/version-2.5.1-blue)](https://github.com)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.0+-purple)](https://kotlinlang.org/)
[![Jetpack Compose](https://img.shields.io/badge/Compose-Material3-green)](https://developer.android.com/jetpack/compose)

面向个人的**离线**工时与加班记录工具。手动录入上班、下班、加班到、加班退四类时间，自动计算工时，以今日台账与本月日历为核心视图查看与分析。

**零后端、零服务器，数据完全留在手机本地。** 联网仅用于检查应用更新。

> 最近更新：2026-11-24 · 新增提醒方式三选项：声音 + 震动 / 仅震动 / 跟随系统，
> 会议与夜间可完全静音提醒

---

## 项目结构

```
f:/MyApplication/work_time/
├── README.md                       # 本文件（构建 / 发布 / 更新机制）
├── DESIGN.md                       # 项目设计文档（架构 / 数据模型 / 引擎 / 界面设计）
├── UserGuide.md                    # 应用使用文档（全部功能操作说明）
└── worktime-android/               # Kotlin 单机版 Android 工程
    └── app/src/
        ├── main/java/com/worktime/pro/
        │   ├── MainActivity.kt          # 导航骨架（底部/侧边自适应）+ 子页面路由 + 依赖注入
        │   ├── WorkTimeApplication.kt   # 全局容器，常驻提醒协调器
        │   ├── domain/
        │   │   ├── engine/              # 工时引擎、多段配对、时间线校验
        │   │   ├── reminder/            # 累计工时提醒（规划/调度/通知/响铃服务/开机重排）
        │   │   ├── repository/          # 事件聚合与重算入口
        │   │   ├── calculator/          # 五维度汇总计算
        │   │   ├── export/              # CSV / XLSX / PDF 导出
        │   │   ├── backup/              # 本地 JSON 备份与恢复
        │   │   └── model/               # 领域模型
        │   ├── data/db/                 # Room 实体 / DAO / 数据库
        │   ├── data/datastore/          # DataStore 设置与打卡习惯
        │   ├── ui/                      # Compose 界面（见下）
        │   └── util/                    # 时间格式、对比度、常量、日期计时器
        └── test/                        # 单元测试（9 个测试类 / 66 个用例）
```

UI 包：`today`（今日台账+时间带）、`home`（月历）、`manual`（手动录入）、
`detail`（日详情）、`summary`（统计汇总）、`habit`（习惯打卡）、`rules`（工作规则）、
`settings`（设置目录 + 4 个二级页 + 备份）、`theme`（主题与自定义配色）、
`components`（校时器/盖章确认/日期选择/窗口自适应）、`mine`（我的）。

> 旧版云端架构（Vue 前端 + Spring Boot 后端）与拍照 OCR 识别功能均已移除。当前功能：数据层、工时引擎、
> 手动录入、今日台账、日历、统计汇总、报表导出、工时提醒、数据备份。

## 构建与测试

```bash
cd worktime-android

# 运行单元测试（工时引擎、多段配对、时间线校验、口径、表盘样式、汇总、习惯、版本比较，共 66 项）
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

应用通过多源自查更新（`wklsky/work_time`，公开仓库）：优先读 GitHub Release API，失败后走 jsDelivr CDN 与代理 raw 读取仓库内 `update/update.json` 清单；APK 下载在 GitHub 直链失败后自动切换加速代理重试，全部失败再引导浏览器下载。发布后老版本用户下次启动即收到提示。

> 国内手机直连 GitHub 不稳定，多源降级是为此设计的：检查与下载任一环节失败都会自动换下一个源，用户无感。

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
2. 同步更新仓库根目录 `update/update.json`（versionName / notes / apkUrl 指向本次 tag 的下载直链），
   随代码一起提交推送——这是 jsDelivr 镜像源的数据来源，漏更新会导致镜像源查不到新版本
3. Releases → Draft a new release，tag 填 `v2.1.1`（必须与 `versionName` 一致；大小写 `V` 也兼容）
4. 上传产物并命名为 `WorkTime.apk`——客户端按 `UpdateConfig.APK_ASSET_NAME` 精确匹配，
   改资产名就必须同步改该常量，否则 `releases/latest/download/` 固定直链兜底会 404
5. 点 Publish release（草稿状态与预发布态 API 都查不到，务必勾选 latest）

### 4. 更新机制

| 场景 | 行为 |
|------|------|
| 冷启动 | 24 小时内最多查一次；有新版本弹窗，已是最新或请求失败均静默 |
| 我的 → 检查更新 | 跳过节流，明确提示「已是最新」或失败原因 |
| 检查数据源 | Release API → jsDelivr 清单 → 代理 raw 清单，依次降级，任一源成功即返回 |
| 下载 | 系统 `DownloadManager` 下载到应用私有目录，通知栏可见进度，断点续传；失败自动换加速代理重试 |
| 安装 | 首次需授权「安装未知应用」，之后下载完成自动拉起系统安装器 |

### 5. 镜像源维护

- `update/update.json` 经 jsDelivr 分发有最长约 12 小时缓存，发版推送后新清单最多延迟半天生效；
  可访问 `https://purge.jsdelivr.net/gh/wklsky/work_time@main/update/update.json` 手动刷新
- APK 加速代理域名（`UpdateConfig.APK_PROXY_PREFIXES`）为第三方公益服务，可能随时间失效，
  失效表现为下载自动重试后仍失败；发现全部不可用时更新该列表并随下个版本发布
- 未认证的 GitHub API 按 IP 限流 60 次/小时，共享出口 IP 下可能返回 403，已做换源降级

---

## 技术栈（V2.0）

| 层次 | 技术 | 说明 |
|------|------|------|
| 语言 | Kotlin 2.0+ | 协程 + Flow |
| UI | Jetpack Compose + Material3 | 声明式 UI，`material-icons-extended` |
| 导航 | Navigation Compose 2.8 | 三 Tab + 子页面路由 |
| 架构 | MVVM + 单向数据流 | ViewModel + StateFlow，手写 `ViewModelFactory` 注入 |
| 本地库 | Room 2.6.1（KSP） | 3 张表持久化打卡、工时段与规则，v2 迁移清理 OCR 残留字段 |
| 设置 | DataStore Preferences 1.1 | 本地偏好（含表盘刻度样式等） |
| 图表 | Compose Canvas 自绘 | 未引入第三方图表库，包体更小 |
| 提醒 | AlarmManager（setAlarmClock）+ Notification + 前台服务 | 精确闹钟准点唤醒，响铃走闹钟音频流；无 WorkManager 依赖，开机/更新/时间变更后自动重排；停止响铃走广播兜底 |
| 更新 | GitHub Releases API + DownloadManager | 原生 `HttpURLConnection` + `org.json`，未新增网络库 |

---

## 核心功能

**记录**
- **手动录入**：打卡类型分格 + 打卡时刻大读数，点击读数进入「校时」表盘选择
- **打卡盖章**：打卡成功后弹出朱红「盖章留痕」确认动画，直观反馈写入结果
- **多段打卡**：同一天可多次上班/下班、加班到/加班退，按时间自动配对成多段
- **默认打卡动作**：今日页上班与加班到两种起始动作都开放时按设置预选，只剩唯一下一步（下班/加班退）时以状态为准
- **时间线校验**：上班与加班不可重叠，同类段闭合后才能开始下一段，冲突写入直接拒绝并提示
- **校时器**：12 小时圆形表盘（上午/下午区分），拖动时针/分针校准，刻度数字样式可选数字 / 罗马数字 / 条丁刻度，全应用统一复用

**查看与分析**
- **今日台账**：状态与实时累计 + 24 小时时间带（计划班次/已记录段/当前时刻指针）+ 打卡时刻存根 + 今日流水
- **删除撤销**：删除后 Snackbar 可撤销，完整加班段整段删除并整段还原
- **本月日历**：一次查询聚合整月，时长标签 + 加班色阶 + 缺卡提示，左右滑动切换月份，格子高度随窗口自适应
- **日历时间显示**：每日下方显示上班时间或加班时间，在设置中**全局统一切换**
- **日详情页**：加班段归组展示 + 工时计算明细 + 补录一段；加班段整段编辑/删除，删除前二次确认
- **工时计算**：四类时间计算引擎（含跨天、午休、一日多段），正常与加班口径互不重叠
- **统计汇总**：日 / 星期 / 月 / 年 / 自定义 五维度本地汇总，自定义区间在弹层内就地编辑
- **报表导出**：CSV / 标准 XLSX / PDF，按格式设置 MIME
- **习惯打卡**：自定义固定班次习惯，在日详情页一键写入当天，已有记录时确认追加/覆盖

**提醒与规则**
- **工时提醒**：按加班 / 正常工时 / 净投入设定累计阈值（如加班满 7 小时），达到后发送系统通知；
  可选**响铃提醒**，持续提醒最多 60 秒、通知内可停止；响铃方式三选一：
  **声音 + 震动** / **仅震动**（完全静音，适合会议与夜间）/ **跟随系统**（静音或震动模式下只震动），
  铃声支持从本地音频文件中选择；
  退到后台由系统精确闹钟触发，开机/应用更新/系统时间变更后自动重排，同一天同一阈值不重复通知；
  提醒页内置**可靠性自检**（通知权限 / 电池优化 / 精确闹钟 / 自启动白名单 / 测试响铃），
  引导用户放行国内 ROM 的后台清理限制
- **工作规则**：上下班、午休、加班缓冲、日切时间、工作日可配置，支持按新规则重算历史

**外观与数据**
- **外观**：浅色/深色/黑白 + 10 套预设配色 + 自定义配色（品牌/工时/加班/背景/卡片/文字）+ 字号缩放
- **设置目录**：「我的 → 设置」为目录页，分 4 个入口（打卡设置 / 显示与外观 / 提醒 / 数据管理），
  每行直接显示当前值摘要，子页与三级页返回逐级回退
- **数据备份**：本地 JSON 导出 / 导入，换机/重装后行为一致

**界面与适配**
- **导航自适应**：窗口宽 ≥600dp（平板、折叠展开、手机横屏）自动把三 Tab 收进左侧侧边导航，窄屏保持底部导航
- **内容限宽**：全部页面单列内容限宽 640dp 居中，平板与横屏下不把正文拉成整屏宽
- **首页日历多尺寸**：日历占满页面剩余空间，格子高度按可用高度与字体缩放自适应；
  宽屏切换「信息栏 + 日历」双栏，横屏/平板下 6 行月历完整显示
- **极端尺寸**：320dp 极窄屏叠加系统字体 1.3x 仍不截断、不越界；页面统一消费系统栏 insets，全面屏与手势导航不遮挡内容

**更新**
- **自动检查**：冷启动 24 小时内查一次 GitHub Release，按 tag 与 `versionName` 比对，有新版本才弹窗
- **下载安装**：`DownloadManager` 后台下载并可继续使用应用，完成后自动拉起系统安装器
- **手动检查**：「我的 → 检查更新」跳过节流，明确提示已是最新或失败原因

---

## 权限说明

| 权限 | 用途 | 备注 |
|------|------|------|
| `POST_NOTIFICATIONS`（API 33+） | 发送工时提醒 | 开启提醒时申请，拒绝则提示且不阻塞其他功能 |
| `RECEIVE_BOOT_COMPLETED` | 开机后重排提醒闹钟 | 仅做提醒重排，不后台自启业务 |
| `FOREGROUND_SERVICE` / `FOREGROUND_SERVICE_MEDIA_PLAYBACK` | 响铃提醒时在后台播放闹钟铃声 | 仅响铃期间运行，最多 60 秒，通知内可停止 |
| `VIBRATE` / `WAKE_LOCK` | 响铃时震动并保证熄屏下持续响铃 | 仅响铃期间生效 |
| `USE_EXACT_ALARM`（API 33+）/ `SCHEDULE_EXACT_ALARM`（API 31-32） | 准点触发提醒 | 使用系统闹钟级精确闹钟，仅用于用户开启的工时提醒 |
| `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` | 引导用户允许后台运行 | 仅在提醒设置页由用户主动点击时申请，避免清理后台后闹钟失效 |
| `INTERNET` | 读取 GitHub Release 版本信息、下载更新包 | 仅更新检查使用，不上传任何数据 |
| `REQUEST_INSTALL_PACKAGES` | 安装已下载的更新包 | Android 8.0+ 需用户先允许「安装未知应用」 |
| `ACCESS_NETWORK_STATE` | 更新下载的网络状态判断 | 配合 DownloadManager 使用 |

**除更新检查外全流程离线**：联网只用于读取版本号与下载安装包，打卡记录、规则配置均不出本机。

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

- [项目设计文档](DESIGN.md) — 产品定位、技术架构、数据模型、核心引擎、界面设计、适配策略
- [应用使用文档](UserGuide.md) — 全部功能的操作说明与常见问题

---

## 许可证

内部项目，仅供学习与个人使用。
