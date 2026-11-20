# WorkTime Pro 优化升级方案 V3.0（待审核）

> 状态：草案 · 待审核后实施
> 编写日期：2026-11-20
> 代码基线：`main @ 71120d8` + 工作区未提交改动（习惯打卡、备份 CSV、日详情编辑、主题模式、今日快速打卡等）
> 目标：修复已承诺但未落地的功能，消除数据口径错误，并把「工时台账」视觉语言做成一套完整、克制、可扩展的界面体系。

---

## 一、代码体检结论

### 1.1 总体判断

架构分层清晰（UI / ViewModel / Repository / Engine / Room），离线约束执行到位，工时引擎与 OCR 抽取器有单测（38 项），这是很好的基础。当前问题集中在四类：

1. **数据口径错误**：正常工时与加班时长存在重复计数，首页、今日、详情、统计、CSV 全部受影响。
2. **PRD 已承诺但未实现**：工作规则设置、月份滑动、加班色阶、自定义统计区间、PDF 报表、凭证查看、备份含照片。
3. **设置项未闭环**：`showOvertimeShade`、`ocrAutoFill`、`backupIncludePhotos`、`defaultInputMode` 四个开关存了值但没有读取端。
4. **性能与生命周期**：月历 42 个格子各自建 Flow 查库；跨零点日期不刷新；OCR 存图与备份读取在主线程。

### 1.2 缺陷清单（按严重度）

