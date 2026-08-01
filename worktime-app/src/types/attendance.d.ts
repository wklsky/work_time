/** 打卡记录状态 */
export type ClockStatus = 'NOT_STARTED' | 'WORKING' | 'COMPLETED'

/** 记录来源 */
export type RecordSource = 'BUTTON' | 'MANUAL'

/** 记录状态 */
export type RecordStatus = 'PENDING' | 'COMPLETED'

/** 加班色阶 */
export type OvertimeLevel = 'NONE' | 'LOW' | 'MEDIUM' | 'HIGH'

/** 打卡记录 */
export interface AttendanceRecord {
  id: number
  userId: number
  workDate: string
  clockIn: string
  clockOut: string | null
  workMinutes: number
  overtimeMinutes: number
  source: RecordSource
  crossDay: number
  ruleId?: number
  remark: string | null
  status: RecordStatus
  createTime: string
  updateTime?: string
}

/** 工时明细分解 */
export interface WorktimeBreakdown {
  regularWorkMinutes: number
  restMinutes: number
  overtimeMinutes: number
  formula: string
}

/** 结束工作响应 */
export interface ClockOutResult {
  id: number
  clockIn: string
  clockOut: string
  workMinutes: number
  overtimeMinutes: number
  crossDay: number
  status: string
  breakdown: WorktimeBreakdown
  todayAccumulated: TodayAccumulated
}

/** 当日累计 */
export interface TodayAccumulated {
  totalWorkMinutes: number
  totalOvertimeMinutes: number
  segmentCount: number
}

/** 首页今日数据 */
export interface TodayData {
  dateInfo: DateInfo
  clockStatus: ClockStatus
  currentRecord: AttendanceRecord | null
  todaySummary: TodayAccumulated | null
  todayRecords: AttendanceRecord[]
  userSetting: TodayUserSetting
}

export interface DateInfo {
  date: string
  weekday: string
  weekdayNum: number
  isToday: boolean
}

export interface TodayUserSetting {
  showWorktimeAfterClockout: boolean
  timeFormat: string
  homeCalendarView: string
  defaultInputMode: string
}

/** 日历视图类型 */
export type CalendarViewType = 'MONTH' | 'WEEK' | 'TODAY'

/** 月视图日历数据 */
export interface MonthCalendarData {
  view: 'MONTH'
  year: number
  month: number
  dayCount: number
  firstDayOfWeek: number
  days: CalendarDay[]
}

/** 周视图日历数据 */
export interface WeekCalendarData {
  view: 'WEEK'
  weekStart: string
  weekEnd: string
  days: WeekDay[]
  weekTotal: WeekTotal
}

/** 今日视图数据 */
export interface TodayCalendarData {
  view: 'TODAY'
  date: string
  weekday: string
  timeline: TimelineConfig
  records: AttendanceRecord[]
  currentStatus: ClockStatus
  todayAccumulated: TodayAccumulated | null
}

export interface CalendarDay {
  date: string
  weekday: number
  hasRecord: boolean
  workMinutes: number
  overtimeMinutes: number
  overtimeLevel: OvertimeLevel | null
  isToday: boolean
  recordCount: number
}

export interface WeekDay {
  date: string
  weekday: number
  weekdayName: string
  hasRecord: boolean
  workMinutes: number
  overtimeMinutes: number
  firstClockIn: string | null
  lastClockOut: string | null
  isToday: boolean
  workPercentage: number
  records: AttendanceRecord[]
}

export interface WeekTotal {
  workMinutes: number
  overtimeMinutes: number
  attendanceDays: number
  avgDailyMinutes: number
}

export interface TimelineConfig {
  standardStart: string
  standardEnd: string
  restStart: string
  restEnd: string
}

/** 手动录入请求 */
export interface ManualRecordRequest {
  clockIn: string
  clockOut?: string
  remark?: string
}

/** 修改记录请求 */
export interface UpdateRecordRequest {
  clockIn?: string
  clockOut?: string
  remark?: string
}
