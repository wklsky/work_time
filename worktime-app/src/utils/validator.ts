/**
 * 表单验证工具
 */

import { MAX_CUSTOM_RANGE_DAYS, MAX_SINGLE_WORK_MINUTES } from '@/types/common'

export interface ValidationResult {
  valid: boolean
  message: string
}

/**
 * 验证手机号（中国大陆）
 */
export function validatePhone(phone: string): ValidationResult {
  if (!phone) return { valid: false, message: '请输入手机号' }
  if (!/^1[3-9]\d{9}$/.test(phone)) return { valid: false, message: '请输入正确的手机号' }
  return { valid: true, message: '' }
}

/**
 * 验证密码强度
 */
export function validatePassword(password: string): ValidationResult {
  if (!password) return { valid: false, message: '请输入密码' }
  if (password.length < 8 || password.length > 20) return { valid: false, message: '密码长度需为 8-20 位' }
  if (!/[A-Z]/.test(password)) return { valid: false, message: '密码必须包含大写字母' }
  if (!/[a-z]/.test(password)) return { valid: false, message: '密码必须包含小写字母' }
  if (!/[0-9]/.test(password)) return { valid: false, message: '密码必须包含数字' }
  return { valid: true, message: '' }
}

/**
 * 验证用户名
 */
export function validateUsername(username: string): ValidationResult {
  if (!username) return { valid: false, message: '请输入用户名' }
  if (username.length < 4 || username.length > 20) return { valid: false, message: '用户名长度需为 4-20 位' }
  if (!/^[a-zA-Z0-9_]+$/.test(username)) return { valid: false, message: '用户名只能包含字母、数字和下划线' }
  return { valid: true, message: '' }
}

/**
 * 校验纯时间字符串 "HH:mm" 是否为合法时刻（picker mode=time 的返回形态）
 */
function parseTimeOnly(time: string): { hour: number; minute: number } | null {
  // 兼容完整日期串：仅取末尾的 "HH:mm" 部分（后端 clockIn 在提交时拼成完整 ISO）
  const match = time.match(/(\d{1,2}):(\d{2})/)
  if (!match) return null
  const hour = Number(match[1])
  const minute = Number(match[2])
  if (hour < 0 || hour > 23 || minute < 0 || minute > 59) return null
  return { hour, minute }
}

/**
 * 验证手动录入时间
 * 注意：手动模式下 picker 返回的是 "HH:mm" 纯时刻串，必须用时刻解析而非 new Date()，
 * 否则 new Date("09:30") 解析为 Invalid Date，误报"时间格式不正确"
 */
export function validateManualTime(clockIn: string, clockOut?: string): ValidationResult & { crossDay?: boolean } {
  if (!clockIn) return { valid: false, message: '请输入开始时间' }

  const inTime = parseTimeOnly(clockIn)
  if (!inTime) return { valid: false, message: '开始时间格式不正确' }

  // 不允许未来时间（以今日该时刻为基准比较，避免跨时区误差）
  const now = new Date()
  const startToday = new Date(now.getFullYear(), now.getMonth(), now.getDate(), inTime.hour, inTime.minute)
  if (startToday.getTime() > Date.now()) return { valid: false, message: '开始时间不能晚于当前时间' }

  if (clockOut) {
    const outTime = parseTimeOnly(clockOut)
    if (!outTime) return { valid: false, message: '结束时间格式不正确' }

    const inMinutes = inTime.hour * 60 + inTime.minute
    const outMinutes = outTime.hour * 60 + outTime.minute
    const diffMinutes = outMinutes - inMinutes

    // 跨天：结束时刻小于开始时刻
    const isCrossDay = diffMinutes < 0

    if (isCrossDay) {
      const adjustedDiff = diffMinutes + 24 * 60
      if (adjustedDiff > MAX_SINGLE_WORK_MINUTES) {
        return { valid: false, message: `单段时长不能超过 ${MAX_SINGLE_WORK_MINUTES / 60} 小时` }
      }
      return { valid: true, message: '', crossDay: true }
    }

    if (diffMinutes <= 0) return { valid: false, message: '结束时间必须晚于开始时间（跨天除外）' }

    if (diffMinutes > MAX_SINGLE_WORK_MINUTES) {
      return { valid: false, message: `单段时长不能超过 ${MAX_SINGLE_WORK_MINUTES / 60} 小时` }
    }
  }

  return { valid: true, message: '', crossDay: false }
}

/**
 * 验证日期区间
 */
export function validateDateRange(
  startDate: string,
  endDate: string,
  maxDays: number = MAX_CUSTOM_RANGE_DAYS,
): ValidationResult {
  if (!startDate || !endDate) return { valid: false, message: '请选择日期区间' }

  const start = new Date(startDate)
  const end = new Date(endDate)

  if (isNaN(start.getTime()) || isNaN(end.getTime())) {
    return { valid: false, message: '日期格式不正确' }
  }

  if (end < start) return { valid: false, message: '结束日期不能早于开始日期' }

  const days = Math.ceil((end.getTime() - start.getTime()) / (1000 * 60 * 60 * 24)) + 1
  if (days > maxDays) return { valid: false, message: `日期区间不能超过 ${maxDays} 天` }

  return { valid: true, message: '' }
}

/**
 * 验证时区
 */
export function validateTimezone(tz: string): ValidationResult {
  if (!tz) return { valid: false, message: '请选择时区' }
  if (!/^[A-Za-z_]+\/[A-Za-z_]+$/.test(tz)) return { valid: false, message: '无效的时区格式' }
  return { valid: true, message: '' }
}
