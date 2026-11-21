# 智能工时与加班管理 App（单机版）

# 数据库设计文档 V2.0

**项目名称：** WorkTime Pro

**文档版本：** V2.0（实现校准版，2026-11-20 复核）

**编制日期：** 2026-11-20

**上一版本：** DatabaseDesign.md V1.1（MySQL 云端版，保留归档）

> **本次复核校准**：按 `Entities.kt` / `SettingsDataStore.kt` / `BackupManager.kt` 实际实现同步了
> 列名命名约定、一日多段的写入校验规则、DataStore 全部 18 个键、备份 JSON 结构（驼峰 + 无照片）、
> 索引与外键声明。设计意图与 V2.0 初版一致，差异仅在落地细节。

---

## 修订说明

| 变更项 | V1.1（旧） | V2.0（本版） |
|--------|-----------|-------------|
| 数据库 | MySQL 8.0（服务器） | **Room / SQLite（手机本地单文件）** |
| 表数量 | 6 张 | **3 张**（punch_event / work_session / work_rule，去掉用户表、导出记录表、设置表） |
| 多用户 | `user_id` 隔离 | **单用户，去掉所有 user_id 字段** |
| 打卡模型 | `attendance_record`（一条=一个工作段） | **`punch_event` + `work_session`（点段分离）** |
| 缓存 | Redis 10+ 缓存键 | **删除**（本地查询足够快） |
| 凭证 | 无 | **照片路径 + OCR 原文 + 置信度** |
| 设置存储 | `user_setting` 表 | **DataStore**（不入库） |

---

# 第一章 设计原则

## 1.1 核心设计决策：点段分离

V1.1 的 `attendance_record` 用一条记录表达"上班→下班"一个工作段，无法干净表达 V2.0 的四类时间点（上班/下班/加班到/加班退）。V2.0 采用**点段分离**模型：

```
punch_event（打卡事件，原始凭证）
    ↓ 聚合计算
work_session（工时段，计算结果）
```

**收益：**
- OCR 识别的原始时间点可追溯（哪张照片、哪次识别、什么置信度）
- 工时计算结果与原始凭证解耦，修改事件后可重算段
- 四类时间点无序录入也能正确配对成段
- 单机版失去服务器信任后，**凭证链**成为数据可信的基础

## 1.2 通用原则

| 原则 | 说明 |
|------|------|
| 单用户 | 无 `user_id`，无账号体系 |
| 时间存储 | 统一用 `Long` 时间戳（毫秒），展示层转 24H/12H |
| 日期归属 | `work_date` 存 `yyyy-MM-dd` 字符串，按日切规则计算 |
| 枚举存储 | 用 `String` 存枚举名（Room 无原生枚举支持，配 TypeConverter） |
| 主键 | `Long` 自增 |
| 外键 | Room `@ForeignKey` 约束，级联删除 |
| 索引 | 高频查询字段建索引 |

---

# 第二章 核心表设计

> **实现命名约定（V2.0 现状）**：Room 实体直接使用**驼峰列名**（`workDate`、`punchType`、`punchTime`、
> `sessionId`、`imagePath`…），DAO 查询亦按驼峰书写。本文档代码块为便于阅读沿用设计期的下划线命名，
> 两者语义一一对应，对接实现时以驼峰为准。真实实体见 `data/db/Entities.kt`。

## 2.1 打卡事件表 `punch_event`

**功能：** 存储每一次时间点记录（上班/下班/加班到/加班退），含 OCR 凭证信息。

```kotlin
@Entity(
    tableName = "punch_event",
    foreignKeys = [ForeignKey(
        entity = WorkSession::class,
        parentColumns = ["id"],
        childColumns = ["session_id"],
        onDelete = ForeignKey.SET_NULL
    )],
    indices = [
        Index(value = ["work_date"]),
        Index(value = ["work_date", "punch_type"]),
        Index(value = ["session_id"])
    ]
)
data class PunchEvent(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,

    /** 业务归属日期（按日切规则计算，yyyy-MM-dd） */
    val work_date: String,

    /** 打卡类型：WORK_START / WORK_END / OT_START / OT_END */
    val punch_type: String,

    /** 打卡时间戳（毫秒） */
    val punch_time: Long,

    /** 来源：OCR / MANUAL / BUTTON */
    val source: String,

    /** 关联工时段 ID（计算后回填） */
    val session_id: Long? = null,

    /** OCR 凭证照片路径（App 私有目录） */
    val image_path: String? = null,

    /** OCR 识别原文，便于追溯 */
    val ocr_raw_text: String? = null,

    /** 识别置信度：HIGH / MEDIUM / LOW */
    val confidence: String? = null,

    /** 备注 */
    val remark: String? = null,

    /** 创建时间 */
    val create_time: Long,

    /** 更新时间 */
    val update_time: Long
)
```

