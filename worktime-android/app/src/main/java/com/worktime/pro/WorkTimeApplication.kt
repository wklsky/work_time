/**
 * @Author: wj
 * @FilePath: worktime-android/app/src/main/java/com/worktime/pro/WorkTimeApplication.kt
 * @Description: 应用入口，初始化数据库与依赖容器，全局提供 Repository 实例
 */
package com.worktime.pro

import android.app.Application
import com.worktime.pro.data.datastore.SettingsDataStore
import com.worktime.pro.data.db.WorkTimeDatabase
import com.worktime.pro.domain.backup.BackupManager
import com.worktime.pro.domain.repository.WorkTimeRepository

class WorkTimeApplication : Application() {
    lateinit var repository: WorkTimeRepository
        private set
    lateinit var settings: SettingsDataStore
        private set
    lateinit var backupManager: BackupManager
        private set

    override fun onCreate() {
        super.onCreate()
        val database = WorkTimeDatabase.get(this)
        repository = WorkTimeRepository(
            punchDao = database.punchEventDao(),
            sessionDao = database.workSessionDao(),
            ruleDao = database.workRuleDao()
        )
        settings = SettingsDataStore(this)
        backupManager = BackupManager(
            context = this,
            punchDao = database.punchEventDao(),
            sessionDao = database.workSessionDao(),
            ruleDao = database.workRuleDao(),
            settingsDataStore = settings
        )
    }
}
