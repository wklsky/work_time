import dayjs from 'dayjs'
import utc from 'dayjs/plugin/utc'
import timezone from 'dayjs/plugin/timezone'
import 'dayjs/locale/zh-cn'

dayjs.extend(utc)
dayjs.extend(timezone)
dayjs.locale('zh-cn')

const WEEKDAY_NAMES = ['星期日', '星期一', '星期二', '星期三', '星期四', '星期五', '星期六']
const WEEKDAY_SHORT_NAMES = ['周日', '周一', '周二', '周三', '周四', '周五', '周六']

/**
 * 获取今天的日期字符串
 */
export function getTodayDateString(): string {
  return dayjs().format('YYYY-MM-DD')
}

/**
 * 获取当前 UTC ISO 字符串
 */
export function nowUTC(): string {
  return dayjs().utc().toISOString()
}

/**
 * 格式化日期为中文格式
 */
export function formatDateString(dateStr: string, withWeekday = false): string {
  const d = dayjs(dateStr)
  if (!d.isValid()) return dateStr
  const datePart = d.format('YYYY年MM月DD日')
  if (withWeekday) {
    const wd = d.day()
    return `${datePart} ${WEEKDAY_NAMES[wd]}`
  }
  return datePart
}

/**
 * 获取星期名称
 */
export function getWeekdayName(dateOrNum: string | number, short = true): string {
  const names = short ? WEEKDAY_SHORT_NAMES : WEEKDAY_NAMES
  if (typeof dateOrNum === 'string') {
    const d = dayjs(dateOrNum)
    return d.isValid() ? names[d.day()] : ''
  }
  return names[dateOrNum] || ''
}

/**
 * 获取日期对应的星期数字 (0=周日, 1=周一...6=周六)
 */
export function getWeekdayNum(dateStr: string): number {
  return dayjs(dateStr).day()
}

/**
 * 获取周一起始日期
 */
export function getWeekStart(dateStr: string): string {
  const d = dayjs(dateStr)
  const day = d.day()
  const diff = day === 0 ? -6 : 1 - day
  return d.add(diff, 'day').format('YYYY-MM-DD')
}

/**
 * 获取周日结束日期
 */
export function getWeekEnd(dateStr: string): string {
  const d = dayjs(dateStr)
  const day = d.day()
  const diff = day === 0 ? 0 : 7 - day
  return d.add(diff, 'day').format('YYYY-MM-DD')
}

/**
 * 获取月份第一天
 */
export function getMonthStart(year: number, month: number): string {
  return dayjs(`${year}-${String(month).padStart(2, '0')}-01`).format('YYYY-MM-DD')
}

/**
 * 获取月份最后一天
 */
export function getMonthEnd(year: number, month: number): string {
  return dayjs(`${year}-${String(month).padStart(2, '0')}-01`).endOf('month').format('YYYY-MM-DD')
}

/**
 * 日期加减天数
 */
export function addDays(dateStr: string, n: number): string {
  return dayjs(dateStr).add(n, 'day').format('YYYY-MM-DD')
}

export function subDays(dateStr: string, n: number): string {
  return dayjs(dateStr).subtract(n, 'day').format('YYYY-MM-DD')
}

/**
 * 月份加减
 */
export function addMonths(year: number, month: number, n: number): { year: number; month: number } {
  const d = dayjs(`${year}-${String(month).padStart(2, '0')}-01`).add(n, 'month')
  return { year: d.year(), month: d.month() + 1 }
}

export function subMonths(year: number, month: number, n: number): { year: number; month: number } {
  return addMonths(year, month, -n)
}

/**
 * 计算两个日期之间的天数
 */
export function daysBetween(start: string, end: string): number {
  return dayjs(end).diff(dayjs(start), 'day') + 1
}

/**
 * 判断日期是否是今天
 */
export function isToday(dateStr: string): boolean {
  return dayjs(dateStr).isSame(dayjs(), 'day')
}

/**
 * 判断是否是当前月份
 */
export function isCurrentMonth(year: number, month: number): boolean {
  const now = dayjs()
  return now.year() === year && now.month() + 1 === month
}

/**
 * 获取月份天数
 */
export function getDaysInMonth(year: number, month: number): number {
  return dayjs(`${year}-${String(month).padStart(2, '0')}-01`).daysInMonth()
}

/**
 * 获取月份第一天是星期几 (1=周一, 7=周日)
 */
export function getFirstDayOfWeek(year: number, month: number): number {
  const d = dayjs(`${year}-${String(month).padStart(2, '0')}-01`)
  const dow = d.day()
  return dow === 0 ? 7 : dow
}

/**
 * 获取当前季度范围
 */
export function getCurrentQuarter(): { start: string; end: string } {
  const now = dayjs()
  const quarter = Math.floor(now.month() / 3)
  const startMonth = quarter * 3
  const start = now.month(startMonth).startOf('month').format('YYYY-MM-DD')
  const end = now.month(startMonth + 2).endOf('month').format('YYYY-MM-DD')
  return { start, end }
}

export { dayjs }