**字段说明：**

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| id | Long | 是 | 主键，自增 |
| work_date | String | 是 | 业务归属日，按日切规则计算，非自然日 |
| punch_type | String | 是 | `WORK_START`=上班，`WORK_END`=下班，`OT_START`=加班到，`OT_END`=加班退 |
| punch_time | Long | 是 | 打卡时间戳（毫秒） |
| source | String | 是 | `OCR` / `MANUAL` / `BUTTON` |
| session_id | Long? | 否 | 关联的工时段，计算后回填 |
| image_path | String? | 否 | OCR 凭证照片路径 |
| ocr_raw_text | String? | 否 | 识别原文 |
| confidence | String? | 否 | `HIGH` / `MEDIUM` / `LOW` |
| remark | String? | 否 | 用户备注 |
| create_time | Long | 是 | 创建时间戳 |
| update_time | Long | 是 | 更新时间戳 |

**枚举定义：**

```kotlin
enum class PunchType(val label: String) {
    WORK_START("上班"),
    WORK_END("下班"),
    OT_START("加班到"),
    OT_END("加班退")
}

enum class PunchSource(val label: String) {
    OCR("拍照识别"),
    MANUAL("手动录入"),
    BUTTON("按钮打卡")
}

enum class Confidence(val label: String) {
    HIGH("高"), MEDIUM("中"), LOW("低")
}
```

**业务规则：**
- 同一 `work_date` 下每种 `punch_type` 允许**多条**（V2.1 起支持一日多段），不设唯一约束
- `source=OCR` 时 `image_path` 与 `ocr_raw_text` 应有值
- 删除 `punch_event` 时，若有关联 `work_session` 且该段仅剩此事件，则一并删除该段
- 修改 `punch_event` 后触发对应日期的 `work_session` 重算
- **写入前时间线校验**（应用层 `PunchTimelineValidator`，非数据库约束）：
  - 上班段未闭合时不能再写上班卡；加班段未闭合时不能再写加班卡
  - 上班段未闭合时不能写加班到；加班段未闭合时不能写上班卡
  - 结束卡必须晚于对应开始卡；无开始卡的孤立结束卡按旧数据/分步补录兼容，不做拒绝
  - 同一时刻的结束卡优先于开始卡，允许「上一段结束的同一分钟开始下一段」
  - 校验失败时**不落库**，返回中文提示由 UI 展示

---

## 2.2 工时段表 `work_session`

**功能：** 存储计算后的工时段（正常工作段 / 加班段），供首页与汇总查询。

```kotlin
@Entity(
    tableName = "work_session",
    indices = [
        Index(value = ["work_date"]),
        Index(value = ["work_date", "session_type"])
    ]
)
data class WorkSession(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,

    /** 业务归属日期 */
    val work_date: String,

    /** 段类型：REGULAR-正常工作 / OVERTIME-加班 */
    val session_type: String,

    /** 段开始时间戳 */
    val start_time: Long,

    /** 段结束时间戳 */
    val end_time: Long,

    /** 工作时长（分钟） */
    val work_minutes: Int,

    /** 加班时长（分钟） */
    val overtime_minutes: Int,

    /** 扣除的午休时长（分钟） */
    val rest_minutes: Int,

    /** 是否跨天 */
    val cross_day: Boolean,

    /** 计算式说明文本 */
    val formula: String,

    /** 关联的上班事件 ID（用于详情页定位凭证） */
    val start_event_id: Long? = null,

    /** 关联的下班事件 ID */
    val end_event_id: Long? = null,

    /** 创建时间 */
    val create_time: Long,

    /** 更新时间 */
    val update_time: Long
)
```

