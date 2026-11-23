# WorkTime Pro 界面优化升级方案 V4.0（设计方案）

> 状态：**Phase A 已实施**；**Phase B 已实施**（首用引导按决策不做）；**Phase C 部分实施**（手机多形态适配：窗口宽改真实窗口宽、今日页横屏两栏；平板两栏/折叠 hinge/限宽分级待做）；Phase D 待排期
> 编写日期：2026-11-23 · 代码基线：`main @ d8a31ac`（V2.1.2）
> 前置：V3.0 已落地（数据口径、组件库、主题令牌、基础动效、640dp 限宽与侧栏导航），本文只覆盖 V3 之后的新一轮增量。
> 目标四件事：**① 打磨当前 UI　② 增加更多反馈动效　③ 让用户更好上手　④ 增强多设备/多形态适应**。

---

## 一、口径与总体判断

### 1.1 「多媒体适应」的解读

本项目是离线打卡工具，不涉及音视频。「多媒体适应」在本轮按 **多设备 / 多形态屏幕自适应（responsive & adaptive）** 理解：手机竖屏、手机横屏、折叠屏（展开/合盖）、平板、分屏/桌面小窗，以及不同字体/密度下的布局与交互自适应。若你本意是「音视频」，请纠正，我再另出一版。

### 1.2 现状基线（已确认）

V3 之后，项目已具备良好的底子：

- **视觉**：6 色语义系统 + 等宽数字 + 三套 ColorScheme（浅/深/黑白）齐全，无 M3 紫灰回落（[Theme.kt](worktime-android/app/src/main/java/com/worktime/pro/ui/theme/Theme.kt)）。
- **签名动效**：打卡盖章确认 `PunchStamp`（[PunchStamp.kt](worktime-android/app/src/main/java/com/worktime/pro/ui/components/PunchStamp.kt)）已成品牌记忆点。
- **多尺寸适配**：≥600dp 走 `NavigationRail`、单列 640dp 居中（[WindowAdaptive.kt](worktime-android/app/src/main/java/com/worktime/pro/ui/components/WindowAdaptive.kt)）、[MainActivity.kt](worktime-android/app/src/main/java/com/worktime/pro/MainActivity.kt) 自适应导航骨架。

### 1.3 本轮差距（精确对标）

