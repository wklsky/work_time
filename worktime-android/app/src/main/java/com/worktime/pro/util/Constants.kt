/**
 * @Author: wj
 * @FilePath: worktime-android/app/src/main/java/com/worktime/pro/util/Constants.kt
 * @Description: 全局常量，集中管理标准工时、缓冲时长等魔法数字，供引擎与设置默认值统一引用
 */
package com.worktime.pro.util

object Constants {
    const val DEFAULT_START_TIME = "09:00"
    const val DEFAULT_END_TIME = "18:00"
    const val DEFAULT_REST_START = "12:00"
    const val DEFAULT_REST_END = "13:00"
    const val DEFAULT_DAY_CUT_TIME = "04:00"
    const val DEFAULT_WORK_DAYS = "1,2,3,4,5"

    /** 加班缓冲默认 30 分钟：下班后半小时内不计加班，属企业常见宽限规则 */
    const val DEFAULT_BUFFER_MINUTES = 30

    /** 标准工时 480 分钟（8 小时），仅在无规则时兜底，正常应从规则动态计算 */
    const val STANDARD_MINUTES_FALLBACK = 480

    const val DB_NAME = "worktime.db"
    const val EVIDENCE_DIR = "evidence"
    const val BACKUP_DIR = "backup"
    const val BACKUP_VERSION = "2.0"
    const val MAX_CUSTOM_RANGE_DAYS = 366
}
