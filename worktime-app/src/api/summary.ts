import { http } from './index'
import type { DaySummary, WeekSummary, MonthSummary, YearSummary, CustomSummary } from '@/types/summary'

const BASE = '/summary'

export const summaryApi = {
  /** 日汇总 */
  getDay(date: string): Promise<DaySummary> {
    return http.get(`${BASE}/day`, { date })
  },

  /** 周汇总 */
  getWeek(date: string): Promise<WeekSummary> {
    return http.get(`${BASE}/week`, { date })
  },

  /** 月汇总 */
  getMonth(year: number, month: number): Promise<MonthSummary> {
    return http.get(`${BASE}/month`, { year: String(year), month: String(month) })
  },

  /** 年汇总 */
  getYear(year: number): Promise<YearSummary> {
    return http.get(`${BASE}/year`, { year: String(year) })
  },

  /** 自定义区间汇总 */
  getCustom(startDate: string, endDate: string): Promise<CustomSummary> {
    return http.get(`${BASE}/custom`, { startDate, endDate })
  },

  /** 快捷区间汇总 */
  getQuick(preset: string): Promise<CustomSummary> {
    return http.get(`${BASE}/quick`, { preset })
  },
}