**字段说明：**

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| id | Long | 是 | 主键 |
| work_date | String | 是 | 业务归属日 |
| session_type | String | 是 | `REGULAR` / `OVERTIME` |
| start_time | Long | 是 | 段开始时间戳 |
| end_time | Long | 是 | 段结束时间戳 |
| work_minutes | Int | 是 | 工作分钟数 |
| overtime_minutes | Int | 是 | 加班分钟数 |
| rest_minutes | Int | 是 | 扣除的午休分钟数 |
| cross_day | Boolean | 是 | 是否跨天 |
| formula | String | 是 | 计算式文本，详情页展示 |
| start_event_id | Long? | 否 | 起始事件 ID |
| end_event_id | Long? | 否 | 结束事件 ID |

**枚举定义：**

```kotlin
enum class SessionType(val label: String) {
    REGULAR("正常工作"), OVERTIME("加班")
}
```

**业务规则：**
- 由 `punch_event` 聚合计算生成，是**派生数据**，修改事件后必须重算
- `REGULAR` 段：`start_time`=上班，`end_time`=下班
- `OVERTIME` 段：`start_time`=加班到，`end_time`=加班退
- 一日可有多段：多个 `REGULAR` 段与多个 `OVERTIME` 段；配对按时间贪心，先出现的开始卡与随后最近的结束卡成段
- 未闭合的段（有开始卡无结束卡）不写入本表，只在内存中作为「进行中段」参与实时累计与提醒
- 删除某一天的 `punch_event` 后，该天 `work_session` 全量删除再重算，不做增量修补
- 汇总查询基于本表聚合，无需物化表

---

## 2.3 工作规则表 `work_rule`

**功能：** 存储用户的工作时间与加班计算规则（单用户一条）。

```kotlin
@Entity(tableName = "work_rule")
data class WorkRule(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,

    /** 规则名称 */
    val rule_name: String = "默认规则",

    /** 标准上班时间，如 "09:00" */
    val start_time: String,

    /** 标准下班时间，如 "18:00" */
    val end_time: String,

    /** 午休开始，如 "12:00" */
    val rest_start: String?,

    /** 午休结束，如 "13:00" */
    val rest_end: String?,

    /** 加班缓冲时间（分钟） */
    val buffer_minutes: Int,

    /** 日切时间，如 "04:00" */
    val day_cut_time: String,

    /** 工作日（1-7，周一到周日，逗号分隔） */
    val work_days: String,

    /** 是否默认规则 */
    val is_default: Boolean,

    /** 更新时间 */
    val update_time: Long
)
```

**默认值（首次安装初始化）：**

| 字段 | 默认值 |
|------|--------|
| start_time | 09:00 |
| end_time | 18:00 |
| rest_start | 12:00 |
| rest_end | 13:00 |
| buffer_minutes | 30 |
| day_cut_time | 04:00 |
| work_days | 1,2,3,4,5 |

**业务规则：**
- 单用户仅一条默认规则
- 规则修改**不回溯**历史记录，仅对修改后新增记录生效
- 标准工时 = `end_time − start_time`，不再硬编码 480

---

## 2.4 表关系总览

```
work_rule (1) ───────< (N) punch_event   规则被多个事件引用（时间维度追溯）
work_rule (1) ───────< (N) work_session  规则被多个工时段引用

punch_event (N) ────> (1) work_session   多个事件聚合为一个工时段（外键 session_id）
                                         （SET_NULL：段删除后事件保留，可重算）

punch_event ──聚合计算──> work_session   事件变更触发对应日期工时段重算
```

**数据流：**

```
拍照识别 / 手动录入
    │
    └──> punch_event（写入原始事件，含凭证）
              │
              │ 触发重算（按 work_date）
              ↓
        WorkTimeCalculator
              │
              ├──> work_session REGULAR（上班→下班）
              └──> work_session OVERTIME（加班到→加班退）
                        │
                        └──> 首页日历 / 日详情 / 五维度汇总
```

---

# 第三章 DAO 设计

