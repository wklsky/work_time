import { dayjs } from './date'

/**
 * 格式化 UTC ISO 时间为显示时间
 * @param utcIsoString UTC 时间字符串
 * @param timeFormat 时间进制 '24H' | '12H'
 * @param timezone 用户时区
 * @param showSeconds 是否显示秒
 */
export function formatDisplayTime(
  utcIsoString: string | null,
  timeFormat: string = '24H',
  timezone: string = 'Asia/Shanghai',
  showSeconds = false,
): string {
  if (!utcIsoString) return '--:--'

  const d = dayjs.utc(utcIsoString).tz(timezone)
  if (!d.isValid()) return '--:--'

  if (timeFormat === '12H') {
    const hour = d.hour()
    const period = hour < 12 ? '上午' : '下午'
    const h12 = hour === 0 ? 12 : hour > 12 ? hour - 12 : hour
    const minute = String(d.minute()).padStart(2, '0')
    if (showSeconds) {
      const second = String(d.second()).padStart(2, '0')
      return `${period}${h12}:${minute}:${second}`
    }
    return `${period}${h12}:${minute}`
  }

  const fmt = showSeconds ? 'HH:mm:ss' : 'HH:mm'
  return d.format(fmt)
}

/**
 * 格式化分钟数为可读时长
 * @param minutes 总分钟数
 * @returns "8小时30分"
 */
export function formatDisplayDuration(minutes: number): string {
  if (minutes < 0) minutes = 0
  const h = Math.floor(minutes / 60)
  const m = minutes % 60
  if (h === 0) return `${m}分`
  if (m === 0) return `${h}小时`
  return `${h}小时${m}分`
}

/**
 * 格式化分钟数为简短形式
 * @param minutes 总分钟数
 * @returns "8h30m"
 */
export function formatDurationShort(minutes: number): string {
  if (minutes < 0) minutes = 0
  const h = Math.floor(minutes / 60)
  const m = minutes % 60
  if (h === 0) return `${m}m`
  if (m === 0) return `${h}h`
  return `${h}h${m}m`
}

/**
 * 将 24 小时制时间字符串转为 12 小时制显示
 * @param time24 "09:00" 或 "18:30"
 */
export function to12HourDisplay(time24: string): string {
  const parts = time24.split(':')
  const hour = parseInt(parts[0], 10)
  const minute = parts[1]
  const period = hour < 12 ? '上午' : '下午'
  const h12 = hour === 0 ? 12 : hour > 12 ? hour - 12 : hour
  return `${period}${h12}:${minute}`
}

/**
 * 格式化 UTC ISO 时间为日期字符串
 */
export function formatDateFromUTC(utcIsoString: string, timezone: string = 'Asia/Shanghai'): string {
  const d = dayjs.utc(utcIsoString).tz(timezone)
  if (!d.isValid()) return ''
  return d.format('YYYY-MM-DD')
}

/**
 * 获取当前时间的 ISO 字符串（用于手动录入）
 */
export function getCurrentISOString(): string {
  return dayjs().utc().toISOString()
}

/**
 * 根据加班时长获取色阶
 */
export function getOvertimeLevel(minutes: number): 'NONE' | 'LOW' | 'MEDIUM' | 'HIGH' {
  if (minutes <= 0) return 'NONE'
  if (minutes <= 60) return 'LOW'
  if (minutes <= 180) return 'MEDIUM'
  return 'HIGH'
}

/**
 * 格式化耗时（秒 → HH:MM:SS）
 */
export function formatElapsedSeconds(seconds: number): string {
  const h = Math.floor(seconds / 3600)
  const m = Math.floor((seconds % 3600) / 60)
  const s = seconds % 60
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${pad(h)}:${pad(m)}:${pad(s)}`
}
