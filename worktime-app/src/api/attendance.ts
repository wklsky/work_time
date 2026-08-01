import { http } from './index'
import type {
  TodayData,
  AttendanceRecord,
  ClockOutResult,
  ManualRecordRequest,
  UpdateRecordRequest,
  CalendarViewType,
  MonthCalendarData,
  WeekCalendarData,
  TodayCalendarData,
  DayRecord,
} from '@/types/attendance'

const BASE = '/attendance'

export const attendanceApi = {
  /** 开始工作 */
  clockIn(remark?: string): Promise<AttendanceRecord> {
    return http.post(`${BASE}/clock-in`, { remark })
  },

  /** 结束工作 */
  clockOut(recordId?: number, remark?: string): Promise<ClockOutResult> {
    const data: Record<string, unknown> = {}
    if (recordId) data.recordId = recordId
    if (remark) data.remark = remark
    return http.post(`${BASE}/clock-out`, data)
  },

  /** 手动录入 */
  manualRecord(data: ManualRecordRequest): Promise<ClockOutResult | AttendanceRecord> {
    return http.post(`${BASE}/manual`, data as unknown as Record<string, unknown>)
  },

  /** 修改记录 */
  updateRecord(id: number, data: UpdateRecordRequest): Promise<AttendanceRecord> {
    return http.put(`${BASE}/${id}`, data as unknown as Record<string, unknown>)
  },

  /** 获取指定日期的记录列表 */
  getRecords(date: string): Promise<{ date: string; records: DayRecord[]; dayTotal: { workMinutes: number; overtimeMinutes: number; segmentCount: number } }> {
    return http.get(`${BASE}/records`, { date })
  },

  /** 获取日历数据 */
  getCalendar(view: CalendarViewType, date: string): Promise<MonthCalendarData | WeekCalendarData | TodayCalendarData> {
    return http.get(`${BASE}/calendar`, { view, date })
  },
}

export const homeApi = {
  /** 获取首页今日数据 */
  getToday(): Promise<TodayData> {
    return http.get('/index/today')
  },
}