> 以下代码块为**设计期示意**（下划线列名）。实现见 `data/db/Daos.kt`，列名为驼峰，并在此基础上补充了：
> `getByDateOnce` / `getByRange` / `getDatesInRange` / `observeDayPunchCounts` / `observeDayAggregates`
> （日历与汇总一次查询聚合整月，避免 N+1）、`getAll` / `deleteAll` / `insertAll`（备份导出与恢复）。
> 实体类名实际为 `PunchEventEntity` / `WorkSessionEntity` / `WorkRuleEntity`。

## 3.1 PunchEventDao

```kotlin
@Dao
interface PunchEventDao {
    /** 查询某日的全部打卡事件（按时间升序） */
    @Query("SELECT * FROM punch_event WHERE work_date = :date ORDER BY punch_time ASC")
    fun getByDate(date: String): Flow<List<PunchEvent>>

    /** 查询日期区间的全部事件 */
    @Query("SELECT * FROM punch_event WHERE work_date BETWEEN :start AND :end ORDER BY work_date, punch_time")
    fun getByRange(start: String, end: String): Flow<List<PunchEvent>>

    /** 查询某日某类型的事件（用于配对检测） */
    @Query("SELECT * FROM punch_event WHERE work_date = :date AND punch_type = :type")
    suspend fun getByDateAndType(date: String, type: String): List<PunchEvent>

    /** 查询未关联工时段的事件（待重算） */
    @Query("SELECT * FROM punch_event WHERE session_id IS NULL")
    suspend fun getUnassigned(): List<PunchEvent>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(event: PunchEvent): Long

    @Update
    suspend fun update(event: PunchEvent)

    @Delete
    suspend fun delete(event: PunchEvent)

    /** 删除某日全部事件 */
    @Query("DELETE FROM punch_event WHERE work_date = :date")
    suspend fun deleteByDate(date: String)
}
```

## 3.2 WorkSessionDao

```kotlin
@Dao
interface WorkSessionDao {
    /** 查询某日的工时段 */
    @Query("SELECT * FROM work_session WHERE work_date = :date ORDER BY start_time ASC")
    fun getByDate(date: String): Flow<List<WorkSession>>

    /** 查询日期区间的工时段（汇总用） */
    @Query("SELECT * FROM work_session WHERE work_date BETWEEN :start AND :end")
    fun getByRange(start: String, end: String): Flow<List<WorkSession>>

    /** 某日合计（首页日历下方显示用） */
    @Query("""
        SELECT
          COALESCE(SUM(CASE WHEN session_type='REGULAR' THEN work_minutes ELSE 0 END), 0) AS regularMinutes,
          COALESCE(SUM(overtime_minutes), 0) AS overtimeMinutes
        FROM work_session WHERE work_date = :date
    """)
    suspend fun getDaySummary(date: String): DaySummary

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(session: WorkSession): Long

    /** 删除某日全部工时段（重算前清空） */
    @Query("DELETE FROM work_session WHERE work_date = :date")
    suspend fun deleteByDate(date: String)
}
```

## 3.3 WorkRuleDao

```kotlin
@Dao
interface WorkRuleDao {
    @Query("SELECT * FROM work_rule WHERE is_default = 1 LIMIT 1")
    fun getDefault(): Flow<WorkRule?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(rule: WorkRule)

    @Update
    suspend fun update(rule: WorkRule)
}
```

---

# 第四章 数据库实现

## 4.1 Room Database

```kotlin
@Database(
    entities = [PunchEventEntity::class, WorkSessionEntity::class, WorkRuleEntity::class],
    version = 1,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class WorkTimeDatabase : RoomDatabase() {
    abstract fun punchEventDao(): PunchEventDao
    abstract fun workSessionDao(): WorkSessionDao
    abstract fun workRuleDao(): WorkRuleDao

    companion object {
        @Volatile private var INSTANCE: WorkTimeDatabase? = null

        // 文件名取自 Constants.DB_NAME（worktime.db），双检锁单例
        fun get(context: Context): WorkTimeDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext, WorkTimeDatabase::class.java, Constants.DB_NAME
                ).addCallback(DefaultRuleCallback).build().also { INSTANCE = it }
            }
    }
}
```

> 实现要点：`exportSchema = true` 但 schema 目录未纳入版本库；`version = 1` 至今未发生迁移，
> 因此未配置 `Migration`，也未开启 `fallbackToDestructiveMigration()`（破坏性降级会静默清库）。

## 4.2 TypeConverter