| 维度 | 现状 | 差距 |
|------|------|------|
| 动效 | 切月 `changeMonth` 立即跳变，无滑动过渡；月摘要数字直接跳；底部 Tab 用 M3 默认 `NavigationBarItem`，无选中指示动画 | [HomeScreen.kt:121-135](worktime-android/app/src/main/java/com/worktime/pro/ui/home/HomeScreen.kt#L121-L135)、[MainActivity.kt:138-150](worktime-android/app/src/main/java/com/worktime/pro/MainActivity.kt#L138-L150) |
| 动效 | 全项目无 `performHapticFeedback`/震动，所有成功只靠 Toast + 盖章 | grep 全工程零命中 |
| 动效 | 打卡成功无「状态脉冲」；hero 卡状态点/标签瞬间切换 | [TodayScreen.kt:187-209](worktime-android/app/src/main/java/com/worktime/pro/ui/today/TodayScreen.kt#L187-L209) |
| 动效 | 今日流水是普通 `Column`，增删无行高收起/展开动画 | [TodayScreen.kt:390-405](worktime-android/app/src/main/java/com/worktime/pro/ui/today/TodayScreen.kt#L390-L405) |
| 易用 | 删除只有 `AlertDialog` 确认，无撤销（V3 DoD 已标注遗留） | [TodayScreen.kt:428-449](worktime-android/app/src/main/java/com/worktime/pro/ui/today/TodayScreen.kt#L428-L449) |
| 易用 | 统计/首页加载用 `CircularProgressIndicator`，无骨架屏（V3 F-18 遗留） | [SummaryScreen.kt:175-182](worktime-android/app/src/main/java/com/worktime/pro/ui/summary/SummaryScreen.kt#L175-L182) |
| 易用 | 月历只能左右箭头逐月翻，跨年要点 12 次；无年份/月份快速跳转 | [HomeScreen.kt:121-135](worktime-android/app/src/main/java/com/worktime/pro/ui/home/HomeScreen.kt#L121-L135) |
| 易用 | 习惯应用确认用 `AlertDialog`，与项目「底部弹层」语言不一致 | [HomeScreen.kt:300-340](worktime-android/app/src/main/java/com/worktime/pro/ui/home/HomeScreen.kt#L300-L340) |
| 多设备 | `rememberAppWindowSize` 读 `LocalConfiguration.screenWidthDp`（整屏宽），分屏/折叠/桌面小窗下断点不准 | [WindowAdaptive.kt:36-42](worktime-android/app/src/main/java/com/worktime/pro/ui/components/WindowAdaptive.kt#L36-L42) |
| 多设备 | 大屏只是单列 640dp 居中，无两栏/列表-详情布局，平板空间浪费 | [WindowAdaptive.kt:50-63](worktime-android/app/src/main/java/com/worktime/pro/ui/components/WindowAdaptive.kt#L50-L63) |
| 多设备 | 手机横屏（MEDIUM）今天页仍是竖排单列，时间卡与流水未并排 | [TodayScreen.kt](worktime-android/app/src/main/java/com/worktime/pro/ui/today/TodayScreen.kt) |
| 打磨 | 中文文案全部硬编码在 Kotlin，`strings.xml` 仅 `app_name`（V3 遗留） | [strings.xml](worktime-android/app/src/main/res/values/strings.xml) |

---

## 二、设计原则（沿用并收紧「现代台账」）

1. **数字是主角，动效只做状态解释**：切月方向、打卡成功、数量变化、增删收放——绝不做无意义弹跳、无限循环的装饰动画。
2. **一处定义，全局复用**：动效时长/缓动/触觉分层收进 `ui/motion/` 统一出口，禁止各页面手写散落的 `tween`。
3. **反馈分层**：视觉（动效）→ 触觉（震动）→ 听觉（可选，默认关），三层按严重度叠加，不重复轰炸。
4. **尊重系统**：系统开启「移除动画」或用户偏好减少动效时，位移/缩放全部降级为淡入或 0ms；高对比度下不靠颜色传递状态。
5. **克制**：延续「不做紫蓝渐变、玻璃拟态、大面积阴影」的红线，大屏不铺满，窄屏不挤压。

---

## 三、动效系统（Motion System）——本轮核心基建

新增 `ui/motion/` 包，全项目唯一动效出口。

### 3.1 时长令牌（`Motion.kt`）

| 令牌 | 值 | 用途 |
|------|-----|------|
| `durationInstant` | 0ms | 纯状态翻转（无动画场景） |
| `durationFast` | 120ms | 按压回弹、色值切换、开关 |
| `durationBase` | 200ms | 页面内状态切换、计数过渡、淡入 |
| `durationSlow` | 320ms | 切月滑动、弹层展开、删除收起 |
| `durationEmphasis` | 480ms | 打卡盖章、hero 状态脉冲（仅 1~2 处） |

缓动统一：进入/位移 `FastOutSlowInEasing`，淡入 `LinearOutSlowInEasing`，盖章压入 `Overshoot` 仅限 PunchStamp。

### 3.2 触觉分层（`Haptics.kt`）

封装 `LocalHapticFeedback`，按语义命名：

| 语义 | 类型 | 场景 |
|------|------|------|
| `hapticSelect()` | `TextHandleMove`/轻 | 切换选项、点 Chip、选日期 |
| `hapticConfirm()` | `LongPress`/中 | 打卡成功、保存成功、导出成功 |
| `hapticReject()` | 重 | 删除确认、写入被拒、校验失败 |

> 现全项目零触觉，打卡这种「留痕」动作尤其缺手感。打卡成功 = 盖章动画 + `hapticConfirm`（+ 可选音效，默认关闭）。

### 3.3 降级策略

- 读 `Settings.Global.ANIMATOR_DURATION_SCALE == 0f` 或系统 `removeAnimations` 时，`Motion.kt` 内部把时长归零，只保留淡入。
- 无障碍 `TalkBack` 开启时，不触发位移/缩放类动画，只保留状态语义播报。

---

## 四、方案一：UI 视觉打磨（小而准）

1. **底部 Tab 选中指示**：给 `NavigationBarItem`/`NavigationRailItem` 加选中胶囊底色过渡 + 图标轻微上浮（`animateDpAsState`/`animateColorAsState`），选中态不再是无过渡的静态高亮。
2. **缺卡提醒条做成可感知状态**：`N 天缺卡` 从静态红字升级为「低饱和红底 + 左侧竖条 + 出现时展开」，点击直达最近缺卡日补录（[HomeScreen.kt:229-245](worktime-android/app/src/main/java/com/worktime/pro/ui/home/HomeScreen.kt#L229-L245)）。
3. **加载态**：`CircularProgressIndicator` → 骨架屏（`Skeleton` 组件，shimmer 光扫），统计页四块指标卡、首页月摘要先出骨架再出数据，避免「转圈→整屏跳变」。
4. **统一「删除」视觉**：删除动作统一用 danger 红（`error` 槽位）+ 图标，不再混用灰图标 + 红按钮两种表达。
5. **空状态**：图标圆底 + 主 CTA 已在部分页面；补齐「轻呼吸」动画（`infiniteTransition` 0.9~1.05 缩放，受降级策略约束），不喧宾夺主。

---

## 五、方案二：反馈动效（重点，逐场景落地）

### 5.1 切月滑动 + 数值过渡（首页）

- 现状 `changeMonth` 立即换图（[HomeScreen.kt:121](worktime-android/app/src/main/java/com/worktime/pro/ui/home/HomeScreen.kt#L121)）。
- 改为 `AnimatedContent(targetState = month)`，按方向 `slideInHorizontally` + `fadeIn`（`durationSlow`），返回反向。
- 月摘要「出勤/正常/加班」三个数字用 `CountUpText`（`animateIntAsState`，200ms）滚动过渡，替代跳变。

### 5.2 打卡成功（今日页）

- 保留 `PunchStamp` 盖章（已是品牌点）。
- 追加：① `hapticConfirm()` 触觉；② hero 卡状态点做一次「脉冲扩散」（一个从状态点扩散的圆环 `animateFloatAsState` 半径 + 透明度，`durationEmphasis`）；③ 打卡主按钮按压 0.96 缩放回弹（`interactionSource`）。
- 主按钮 `containerColor` 已是印章朱红，保持；成功文案沿用 `savedCopy()` 说法，不新增歧义词。

### 5.3 状态切换脉冲（今日页）

- `status.label`（未开始→工作中→加班中→已下班→今日已完成）切换时：状态点颜色 `animateColorAsState`，label 做一次 4dp 上移 + 淡入，标识「状态变了」而非无声替换（[TodayScreen.kt:196-201](worktime-android/app/src/main/java/com/worktime/pro/ui/today/TodayScreen.kt#L196-L201)）。

### 5.4 今日流水增删动画

- 现状普通 `Column` + `forEachIndexed`（[TodayScreen.kt:390](worktime-android/app/src/main/java/com/worktime/pro/ui/today/TodayScreen.kt#L390)）。
- 改为 `LazyColumn`（或保留 Column + `Modifier.animateItem()`），新打卡条目「上移淡入」，删除条目「行高收起 180ms + 淡出」。
- 配合 6.1 的删除撤销，形成「消失 → Snackbar 撤销 → 恢复回弹」的完整闭环。

### 5.5 删除撤销（易用 + 动效合一）

- 现状删除 = 弹窗确认 → 直接落库（[TodayScreen.kt:428-449](worktime-android/app/src/main/java/com/worktime/pro/ui/today/TodayScreen.kt#L428-L449)）。
- 改为「乐观隐藏 + `Snackbar` 撤销 5 秒 + 到时才落库」：删除即时从界面收起，Snackbar 显示「已删除 XX · 撤销」；撤销则条目动画恢复，超时/离开则真正写库。日详情页同步。
- 危险区（清空、覆盖导入）保留二次确认，不套撤销。

### 5.6 其他动效清单

| 场景 | 动效 | 时长 |
|------|------|------|
| 底部 Tab / 侧栏选中 | 选中胶囊过渡 + 图标上浮 | 200ms |
| 子页进入/返回 | 已有 fade+slide，微调为 `slideIn` 全宽 1/4（现为 1/10，观感太弱） | 220ms |
| 时间带 `DayTape` 首次加载 | 各段从 0 宽扫入 + 「现在」游标按秒平滑（秒级 tick） | 320ms |
| 缺卡提醒条出现 | 展开 + 淡入 | 200ms |
| 导出成功 | 按钮短暂「✓」态 + `hapticConfirm` | 120ms |
| 设置开关 | 触觉 `hapticSelect` | 瞬时 |
| 习惯应用成功 | `hapticConfirm` + Snackbar（替代纯 Toast） | 瞬时 |

---

## 六、方案三：易用性升级（让用户更好上手）

1. **月/年快速跳转**：点月份标题弹「年月选择」底部弹层（复用 `TimePickerDialog` 的滚轮交互），一次跨年；左右箭头保留用于相邻月微调。解决「跨年点 12 次」。
2. **删除撤销**：见 5.5，覆盖今日页 + 日详情。
3. **习惯应用确认降级为底部弹层**：把 `AlertDialog`（[HomeScreen.kt:300-340](worktime-android/app/src/main/java/com/worktime/pro/ui/home/HomeScreen.kt#L300-L340)）换成 `ModalBottomSheet`，展示「追加 / 覆盖」与将写入的时间，与全项目「底部弹层」语言一致。
4. **首次使用引导**：3 步轻量引导（① 今日页打卡 ② 月历看台账 ③ 设置工作规则），仅首装显示，可跳过；空状态 CTA 文案指向下一步动作（已有底子，补引导页）。
5. **快捷打卡（大屏/长按）**：底部「今日」Tab 长按弹出快捷菜单（上班/下班/加班），桌面/平板可键盘 `Space` 打卡（P2 可选）。
6. **无障碍补强**：打卡按钮 TalkBack 朗读「当前下一步动作 + 当前时刻」；所有颜色语义旁都有文字/形状双编码（缺卡角标已用形状，沿用）。
7. **动效触觉**：见第三、五节，触觉本身就是「更容易用」的一环。

---

## 七、方案四：多设备/多形态适应

### 7.1 修正断点测量（正确性优先）

`rememberAppWindowSize` 现读 `screenWidthDp`（整屏宽），分屏/桌面小窗/折叠合盖下会把半个屏判成大屏。改为按**当前窗口**宽：

```
API 30+：LocalWindowInfo.current.containerSize.width
API < 30：WindowMetricsCalculator.getOrCreate().computeCurrentWindowMetrics()
```

断点保持 COMPACT <600 / MEDIUM 600–839 / EXPANDED ≥840，只换数据源。这是本轮最「值钱」的一行改动。

### 7.2 两栏 / 列表-详情布局（EXPANDED）

新增 `TwoPaneLayout`（`ui/components/`），只在 EXPANDED 启用：

| 页面 | 左（权重 2） | 右（权重 1） |
|------|-------------|-------------|
| 首页 | 月历卡 | 本月摘要 + 缺卡列表 + 习惯快捷 |
| 日详情 | 打卡时间线 | 合计 + 凭证缩略图 |

MEDIUM/COMPACT 回落现有单列；`AdaptiveContent` 的 640dp 限宽在 EXPANDED 下放宽为两栏各自限宽（如左 560 + 右 360）。

### 7.3 手机横屏（MEDIUM）专属

- 今天页：时间卡（左）与「今日流水」（右）左右并排，避免横屏下竖排一长条、滚动过深。
- 首页月历保持单列但去掉底部大按钮，改用右下 FAB（横屏底部按钮挤压内容）。

### 7.4 折叠屏 / 分屏

- 用 7.1 的真实窗口宽，折叠展开→合盖能即时在 COMPACT/MEDIUM 间切换，不需额外 hinge 检测（本轮）。
- 折叠 hinge 避让（读 `WindowManager` hinge/fold 信息、避开折缝）列为 P2，需真机验证。

### 7.5 内容限宽分级

`CONTENT_MAX_WIDTH` 从写死 640dp 提升为按档位：COMPACT 全宽、MEDIUM 640dp、EXPANDED 两栏（见 7.2），由 `TwoPaneLayout` 决定分栏后单列宽度，`AdaptiveContent` 保留为回退路径。

### 7.6 密度 / 字体叠加

- 保持「可滚动 + 自适应高度容器」策略，`wm density` 与系统字体 1.3x 叠加下不截断（V3 已验证，本轮不动）。
- 新增：跟随系统「高对比度」可选项（P2），黑白主题已覆盖大部分需求。

---

## 八、组件与文件地图

| 区域 | 新增 | 修改 |
|------|------|------|
| 动效基建 | `ui/motion/Motion.kt`、`ui/motion/Haptics.kt`、`ui/motion/CountUpText.kt`、`ui/motion/Skeleton.kt`、`ui/motion/AnimatedNavItem.kt`、`ui/motion/PulseRing.kt` | — |
| 多设备 | `ui/components/TwoPaneLayout.kt`、`ui/components/YearMonthPicker.kt` | `ui/components/WindowAdaptive.kt`（断点数据源、限宽分级） |
| 首页 | — | `ui/home/HomeScreen.kt`（切月 AnimatedContent、年月弹层、缺卡条、CountUp、骨架、两栏） |
| 今日页 | — | `ui/today/TodayScreen.kt`（状态脉冲、流水增删动画、删除撤销、触觉） |
| 统计 | — | `ui/summary/SummaryScreen.kt`（骨架、CountUp、导出成功态） |
| 导航 | — | `MainActivity.kt`（Tab 选中动画、触觉、子页滑动微调） |
| 日详情 | — | `ui/detail/DayDetailScreen.kt`（删除撤销、两栏） |
| 习惯 | — | `ui/habit/HabitScreen.kt` + `ui/home/HomeScreen.kt`（确认弹层换底部弹层） |

> 复用优先：年月选择复用 `TimePickerDialog` 的滚轮交互；底部弹层复用现有 `TimePickerDialog` 的容器样式，避免再造一套。

---

## 九、分阶段实施与验收

| 阶段 | 内容 | 验收 |
|------|------|------|
| **Phase A**（动效基建 + 高价值动效，1.5~2 天） | Motion/Haptics/CountUp/Skeleton 基建；切月 AnimatedContent、Tab 选中动画、打卡成功脉冲 + 触觉、删除撤销 + 流水增删动画 | 切月有方向感；打卡有手感；删除可撤销；系统开「移除动画」时全部降级 |
| **Phase B**（易用性，1~1.5 天） | 年月快速跳、习惯底部弹层、首用引导、空状态呼吸 | 跨年一次到位；习惯确认不再弹窗打断；TalkBack 朗读下一步动作 |
| **Phase C**（多设备适应，1.5~2 天） | 断点数据源修正、两栏布局、横屏并排、限宽分级 | 分屏/桌面小窗断点正确；平板首页左日历右摘要；手机横屏今天页并排 |
| **Phase D**（打磨，另议） | 文案资源化（`strings.xml` 外置）、高对比度跟随、折叠 hinge 避让、键盘/长按快捷 | 单测全绿 + 手动走查清单 |

**DoD（每阶段）**：`:app:testDebugUnitTest` 全绿；`assembleDebug` 无新增警告；浅/深/黑白 × 12/24h × 1.3 字号 × 竖屏/横屏/平板/分屏逐页走查；系统「移除动画」开启下功能不丢、无卡死动画。

**风险与对策**：

| 风险 | 对策 |
|------|------|
| 动效过多拖慢低端机 | 统一 200~320ms、`graphicsLayer` 而非重排、降级策略；不引入第三方动画库 |
| `LazyColumn` 替换流水引入滚动位置问题 | 今日页流水条数少，保留 `Column + animateItem()` 可避免 `LazyColumn` 在 `verticalScroll` 内的嵌套滚动冲突 |
| 断点换 `LocalWindowInfo` 在 API<30 需兼容 | 封装 `currentWindowWidthDp()`，内部按 SDK 分支，单点维护 |
| 两栏布局与 640dp 单列并存易碎 | `TwoPaneLayout` 只认 `AppWindowSize.EXPANDED`，其余一律走现有 `AdaptiveContent`，不重写现有页面 |
| 触觉在部分机型无效 | 触觉是可感知增强，不承载唯一信息；文字/盖章/Toast 仍在 |

---

## 十、明确不做（本轮红线）

- 不做音视频/贴纸/彩蛋类「多媒体」；「多媒体适应」= 多设备自适应（见 1.1）。
- 不引入第三方动画/动效库（Lottie 等），包体与离线约束优先。
- 不做无意义的循环装饰动画、玻璃拟态、紫蓝渐变、大面积阴影。
- 不改数据库表结构，不触碰工时引擎契约；本轮纯 UI/交互层。
- 不重写现有页面结构，只做增量替换（两栏为可选的 EXPANDED 分支）。
