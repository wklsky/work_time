# WorkTime Pro · 智能工时与加班管理系统（单机版）

[![Version](https://img.shields.io/badge/version-2.0.0-blue)](https://github.com)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.0+-purple)](https://kotlinlang.org/)
[![Jetpack Compose](https://img.shields.io/badge/Compose-Material3-green)](https://developer.android.com/jetpack/compose)

面向个人的**离线**工时与加班记录工具。拍摄考勤机屏幕，本地 OCR 自动识别上班、下班、加班到、加班退四类时间，自动计算工时，以本月日历为核心视图进行查看与分析。

**零后端、零服务器、零网络请求，数据完全留在手机本地。**

---

## 项目结构

```
f:/MyApplication/work_time/
├── README.md                       # 本文件
├── PRD_V2.md                       # 产品需求文档 V2.0
├── DevelopmentPlan_V2.md           # 开发计划书 V2.0
├── DatabaseDesign_V2.md            # 数据库设计文档 V2.0
└── worktime-android/               # Kotlin 单机版 Android 工程
    └── app/src/
        ├── main/java/com/worktime/pro/
        │   ├── domain/engine/       # 工时计算引擎（迁移自旧版）
        │   ├── domain/ocr/          # OCR 字段抽取器
        │   ├── domain/repository/   # 事件聚合与重算
        │   ├── data/db/             # Room 实体/DAO/数据库
        │   ├── data/datastore/      # 本地设置
        │   └── ui/                  # Compose 界面（首页/拍照/确认/详情/设置）
        └── test/                    # 单元测试（50 个用例）
```

> 旧版云端架构（Vue 前端 + Spring Boot 后端）已移除。V2.0 核心功能已完成：数据层、工时引擎、OCR 识别、日历界面、统计汇总、报表导出、数据备份。

## 构建与测试

```bash
cd worktime-android

# 运行单元测试（工时引擎、配对、口径、OCR、汇总、习惯，共 50 项）
gradlew.bat :app:testDebugUnitTest

# 构建 Debug APK（输出：app/build/outputs/apk/debug/app-debug.apk）
gradlew.bat :app:assembleDebug
```

环境要求：JDK 17、Android SDK Platform 35 / Build-Tools 35、Gradle 9.x（已含 Wrapper）。

---

## 技术栈（V2.0）

| 层次 | 技术 | 说明 |
|------|------|------|
| 语言 | Kotlin 2.0+ | 协程 + Flow |
| UI | Jetpack Compose + Material3 | 声明式 UI |
| 架构 | MVVM + 单向数据流 | ViewModel + StateFlow |
| 本地库 | Room (SQLite) | 持久化打卡数据 |
| 设置 | DataStore | 本地偏好设置 |
| 相机 | CameraX | 预览、拍照 |
| OCR | ML Kit Text Recognition (Chinese, bundled) | 完全离线中文识别 |
| 图表 | Vico / Compose Charts | 统计可视化 |

---

## 核心功能

- **拍照识别**：拍摄考勤机屏幕，离线 OCR 识别四类时间 + 类型，确认页人工核对后入库
- **本月日历**：一次查询聚合整月，时长标签 + 加班色阶 + 缺卡提示，左右滑动切换月份
- **日历时间显示**：每日下方显示上班时间或加班时间，在设置中**全局统一切换**
- **日详情页**：四类时间 + 凭证原图全屏查看 + 工时计算明细，删除前二次确认
- **工时计算**：四类时间计算引擎（含跨天、午休、一日多段），正常与加班口径互不重叠
- **工作规则**：上下班、午休、加班缓冲、日切时间、工作日可配置，支持按新规则重算历史
- **统计汇总**：日 / 周 / 月 / 年 / 自定义 五维度本地汇总，自定义区间可选日期
- **报表导出**：CSV / 标准 XLSX / PDF
- **凭证与相册**：确认保存后可写入系统相册 `Pictures/工时助手`，设置可关闭
- **外观**：四种主题 + 自定义配色（品牌/工时/加班/背景/卡片/文字）+ 字号缩放
- **数据备份**：本地 JSON 导出 / 导入，**不含照片**；照片只进相册与应用私有目录

---

## 环境要求

| 组件 | 最低版本 |
|------|---------|
| Android Studio | Ladybug 或更高 |
| JDK | 17 |
| Android SDK | API 26（Android 8.0）+ |
| Kotlin | 2.0+ |

---

## 文档索引

- [产品需求文档 V2.0](PRD_V2.md) — 功能范围、OCR 抽取规则、验收标准
- [开发计划书 V2.0](DevelopmentPlan_V2.md) — 技术架构、WBS、排期、风险
- [数据库设计文档 V2.0](DatabaseDesign_V2.md) — 点段分离模型、DAO、备份格式

---

## 许可证

内部项目，仅供学习与个人使用。