```kotlin
class Converters {
    @TypeConverter
    fun fromStringList(value: String): List<Int> =
        value.split(",").filter { it.isNotBlank() }.map { it.toInt() }

    @TypeConverter
    fun toStringList(list: List<Int>): String = list.joinToString(",")
}
```

## 4.3 初始化

首次安装时，在 Database Callback 中初始化默认 `work_rule`：

```kotlin
private val CALLBACK = object : RoomDatabase.Callback() {
    override fun onCreate(db: SupportSQLiteDatabase) {
        super.onCreate(db)
        // 插入默认工作规则（09:00-18:00，午休12:00-13:00，缓冲30，日切04:00，工作日1-5）
    }
}
```

---

# 第五章 设置存储（DataStore，不入库）

以下设置项不进 Room，改用 DataStore `Preferences` 存储（共 18 个键，实现见 `SettingsDataStore.kt`）：

| Key | 类型 | 默认值 | 说明 |
|-----|------|--------|------|
| `calendar_display_mode` | String | `WORK_TIME` | **日历下方显示内容：WORK_TIME-上班时间 / OVERTIME-加班时间，全局统一** |
| `time_format` | String | `HOUR_24` | `HOUR_24` / `HOUR_12` / `SYSTEM` |
| `show_overtime_shade` | Boolean | `true` | 加班色阶开关 |
| `default_input_mode` | String | `OCR` | `OCR` / `MANUAL` |
| `default_punch_type` | String | `WORK_START` | 今日页默认打卡动作：`WORK_START`（上班）/ `OT_START`（加班到）；其余值一律回落为上班 |
| `ocr_auto_fill` | Boolean | `true` | OCR 自动填充开关 |
| `gallery_save_enabled` | Boolean | `true` | 确认保存后把凭证写入系统相册（备份永不含照片） |
| `status_mapping` | String | `""` | 考勤机状态文字映射，序列化字符串；空串用默认映射 |
| `theme_mode` | String | `LIGHT` | `LIGHT` / `DARK` / `MONO`（黑白） |
| `punch_habits` | String | `""` | 习惯打卡列表，序列化字符串 |
| `custom_theme_enabled` | Boolean | `false` | 启用自定义配色（黑白模式不受影响） |
| `custom_light_colors` | String | `""` | 浅色自定义配色，序列化 ARGB 列表；空串用预设 |
| `custom_dark_colors` | String | `""` | 深色自定义配色，同上 |
| `font_scale` | Float | `1.0` | 字号缩放：0.9 / 1.0 / 1.15 |
| `reminder_enabled` | Boolean | `false` | 累计工时提醒总开关 |
| `reminder_type` | String | `OVERTIME` | `OVERTIME` / `REGULAR` / `NET`（净投入） |
| `reminder_minutes` | Int | `420` | 提醒阈值分钟数，30 分钟步进 |
| `last_reminder_key` | String | `""` | 已提醒键 `日期+类型+阈值`，用于同日去重；恢复备份后清空 |

```kotlin
data class AppSettings(
    val calendarDisplayMode: String = CalendarDisplayMode.WORK_TIME.value,
    val timeFormat: String = TimeFormatMode.HOUR_24.value,
    val showOvertimeShade: Boolean = true,
    val defaultInputMode: String = "OCR",
    val defaultPunchType: String = PunchType.WORK_START.name,
    val ocrAutoFill: Boolean = true,
    val gallerySaveEnabled: Boolean = true,
    val statusMapping: String = "",
    val themeMode: String = ThemeMode.LIGHT.value,
    val habits: String = "",
    val customThemeEnabled: Boolean = false,
    val customLightColors: String = "",
    val customDarkColors: String = "",
    val fontScale: Float = 1f,
    val reminderEnabled: Boolean = false,
    val reminderType: String = ReminderType.OVERTIME.value,
    val reminderMinutes: Int = 420,
    val lastReminderKey: String = ""
)
```

> 说明：`calendar_display_mode` 控制首页所有日期格子下方的显示内容，全局统一生效；
> `last_reminder_key` 属运行时状态，导入备份时会被重置，避免恢复后当天不再提醒。
> 早期设计中的 `backup_include_photos` 已移除——备份固定不含照片。

---

# 第六章 备份文件格式

