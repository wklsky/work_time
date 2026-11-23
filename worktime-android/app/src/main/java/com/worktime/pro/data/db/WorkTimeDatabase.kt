/**
 * @Author: wj
 * @FilePath: worktime-android/app/src/main/java/com/worktime/pro/data/db/WorkTimeDatabase.kt
 * @Description: Room 数据库持有类，首次创建时初始化默认工作规则
 */
package com.worktime.pro.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.worktime.pro.util.Constants

@Database(
    entities = [PunchEventEntity::class, WorkSessionEntity::class, WorkRuleEntity::class],
    version = 2,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class WorkTimeDatabase : RoomDatabase() {
    abstract fun punchEventDao(): PunchEventDao
    abstract fun workSessionDao(): WorkSessionDao
    abstract fun workRuleDao(): WorkRuleDao

    companion object {
        @Volatile
        private var INSTANCE: WorkTimeDatabase? = null

        /**
         * 1 → 2：移除 punch_event 表中 OCR 遗留的三列（imagePath / ocrRawText / confidence），
         * 并把历史 source='OCR' 统一改写为 'MANUAL'。
         * 因低版本 SQLite 不支持 ALTER TABLE DROP COLUMN，采用「建新表 → 复制 → 删旧 → 改名」的通用方式。
         */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `punch_event_new` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `workDate` TEXT NOT NULL,
                        `punchType` TEXT NOT NULL,
                        `punchTime` INTEGER NOT NULL,
                        `source` TEXT NOT NULL,
                        `sessionId` INTEGER,
                        `remark` TEXT,
                        `createTime` INTEGER NOT NULL,
                        `updateTime` INTEGER NOT NULL,
                        FOREIGN KEY(`sessionId`) REFERENCES `work_session`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    INSERT INTO `punch_event_new` (`id`, `workDate`, `punchType`, `punchTime`, `source`, `sessionId`, `remark`, `createTime`, `updateTime`)
                    SELECT `id`, `workDate`, `punchType`, `punchTime`,
                           CASE WHEN `source` = 'OCR' THEN 'MANUAL' ELSE `source` END,
                           `sessionId`, `remark`, `createTime`, `updateTime`
                    FROM `punch_event`
                    """.trimIndent()
                )
                db.execSQL("DROP TABLE `punch_event`")
                db.execSQL("ALTER TABLE `punch_event_new` RENAME TO `punch_event`")
                // 旧版 SQLite 的 RENAME 不会同步 sqlite_sequence 表名，需手动修正，否则后续自增主键可能冲突
                db.execSQL("UPDATE sqlite_sequence SET name = 'punch_event' WHERE name = 'punch_event_new'")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_punch_event_workDate` ON `punch_event` (`workDate`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_punch_event_workDate_punchType` ON `punch_event` (`workDate`, `punchType`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_punch_event_sessionId` ON `punch_event` (`sessionId`)")
            }
        }

        fun get(context: Context): WorkTimeDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: build(context.applicationContext).also { INSTANCE = it }
            }

        private fun build(context: Context): WorkTimeDatabase =
            Room.databaseBuilder(context, WorkTimeDatabase::class.java, Constants.DB_NAME)
                .addMigrations(MIGRATION_1_2)
                .addCallback(object : Callback() {
                    /**
                     * 首次安装写入默认规则，避免引擎因无规则而静默跳过计算（旧版曾有此缺陷）。
                     * 工作日默认周一至周五，用户可在设置中调整。
                     */
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        super.onCreate(db)
                        db.execSQL(
                            """
                            INSERT INTO work_rule
                            (ruleName, startTime, endTime, restStart, restEnd, bufferMinutes, dayCutTime, workDays, isDefault, updateTime)
                            VALUES (?, ?, ?, ?, ?, ?, ?, ?, 1, ?)
                            """.trimIndent(),
                            arrayOf(
                                "默认规则",
                                Constants.DEFAULT_START_TIME,
                                Constants.DEFAULT_END_TIME,
                                Constants.DEFAULT_REST_START,
                                Constants.DEFAULT_REST_END,
                                Constants.DEFAULT_BUFFER_MINUTES,
                                Constants.DEFAULT_DAY_CUT_TIME,
                                Constants.DEFAULT_WORK_DAYS,
                                System.currentTimeMillis()
                            )
                        )
                    }
                })
                .build()
    }
}