| 编号 | 严重度 | 问题 | 证据 | 影响 |
|------|--------|------|------|------|
| F-01 | **S0 数据** | 正常工时与加班重复计数：引擎 `workMinutes` 是净投入，`overtimeMinutes` 是其子集；UI 却把两者相加展示。周末更明显：`calculateWeekend` 同时返回 work=净时长、overtime=净时长，被当作「工作 8h + 加班 8h」 | [OvertimeCalculator.kt:46-56](worktime-android/app/src/main/java/com/worktime/pro/domain/engine/OvertimeCalculator.kt#L46-L56)、[TodayScreen.kt:96-98](worktime-android/app/src/main/java/com/worktime/pro/ui/today/TodayScreen.kt#L96-L98)、[SummaryViewModel.kt:80-85](worktime-android/app/src/main/java/com/worktime/pro/ui/summary/SummaryViewModel.kt#L80-L85)、[BackupManager.kt:115-117](worktime-android/app/src/main/java/com/worktime/pro/domain/backup/BackupManager.kt#L115-L117) | 9:00–20:00 显示「工作 10h + 加班 0.5h」，实际净投入 10h；月统计虚高 |
| F-02 | **S0 数据** | 一天需要支持多段打卡，但重算按 `starts[index] ↔ ends[index]` 配对，且 UI 只展示每种类型的第一条 | [TodayViewModel.kt:54-66](worktime-android/app/src/main/java/com/worktime/pro/ui/today/TodayViewModel.kt#L54-L66)、[WorkTimeRepository.kt:155-177](worktime-android/app/src/main/java/com/worktime/pro/domain/repository/WorkTimeRepository.kt#L155-L177) | 多段班次错配，第二段及之后的打卡无法查看/编辑 |
| F-03 | **S1 功能** | 工作规则无法配置：实体、DAO、默认数据都在，但没有任何写入口 | [Entities.kt:69-82](worktime-android/app/src/main/java/com/worktime/pro/data/db/Entities.kt#L69-L82)、`grep WorkRule` 仅命中数据层 | 非 9–18 班次、不同午休/缓冲的用户，加班全部算错 |
| F-04 | **S2 性能** | 月历每个格子各自订阅 `observeDaySummary`，且 Flow 在 composable 内新建 | [HomeScreen.kt:243-245](worktime-android/app/src/main/java/com/worktime/pro/ui/home/HomeScreen.kt#L243-L245) | 每次重组 42 个 Flow 重建、84 次查询；滚动/切月掉帧 |
| F-05 | **S1 功能** | 「加班色阶」开关无读取端，日历从不显示底色 | [SettingsDataStore.kt:45](worktime-android/app/src/main/java/com/worktime/pro/data/datastore/SettingsDataStore.kt#L45)、HomeScreen 无引用 | 设置形同虚设，PRD 3.2.2 未满足 |
| F-06 | **S1 功能** | 月份只能点箭头；`goToToday()` 已写但未接；未来日期仍可点击录入 | [HomeScreen.kt:108-119](worktime-android/app/src/main/java/com/worktime/pro/ui/home/HomeScreen.kt#L108-L119)、[HomeViewModel.kt:78-80](worktime-android/app/src/main/java/com/worktime/pro/ui/home/HomeViewModel.kt#L78-L80) | PRD 的滑动切月、回到今天、未来置灰均未满足 |
| F-07 | **S1 功能** | 统计「自定义」维度没有日期选择入口，`setCustomRange` 从未调用 | [SummaryViewModel.kt:64-67](worktime-android/app/src/main/java/com/worktime/pro/ui/summary/SummaryViewModel.kt#L64-L67) | 自定义区间实际固定为「近 30 天」，用户不可控 |
| F-08 | **S1 功能** | 无 PDF 导出；「Excel」实为制表符文本改 `.xls` 后缀 | [ReportExporter.kt:16-20](worktime-android/app/src/main/java/com/worktime/pro/domain/export/ReportExporter.kt#L16-L20)、[SummaryScreen.kt:128-141](worktime-android/app/src/main/java/com/worktime/pro/ui/summary/SummaryScreen.kt#L128-L141) | Excel 打开报格式不匹配；PRD 3.6 的 PDF 缺失；分享 MIME 固定 `text/*` |
| F-09 | **S1 功能** | 凭证原图无法查看，点击是空实现；图片存绝对路径，恢复/清缓存后失效；「备份含照片」无实现（已确认：移除该配置，照片只进相册） | [MainActivity.kt:180](worktime-android/app/src/main/java/com/worktime/pro/MainActivity.kt#L180)、[DayDetailScreen.kt:215-221](worktime-android/app/src/main/java/com/worktime/pro/ui/detail/DayDetailScreen.kt#L215-L221)、[CameraViewModel.kt:129-135](worktime-android/app/src/main/java/com/worktime/pro/ui/camera/CameraViewModel.kt#L129-L135) | PRD 3.3「查看凭证」不可用；换机后凭证全部断链 |
| F-10 | **S2 正确性** | 统计失败时把 `rangeTruncated` 置 true，界面提示「区间已截断」 | [SummaryViewModel.kt:93-100](worktime-android/app/src/main/java/com/worktime/pro/ui/summary/SummaryViewModel.kt#L93-L100)、[SummaryScreen.kt:104-107](worktime-android/app/src/main/java/com/worktime/pro/ui/summary/SummaryScreen.kt#L104-L107) | 加载失败被误报为区间问题，用户无法判断 |
| F-11 | **S1 功能** | 备份恢复只恢复 3 个设置；`backupIncludePhotos` 无读写；导出文件与照片无关联 | [BackupManager.kt:161-167](worktime-android/app/src/main/java/com/worktime/pro/domain/backup/BackupManager.kt#L161-L167) | 换机后主题以外的显示/打卡偏好全部丢失 |
| F-12 | **S2 视觉** | 黑白主题未定义 `surfaceContainer*`，回落到 Material3 默认紫灰色；状态栏固定浅色主题；未启用 edge-to-edge；等宽数字只应用到 display/headline | [Theme.kt:94-110](worktime-android/app/src/main/java/com/worktime/pro/ui/theme/Theme.kt#L94-L110)、[themes.xml:3-6](worktime-android/app/src/main/res/values/themes.xml#L3-L6)、[Theme.kt:118-125](worktime-android/app/src/main/java/com/worktime/pro/ui/theme/Theme.kt#L118-L125) | 黑白模式卡片发紫；深色模式状态栏图标可能不可见；列表数字不对齐 |
| F-13 | **S0 数据** | `today`/`dateKey` 在 ViewModel 构造时固定；跨零点后打卡写入前一天，今日圆环不更新 | [HomeViewModel.kt:44-45](worktime-android/app/src/main/java/com/worktime/pro/ui/home/HomeViewModel.kt#L44-L45)、[TodayViewModel.kt:36-38](worktime-android/app/src/main/java/com/worktime/pro/ui/today/TodayViewModel.kt#L36-L38) | 夜间/凌晨使用会把记录写错日期 |
| F-14 | **S1 功能** | 确认页日期只能看不能改；`type == null` 的识别候选被静默丢弃；置信度未展示；`ocrAutoFill` 未读取 | [ConfirmScreen.kt:119](worktime-android/app/src/main/java/com/worktime/pro/ui/camera/ConfirmScreen.kt#L119)、[ConfirmScreen.kt:75-84](worktime-android/app/src/main/java/com/worktime/pro/ui/camera/ConfirmScreen.kt#L75-L84) | PRD 3.1.7 的「日期可改、类型修正、置信度标识」未满足 |
| F-15 | **S2 健壮性** | OCR 存图在 Main 线程；相机原图不缩放直接 OCR；`pendingBitmap` 用后不清；ML Kit client 不关闭；备份读取在 Main 线程 | [CameraViewModel.kt:82](worktime-android/app/src/main/java/com/worktime/pro/ui/camera/CameraViewModel.kt#L82)、[CameraViewModel.kt:58-60](worktime-android/app/src/main/java/com/worktime/pro/ui/camera/CameraViewModel.kt#L58-L60)、[BackupViewModel.kt:55-73](worktime-android/app/src/main/java/com/worktime/pro/ui/settings/BackupViewModel.kt#L55-L73) | 大图 OOM、保存卡顿、识别页空白 |
| F-16 | **S2 架构** | 导出使用 `CoroutineScope(Dispatchers.IO)`，未绑定生命周期 | [SettingsScreen.kt:85](worktime-android/app/src/main/java/com/worktime/pro/ui/settings/SettingsScreen.kt#L85)、[SummaryScreen.kt:159](worktime-android/app/src/main/java/com/worktime/pro/ui/summary/SummaryScreen.kt#L159) | 页面退出后仍在跑，异常无人接收 |
| F-17 | **S2 易用** | 状态映射 10 行 × 5 个 Chip 横排，无横向滚动 | [SettingsScreen.kt:368-387](worktime-android/app/src/main/java/com/worktime/pro/ui/settings/SettingsScreen.kt#L368-L387) | 小屏溢出；50 个点按目标，改一次映射很痛苦 |
| F-18 | **S3 打磨** | 删除无撤销；无空状态/骨架屏；中文硬编码在 Kotlin；统计缺勤/加班日口径未区分 | 多处 | 细节体验与可维护性 |

### 1.3 设置项闭环审计

| 设置项 | 有 UI | 有读取端 | 结论 |
|--------|-------|----------|------|
| 日历显示模式 | ✅ | ✅ | 正常 |
| 时间制式 | ✅ | ✅ | 正常 |
| 主题 | ✅ | ✅ | 正常 |
| OCR 自动填充 | ✅ | ❌ | 开关无效，确认页始终自动填 |
| 加班色阶 | ✅ | ❌ | 开关无效 |
| 默认录入方式 | ❌ | ❌ | 死配置 |
| 备份含照片 | ❌ | ❌ | 移除该配置，照片只进相册 |
| 状态映射 | ✅ | ✅ | 正常 |
| 习惯打卡 | ✅ | ✅ | 正常 |

> 建议把「每个设置必须有唯一读取点」写进本项目的自查清单，避免再次出现。

---

## 二、功能性升级方案

### 2.0 口径先定：正常工时 = 净投入 − 加班

不改数据库、不改引擎契约，只在聚合层修正：

```
正常工时 = Σ max(0, session.workMinutes − session.overtimeMinutes)
加班工时 = Σ session.overtimeMinutes
净投入   = 正常工时 + 加班工时 = Σ session.workMinutes
```

- 9:00–20:00（扣午休 60，标准 8h，缓冲 30m）→ 正常 8h30 + 加班 1h30 = 净 10h
- 周末 10:00–15:00 → 正常 0 + 加班 4h
- 多段工作日 → 各段先相减再求和

> 标准工时 = 在岗时长 − 午休（9–18 午休 1h → 8h）。原实现少扣午休，导致加班起算晚 1 小时，本次一并修正。

落点：新增 `domain/model/DayTotals.kt`（纯函数 + 单测），替换 [TodayScreen](worktime-android/app/src/main/java/com/worktime/pro/ui/today/TodayScreen.kt)、[DayDetailScreen](worktime-android/app/src/main/java/com/worktime/pro/ui/detail/DayDetailScreen.kt)、[SummaryViewModel](worktime-android/app/src/main/java/com/worktime/pro/ui/summary/SummaryViewModel.kt)、[WorkTimeRepository.observeDaySummary](worktime-android/app/src/main/java/com/worktime/pro/domain/repository/WorkTimeRepository.kt#L48-L65)、[BackupManager.exportCsv](worktime-android/app/src/main/java/com/worktime/pro/domain/backup/BackupManager.kt#L99-L133) 里所有的 `workMinutes` 累加。

**验收**：任取一天，「正常 + 加班」必须等于当日净投入；周末只增加加班；月统计不再虚高；新增 4 个单测。

---

### 2.1 P0：正确性与已承诺功能（建议先做）

| 编号 | 升级项 | 具体内容 | 验收标准 |
|------|--------|----------|----------|
| P0-1 | 工时口径统一 | 见 2.0；同步修正 CSV、今日、详情、统计、日历 | 所有页面同一日期的「正常 + 加班」一致且不重叠 |
| P0-2 | 工作规则中心 | 新增「设置 → 工作规则」：上下班时间、午休开关+时段、加班缓冲、日切时间、工作日多选；实时预览「标准工时」（在岗 − 午休，修正原实现少扣午休）；保存到 `work_rule`。修改后默认不回溯历史，提供「重算全部记录」二次确认动作 | 改成 8:30–17:30/午休 12–13/缓冲 15 后，新打卡按新规则计算；重算后可看到历史更新 |
| P0-3 | 多段打卡 + 配对加固 | 同一天允许同类型多次打卡：快速打卡/手动录入/OCR 确认都追加写入；重算改为「每个上班找其后最近的未用下班」的贪心配对；今日与日详情按时间展示所有段，支持逐条编辑/删除；OCR 确认页改为可增删的多段列表 | 一天可记录多段上下班与加班段；乱序/缺段时正确配对或明确跳过；每段可独立修改 |
| P0-4 | 月历性能与交互 | 新增 DAO 月区间聚合查询（session 汇总 + event 计数），`HomeViewModel` 一次订阅生成 `Map<date, DayCellState>`；42 格只读内存数据。加 `HorizontalPager` 滑动切月、`回到今天`、未来日期禁用、`加班色阶` 底色、缺卡角标、图例 | 切月 ≤ 500ms；滑动可切月；开关色阶即时生效；缺上下班的日期有提示 |
| P0-5 | 凭证闭环 + 写入相册 | 新增全屏凭证查看器（双指缩放、分享）；路径改存相对文件名并兼容旧绝对路径；文件缺失显示占位；设置加「凭证管理」（占用、批量清理）；移除「备份含照片」死配置；**确认保存时把凭证同步写入系统相册 `Pictures/工时助手`**，设置中可关闭；API 29+ 走 MediaStore 无需权限，API 26–28 申请 `WRITE_EXTERNAL_STORAGE` | 详情页点图能看原图；确认保存后系统相册出现照片；关闭开关后不写；拒绝权限不阻塞入库；备份不含照片 |
| P0-6 | 统计完整 | 自定义区间弹 `DateRangePicker`（含本月/上月/近 30 天/今年预设）；错误态独立于「截断」；真实 XLSX（自写最小 OOXML，零依赖）+ PDF（`PdfDocument`）+ CSV；按格式设置 MIME；导出移入 ViewModel | 五个维度都可翻页/选区；三种格式都能被系统应用正确打开；失败有重试 |
| P0-7 | 备份完整 | 恢复全部设置（日历模式、时间制式、色阶、OCR 填充、输入方式、状态映射、主题、习惯、自定义配色）；旧版 JSON 继续可导入；**备份只含数据库与设置，不含照片（已确认）**；恢复后提示需重启生效的项 | 导出→清数据→导入，所有偏好与数据一致；照片不进入备份 |
| P0-8 | 跨零点与生命周期 | 写库时取 `LocalDate.now()` 而非构造时快照；新增轻量 `DateTicker`（每分钟/到零点触发），Home/Today 的 `today` 改为可更新；跨零点的快速打卡时间重置为当前时刻 | 23:59 打开应用、00:05 打卡，记录落在新一天；圆环自动切到新一天 |
| P0-9 | 确认页补全 | 改为可增删的多段打卡列表；日期可改（日期选择器）；每段可选类型、设时间、显示置信度；识别结果可多次添加，`type == null` 的候选可指定类型；尊重 `ocrAutoFill`；保存前校验每段的类型与时间 | PRD 3.1.7 的日期/类型修正、置信度标识可用，并支持多段入库 |
| P0-10 | 线程与内存 | 存图、备份读写、导出全部切 IO；拍照后按最长边 ~1600px 缩放再 OCR；确认保存后清空 bitmap；`onCleared` 关闭 ML Kit client | 大图不再 OOM；保存无卡顿；连续拍 10 张内存稳定 |

---

### 2.2 P1：体验增强（P0 稳定后做）

| 编号 | 升级项 | 具体内容 |
|------|--------|----------|
| P1-1 | 今日页工作状态 | 顶部 hero 卡：状态（未开始/工作中/加班中/已下班）、实时计时、下一次建议动作；主按钮随状态切换（上班打卡→下班打卡→加班打卡）；打卡成功 Snackbar + 撤销 |
| P1-2 | 首页月度摘要 | 月份卡内嵌「出勤 X 天 · 正常 Yh · 加班 Zh」；缺卡提醒条（本月缺上下班/加班配对的日子，可点进详情）；习惯改为横向 Chip，最多 3 个 + 管理 |
| P1-3 | 统计洞察 | KPI 改为 2×2 大数字卡：正常、加班、出勤、加班占比；图表改堆叠柱（蓝+橙）+ 网格线 + 横轴日期 + 图例；区间对比上期；异常日（加班 > 4h、缺卡）列表 |
| P1-4 | 我的页重构 | 品牌头图保留；新增数据概览（记录天数、累计正常/加班、连续记录）；分组入口：数据（统计/备份/凭证）、效率（习惯）、偏好（设置/主题）、关于（版本/隐私） |
| P1-5 | 设置页信息架构 | 按「显示 / 工作规则 / 打卡 / 识别 / 数据 / 关于」分组；工作规则单独子页；状态映射改为每行下拉（或独立页 + 搜索），不再 5 个 Chip 横排；数据管理显示存储占用 |
| P1-6 | 相机体验 | 取景框四角引导线、手电筒、快门大按钮、拍照后预览确认、相册入口；识别中显示进度与取消 |
| P1-7 | 删除撤销与空状态 | 删除打卡改为「立即隐藏 + Snackbar 撤销 5s + 到时落库」或删除后保留快照可恢复；首页/统计/详情/习惯补空状态与 CTA |
| P1-8 | 导航与来源保持 | 从今日页进入拍照/手动，保存后回今日而非首页；子页进入/返回加滑动过渡；底部导航加选中指示动画 |
| P1-9 | 无障碍与大字体 | 日历格 TalkBack 描述「11月20日，正常9.5小时，加班0.5小时」；所有图标有 contentDescription；1.3x/2.0x 字号下不截断；触控目标 ≥ 48dp |
| P1-10 | 自定义主题与字体 | 设置 → 外观：7 个关键色（品牌、正常工时、加班、背景、卡片、正文、次要文字）+ 字号缩放；浅色/深色分别保存；4 套预设、实时预览、对比度过低警告、一键恢复；零依赖 HSV 取色器；自定义配色随备份导出恢复 | 改色后全应用即时生效；读不清的文字有警告；重启/换机后保持；可恢复默认 |

---

### 2.3 P2：可选升级（需单独确认）

| 编号 | 方向 | 说明 | 成本 |
|------|------|------|------|
| P2-1 | 上下班提醒 | 按工作规则时间本地通知（AlarmManager/WorkManager），无需网络，新增 `POST_NOTIFICATIONS` | 中 |
| P2-2 | 工资估算 | 设置时薪与加班倍率，统计页展示预估收入；纯计算，不改库 | 小 |
| P2-3 | 桌面小组件 | Glance 组件显示今日状态/一键打卡，需处理进程与写库 | 大 |
| P2-4 | 照片存储治理 | 按月份归档、自动清理 N 个月前、导出时打包 | 中 |
| P2-5 | 请假/调休/标签 | 需要数据库 v2 迁移与统计口径扩展 | 大 |
| P2-6 | 多班次/轮班规则 | 现有表结构支持多规则，但 UI 与引擎按单默认规则；改造成本高 | 大 |
| P2-7 | 文案资源化 | 中文迁入 `strings.xml`，为后续多语言与统一文案做准备 | 中 |

> 不建议本轮做 P2-3/P2-5/P2-6，容易把一次界面优化拖成架构改造。

---

## 三、界面美化方案

### 3.1 设计定位：现代台账（Modern Ledger）

关键词：**纸感、刻度、数字、克制**。不是通用 Material 模板，也不是拟物账本。核心表达：

- 数字是主角：工时、时间、日期用等宽数字（tnum）+ 清晰层级，像台账上的刻度和合计。
- 颜色有语义：蓝=正常、橙=加班、墨蓝=品牌/操作，绝不混用。
- 纸感只做点缀：细分割线、页眉小刻度、日期格底色，不大面积贴纹理。
- 动效只做状态解释：切月方向、打卡成功、数量变化，不做无意义弹跳。

**不做**：紫蓝渐变、玻璃拟态、大面积阴影、彩色图标堆叠、圆角一律 24dp 的模板化卡片。

### 3.2 设计令牌

#### 颜色

| 角色 | 浅色 | 深色 | 用途 |
|------|------|------|------|
| ink / primary | `#1B2A41` | `#8FB4E8` | 标题、主按钮、品牌 |
| work / secondary | `#3A6EA5` | `#7FA9D8` | 正常工时、上班 |
| overtime / tertiary | `#E07A3F` | `#E08A5A` | 加班、今日圆环 |
| paper / background | `#F5F7FA` | `#0F1620` | 页面底 |
| surface | `#FFFFFF` | `#182130` | 卡片 |
| surfaceVariant | `#EEF1F5` | `#222D3E` | 标签底、输入底 |
| outline | `#D5DBE3` | `#3A4658` | 卡片描边、分割 |
| textPrimary | `#1B2A41` | `#E8EDF4` | 正文 |
| textSecondary | `#8492A6` | `#A3AEBE` | 说明 |
| success（新增） | `#2E7D5B` | `#6FCF97` | 打卡完整、已保存 |
| warning（新增） | `#C9821F` | `#F2C94C` | 缺卡、待补 |
| danger（新增） | `#C0392B` | `#FF8A80` | 删除、错误 |

黑白主题补齐全部 `surfaceContainer*`（`#121212` / `#1A1A1A` / `#222` / `#2A2A2A` / `#333`），并保证正常/加班不靠色相区分：加班块加一条 2dp 实心边或「加」字角标。

#### 字体

| 层级 | 字号/行高 | 字重 | 用法 |
|------|-----------|------|------|
| display | 34 / 42 | Bold，tnum | 首页月度总工时、今日计时 |
| headline | 26 / 34 | Bold，tnum | 月份、统计大数字 |
| title | 20 / 28 | SemiBold | 页面标题、卡片标题 |
| section | 15 / 22 | Bold | 分组标题 |
| body | 14 / 22 | Regular | 正文 |
| label | 12 / 16 | Medium | 设置项、按钮 |
| caption | 10 / 14 | Medium | 日历时长标签、图例 |

- 所有层级加 `fontFeatureSettings = "tnum"`，避免列表数字跳动。
- 中文行高比 Material 默认略大，防止挤压；关闭 `includeFontPadding` 后用行高控制。
- 不引入第三方字体，避免包体与中文字形问题。

#### 间距 / 形状 / 层级

- 4pt 栅格；页面左右 20dp；卡片内 16dp；区块间距 16dp；列表行高 ≥ 56dp。
- 圆角：标签 6dp（pill 仅用于状态 Chip）、卡片 16dp、输入框 12dp、底部弹层 24dp 顶角。
- 浅色：卡片 `surface + 1dp outline`，不用阴影；深色：用 `surfaceContainer` 的明度差，不用描边堆叠。
- 图标：Material Symbols/Filled 统一 20dp（列表）/24dp（顶栏），线宽与字重匹配。

### 3.3 组件库（新增 `ui/components/`）

| 组件 | 作用 |
|------|------|
| `LedgerTopBar` | 标题 + 副标题日期 + 右侧圆形图标按钮（统计/设置） |
| `MonthSwitcher` | 胶囊容器 + 左右箭头 + 月份 + 「今天」Chip |
| `SummaryStrip` | 三格指标：出勤 / 正常 / 加班，带语义色圆点 |
| `CalendarCell` | 日期、今日圆环、加班底色、时长标签、未来禁用、缺卡角标 |
| `DurationTag` | 低饱和底色 + 语义文字，替代当前高饱和大色块 |
| `PunchTimeline` | 竖向时间线：时间、类型、来源、凭证、编辑/删除 |
| `StatTile` | 2×2 大数字指标卡 |
| `SectionCard` / `SettingRow` | 统一分组与设置行（图标 + 标题 + 说明 + 尾部） |
| `EmptyState` | 图标圆底 + 标题 + 说明 + 主 CTA |
| `Skeleton` | 统计/首页加载骨架 |
| `TimePickerSheet` | 底部弹层时间选择，替代 `AlertDialog(TimePicker)` |
| `EvidenceViewer` | 全屏凭证：缩放、分享、缺失占位 |
| `ConfirmSheet` | 统一的二次确认（覆盖打卡、清空、删除） |

### 3.4 页面改版

#### 首页（核心）

```
┌ 工时助手                     11月20日 周四   ◯统计 ◯设置 ┐
│  ◀    2026年11月    ▶      [今天]                        │
│  ┌──────────────────────────────────────────────────┐  │
│  │ 出勤 12 天        正常 92.5h        加班 14.0h      │  │
│  ├──────────────────────────────────────────────────┤  │
│  │ 一   二   三   四   五   六   日                    │  │
│  │ 16   17   18   19   20   21   22                   │  │
│  │      ●4.5  ●9.0  ⚠   ◯    ●8.5  ●2.0             │  │
│  │ ... 6 行，今日橙色圆环，加班格淡橙底 ...            │  │
│  └──────────────────────────────────────────────────┘  │
│  图例  ●正常  ●加班  ◯今天  ⚠缺卡                         │
│  ⚠ 11月18日 缺下班卡，点击补录                          │
│  [正常班] [加班日]                        管理            │
│                                          ┌────┐        │
│                                          │ 📷 │ FAB    │
│                                          └────┘        │
└──────────────────────────────────────────────────────────┘
```

- 日历卡是唯一视觉重心；月份、图例、摘要都在卡内，减少页面碎片。
- 底部操作不再并排两个大按钮，改为右下 FAB（拍照）+ 长按/次要入口（手动、习惯）。首屏更干净。
- 今日格：橙色 2dp 圆环 + 数字加粗；加班格：`tertiary` 4%/8%/12%/16% 四档底色；缺卡：右上角 6dp 警示圆点。
- 时长标签改为「低饱和底 + 语义色文字」，ALL 模式上下两个小标签，字号 10sp，单行。

#### 今日页

```
┌ 今日   11月20日 周四                              ┐
│ ╭──────────────────────────────────────────────╮ │
│ │ 工作中 · 已工作 3小时12分                      │ │
│ │ 09:02 上班  →  18:00 下班                      │ │
│ │ [        下班打卡        ]  [调整]             │ │
│ ╰──────────────────────────────────────────────╯ │
│ 打卡时间线                                       │
│ ● 09:02  上班   拍照识别   [凭证] [编辑] [删除]   │
│ ○ 18:00  下班   未打卡                [+ 补录]   │
│ ○ --:--  加班到 未打卡                [+ 补录]   │
│ ○ --:--  加班退 未打卡                [+ 补录]   │
│ 今日合计  正常 0h00 · 加班 0h00                  │
└──────────────────────────────────────────────────┘
```

- hero 卡用墨蓝底 + 白色等宽计时，加班状态用橙色状态点；只保留一个主操作，降低误触。
- 「调整」打开底部时间选择层，保留现有 4 类独立时间。
- 实时计时每秒刷新，页面不可见时暂停。

#### 统计页

```
┌ ← 统计汇总                                     ┐
│ [日] [周] [月] [年] [自定义]                     │
│ ◀   2026-11-01 ~ 2026-11-30   ▶                │
│ ┌ 正常 92.5h ┐ ┌ 加班 14.0h ┐                  │
│ ┌ 出勤 12天  ┐ ┌ 加班占比 13% ┐                 │
│ 每日趋势                                        │
│  ▇                                              │
│  ▇ ▇   ▇                                        │
│  ▂▅▇▃▆▇▂▃  图例 ■正常 ■加班                     │
│ 洞察：加班最多 11/18（3.5h） · 日均 7.7h        │
│ [导出报表]                                       │
└────────────────────────────────────────────────┘
```

- KPI 从四行文字改为四块大数字，先看结论再看趋势。
- 图表：堆叠柱 + 3 条淡网格线 + 每 5 根一个日期标签 + 图例；点击柱子可跳到当日详情。
- 导出用底部弹层选择 CSV / Excel / PDF，并显示将导出的区间与条数。

#### 设置页

```
┌ ← 设置                                          ┐
│ 外观    主题 / 时间制式 / 日历内容 / 加班色阶 / 自定义配色 │
│ 工作规则  09:00–18:00 · 午休 1h · 缓冲 30m   →   │
│ 打卡    OCR 自动填充 / 默认录入 / 习惯打卡   →    │
│ 识别    考勤机状态映射                        →   │
│ 数据    统计 / 备份 / 恢复 / 凭证管理             │
│ 关于    版本 2.1.0 · 隐私说明                    │
└──────────────────────────────────────────────────┘
```

- 分组卡片只放「一眼能懂的当前值」，详细配置进子页，解决当前设置页过长、映射区拥挤的问题。

#### 详情 / 确认 / 习惯 / 相机

- **日详情**：时间线 + 「正常/加班/净投入」三行合计 + 计算式折叠区 + 凭证缩略图；删除走 Snackbar 撤销。
- **确认页**：原图可缩放；日期可点改；四个时间槽带置信度徽标（高绿/中黄/低红）；「其他识别结果」列表可指派类型；底部固定保存按钮。
- **习惯**：卡片列表 + 预览时间轴 + FAB 新增；应用时用底部确认层展示将写入的时间。
- **相机**：四角取景框、快门大圆钮、手电筒、相册、识别进度。

### 3.5 动效

| 场景 | 动效 |
|------|------|
| 底部 Tab 切换 | 淡入 + 4dp 上移，180ms |
| 子页进入/返回 | 水平滑动（详情/设置推入，返回反向），220ms |
| 切月 | 按方向水平滑入 + 淡入；月摘要数字 200ms 计数过渡 |
| 打卡成功 | hero 卡状态色短暂脉冲 + Snackbar + 轻震动 |
| 删除 | 行高收起 180ms；Snackbar 撤销 |
| FAB | 按下 0.96 缩放，松开回弹 |

统一 `tween(200, FastOutSlowInEasing)`；系统开启「移除动画」时全部降级为淡入。

### 3.6 深色 / 黑白 / 系统栏

- 三套 `ColorScheme` 全量槽位，不允许任何组件回落到 M3 默认紫灰。
- `MainActivity.enableEdgeToEdge()`；用 `WindowInsetsControllerCompat` 按当前背景亮度切换状态栏/导航栏图标明暗。
- 动态取色默认关闭（保护品牌），如提供开关，放在「主题」子页并注明「可能改变工时/加班色」。
- 黑白模式用「灰度 + 形状/文字」表达语义，不只靠颜色。

#### 3.6.1 用户自定义主题（新增需求）

入口：设置 → 外观 → 自定义配色。浅色/深色各保存一套，黑白模式保持独立。

| 可编辑槽位 | 浅色默认 | 深色默认 | 影响范围 |
|------------|----------|----------|----------|
| 品牌 / 主色 | `#1B2A41` | `#8FB4E8` | 标题、主按钮、选中态 |
| 正常工时色 | `#3A6EA5` | `#7FA9D8` | 正常工时、上班标签 |
| 加班色 | `#E07A3F` | `#E08A5A` | 加班标签、今日圆环 |
| 页面背景 | `#F5F7FA` | `#0F1620` | 页面底 |
| 卡片 / 表面 | `#FFFFFF` | `#182130` | 卡片、弹层 |
| 正文文字 | `#1B2A41` | `#E8EDF4` | 标题与正文 |
| 次要文字 | `#8492A6` | `#A3AEBE` | 说明、图例 |

- **自动推导**：`onPrimary/onSecondary/onTertiary` 按背景亮度自动选黑/白；容器色由主色与表面色按 10% / 16% 混合；描边由文字色按 12% 混合。用户不需要理解 Material3 全部槽位。
- **预设**：工时台账（默认）、墨绿、赭石、午夜蓝，四套一键切换；「恢复默认」清空自定义。
- **对比度校验**：正文/背景、次要文字/背景按 WCAG 计算；低于 4.5:1 显示警告，低于 3:1 禁止保存，避免把界面调成不可读。
- **字号**：可选「小 / 标准 / 大」（0.9 / 1.0 / 1.15），应用到全部 Typography；字体颜色即正文/次要文字两个槽位。
- **取色器**：自写 HSV 取色器（色相滑条 + 饱和度/明度面板 + Hex 输入），零第三方依赖。
- **持久化**：DataStore 存 `custom_light`、`custom_dark`、`font_scale`，随 JSON 备份导入导出；应用启动和设置变更时实时重组。

### 3.7 无障碍与性能预算

- 对比度 ≥ 4.5:1；日历时长标签不用纯色小字压在纯色底上。
- 所有交互 ≥ 48dp；日历格高度 ≥ 56dp，字号放大到 1.3x 不截断。
- 首页首帧不查库：月度聚合一次查询，42 格纯渲染；切月目标 ≤ 500ms。
- 动画不影响列表滚动；图片统一 Coil 缓存与采样。

---

## 四、实施计划

### 4.1 阶段划分

| 阶段 | 内容 | 产出 | 建议节奏 |
|------|------|------|----------|
| Phase 0 | 先提交当前工作区改动作为安全点；跑通单测与 Debug 构建；记录基线 | 干净分支 + 基线 | 0.5 天 |
| Phase 1 | P0-1 ~ P0-10（数据口径、规则、幂等、月历、凭证、统计、备份、跨零点、确认页、线程） | 功能完整、数据正确 | 3–4 天 |
| Phase 2 | P1-1 ~ P1-10（今日 hero、首页摘要、统计洞察、我的、设置 IA、相机、撤销、导航、无障碍、自定义主题） | 体验与视觉升级 | 3–4 天 |
| Phase 3 | P2 选做（提醒、工资、组件、照片治理等） | 按确认范围 | 另议 |

每个 Phase 结束：跑单测 + 构建 Debug APK + 按验收清单手动过一遍浅色/深色/黑白、12/24 小时、1.3x 字号。每个 P0 项单独一次提交，提交信息沿用现有中文 conventional commits 风格。

### 4.2 主要文件改动地图

| 区域 | 新增 | 修改 |
|------|------|------|
| 口径 | `domain/model/DayTotals.kt` | Home/Today/Detail/Summary/Backup 的聚合 |
| 规则 | `ui/rules/WorkRulesScreen.kt`、`WorkRulesViewModel.kt` | `WorkTimeRepository`（保存规则、可选重算）、`MainActivity` 路由、`SettingsScreen` 入口 |
| 月历 | `data/db/MonthAggregate.kt`、`ui/components/CalendarCell.kt` | `Daos.kt`、`HomeViewModel`、`HomeScreen` |
| 统计 | `domain/export/XlsxWriter.kt`、`PdfReportWriter.kt`、`ui/components/DateRangeDialog.kt` | `ReportExporter`、`SummaryViewModel`、`SummaryScreen` |
| 凭证 | `domain/evidence/EvidenceStore.kt`、`domain/evidence/GalleryPublisher.kt`、`ui/components/EvidenceViewer.kt` | `CameraViewModel`、`DayDetailScreen`、`BackupManager`、`SettingsScreen`、`AndroidManifest.xml` |
| 主题 | `ui/theme/Color.kt`、`Type.kt`、`Shape.kt`、`Tokens.kt`、`ui/theme/CustomThemeScreen.kt`、`ui/components/ColorPicker.kt` | `Theme.kt`、`themes.xml`、`MainActivity`、`SettingsDataStore`、`BackupManager` |
| 组件 | `ui/components/*` | 各页面替换内联样式 |
| 基础设施 | `util/DateTicker.kt`、`ui/components/TimePickerSheet.kt` | 各 ViewModel 的日期来源 |

### 4.3 数据库与兼容策略

- P0/P1 **不改表结构**，避免 Room 迁移风险；工时口径只在读取聚合时修正，不需要回填。
- 规则修改默认不自动重算历史；用户主动点「重算全部记录」才写回 `work_session`。
- 备份 JSON 保持 `version = "2.0"` 以兼容旧文件；新增字段按可选读取；备份不含照片，避免体积与兼容问题。
- 凭证路径改为相对文件名，读取时兼容旧的绝对路径；文件不存在不崩溃。

### 4.4 测试计划

新增单测（纯逻辑，易覆盖）：

- `DayTotalsTest`：工作日/周末/多段/仅有加班段，正常+加班=净投入。
- `PunchPairingTest`：乱序、重复、缺失结束、跨天。
- `WorkRuleValidationTest`：午休、缓冲、日切、工作日。
- `MonthlyAggregateTest`：区间聚合与缺卡判定。
- `XlsxWriterTest` / `PdfReportWriterTest`：生成文件可解析、行数正确。
- `BackupRoundTripTest`：JSON ↔ 实体，含旧版兼容与自定义配色。
- `CustomPaletteTest`：on-color 推导、容器混合、WCAG 对比度、序列化往返、字号缩放。
- `PunchHabitTest` 已存在，补「覆盖应用」场景。
- 相册写入无法纯单测覆盖，列入手动清单：确认保存后相册可见、关闭开关不写、拒绝权限仍能入库、API 26/29/35 各验证一次。

现有 38 项单测必须保持全绿；新增口径与配对测试后共 50 项。

### 4.5 风险与对策

| 风险 | 对策 |
|------|------|
| 口径修正后用户看到的历史数字“变小” | 发布说明写清「正常工时不再包含加班」；不修改数据库，不产生迁移 |
| 规则修改引发历史重算争议 | 默认不回溯 + 明确的重算按钮 + 二次确认 + 进度提示 |
| 月历 Pager 与纵向滚动冲突 | Pager 固定高度、只在日历区域响应横向手势；真机验证 |
| edge-to-edge 导致状态栏/导航栏遮挡 | 全页面 `Scaffold`/`WindowInsets` 检查，API 26 与 35 各测一遍 |
| PDF/XLSX 自写生成器兼容性 | 生成后分别用系统 PDF 查看器、Excel/WPS 打开验证；保留 CSV 兜底 |
| 相册写入的权限与隐私 | 默认开启但可关闭；API 26–28 权限被拒时只保留私有凭证并提示；相册照片不属于应用数据，删除记录不回收 |
| 确认页改动影响 OCR 主链路 | 保留现有四槽逻辑，先加日期/置信度/未归类区，不改识别器本身 |

### 4.6 完成定义（DoD）

- [ ] 所有 P0 验收项通过，50 项单测全绿。
- [ ] `assembleDebug` 成功，无新增警告级别以上问题。
- [ ] 浅色/深色/黑白、12/24 小时、1.3x 字号四组组合逐页走查。
- [ ] 飞行模式下全流程可用，无网络权限。
- [ ] 删除/覆盖/导入等破坏性操作均有二次确认或撤销。
- [ ] 相册写入：开关生效、权限拒绝不阻塞入库、确认保存后才发布。
- [ ] 自定义配色：对比度校验、恢复默认、重启与备份恢复后保持一致。
- [ ] README 与 PRD 的功能状态同步更新（PDF、工作规则、凭证、相册、自定义主题；备份明确不含照片）。

---

## 五、需要你拍板的事项

1. **本轮范围**：已确认 **P0 + P1**，P2 留到下一轮。
2. **备份含照片**：已确认 **不做照片备份**；照片只写入系统相册与私有凭证目录。
3. 以下按推荐默认执行：
   - 工作规则改动默认不回溯历史 + 手动「重算全部」；
   - Excel 自写最小 XLSX（零依赖）；
   - 自定义主题按 7 个关键色 + 字号缩放；
   - 相册默认开启、确认保存时写入 `Pictures/工时助手`、设置可关闭；
   - P2（提醒、工资、组件、请假/调休）不进入本轮。

审核通过后，我按 **Phase 1 → Phase 2** 顺序实施，每完成一个 P0 项单独提交，并在阶段末附上构建/测试结果与逐页走查清单。