## 6.1 导出结构（JSON）

```json
{
  "version": "2.0",
  "exportTime": 1737350400000,
  "punchEvents": [ { "id": 1, "workDate": "2026-11-20", "punchType": "WORK_START", "punchTime": 1737350400000, "source": "OCR", "sessionId": 1, "imagePath": "evidence/xxx.jpg", "ocrRawText": "...", "confidence": "HIGH", "remark": null, "createTime": 1737350400000, "updateTime": 1737350400000 } ],
  "workSessions": [ { "id": 1, "workDate": "2026-11-20", "sessionType": "REGULAR", "startTime": 1737350400000, "endTime": 1737382800000, "workMinutes": 480, "overtimeMinutes": 0, "restMinutes": 60, "crossDay": false, "formula": "...", "startEventId": 1, "endEventId": 2, "createTime": 0, "updateTime": 0 } ],
  "workRules": [ { "id": 1, "ruleName": "默认规则", "startTime": "09:00", "endTime": "18:00", "restStart": "12:00", "restEnd": "13:00", "bufferMinutes": 30, "dayCutTime": "04:00", "workDays": [1,2,3,4,5], "isDefault": true, "updateTime": 0 } ],
  "settings": { "calendarDisplayMode": "WORK_TIME", "timeFormat": "HOUR_24", "reminderEnabled": false, "reminderType": "OVERTIME", "reminderMinutes": 420, "...": "其余设置项同第五章" }
}
```

> 实际实现采用**驼峰键名**，且固定不含照片字段（`photos` 已移除）。`lastReminderKey` 不写入备份。

## 6.2 导入校验

| 校验项 | 规则 |
|--------|------|
| 版本 | `version` 必须与 `Constants.BACKUP_VERSION`（字符串 `"2.0"`）一致，否则拒绝导入并提示版本不兼容 |
| 完整性 | `punchEvents` / `workSessions` / `workRules` 解析失败或为空时拒绝，避免空库覆盖 |
| 覆盖确认 | 导入前二次确认，提示「将覆盖当前全部数据」 |
| 照片 | 不备份照片；恢复后原 `imagePath` 可能失效，页面显示占位图，不影响工时计算 |
| 提醒状态 | 恢复后重置 `lastReminderKey`，允许当天重新提醒，并重新排定闹钟 |

---

# 第七章 索引与性能

## 7.1 索引清单

Room 按 `Index(value = [...])` 生成默认索引名 `index_<表>_<列1>_<列2>`：

| 表 | 字段 | 用途 |
|----|------|------|
| punch_event | workDate | 首页日历、日详情 |
| punch_event | workDate, punchType | 时间配对、段计算 |
| punch_event | sessionId | 段关联查询 |
| work_session | workDate | 首页日历、日详情 |
| work_session | workDate, sessionType | 汇总查询 |

外键：`punch_event.sessionId → work_session.id`，`onDelete = SET_NULL`（删段不删卡）。

> 列名为驼峰（`workDate` / `punchType` / `sessionType`），与第二章命名约定一致。

## 7.2 性能说明

- 单用户数据量估算：365 天 × 4 事件 ≈ 1460 条/年，Room 本地查询毫秒级
- 无需物化表、无需缓存，直接聚合即可满足性能目标
- 照片存储为文件系统，不入库，DB 仅存路径

---

# 第八章 V1.1 → V2.0 数据迁移说明

由于 V1.1 为云端 MySQL 数据，V2.0 为手机本地 SQLite，**不提供自动迁移**。如需保留历史数据：

1. 从 V1.1 后端导出历史数据为 CSV
2. 通过 V2.0 的"导入恢复"功能，按映射规则转换后导入：

| V1.1 字段 | V2.0 目标 |
|----------|----------|
| `attendance_record.clock_in` | `punch_event(WORK_START)` |
| `attendance_record.clock_out` | `punch_event(WORK_END)` |
| `attendance_record.work_date` | `punch_event.work_date` |
| `attendance_record.source=BUTTON` | `punch_event.source=BUTTON` |
| 加班数据（如有） | `punch_event(OT_START/OT_END)` |

> 说明：V1.1 无独立加班段数据，无法完整迁移四类时间，仅能迁移上下班两段。此为已知限制。

---

**文档编制完成**
