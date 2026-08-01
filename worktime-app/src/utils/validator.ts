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
 * 验证手动录入时间
 */
export function validateManualTime(clockIn: string, clockOut?: string): ValidationResult & { crossDay?: boolean } {
  if (!clockIn) return { valid: false, message: '请输入开始时间' }

  const clockInDate = new Date(clockIn)
  if (isNaN(clockInDate.getTime())) return { valid: false, message: '开始时间格式不正确' }

  // 不允许未来时间
  if (clockInDate.getTime() > Date.now()) return { valid: false, message: '开始时间不能晚于当前时间' }

  if (clockOut) {
    const clockOutDate = new Date(clockOut)
    if (isNaN(clockOutDate.getTime())) return { valid: false, message: '结束时间格式不正确' }

    const diffMinutes = (clockOutDate.getTime() - clockInDate.getTime()) / 60000

    // 跨天：结束时间小于开始时间
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
