/**
 * 类型安全的 uni.storage 封装
 */

const STORAGE_PREFIX = 'wt_'

export function getItem<T>(key: string, defaultValue?: T): T | null {
  try {
    const fullKey = STORAGE_PREFIX + key
    const value = uni.getStorageSync(fullKey)
    if (value === '' || value === undefined || value === null) {
      return defaultValue ?? null
    }
    try {
      return JSON.parse(value) as T
    } catch {
      return value as unknown as T
    }
  } catch {
    return defaultValue ?? null
  }
}

export function setItem<T>(key: string, value: T): void {
  try {
    const fullKey = STORAGE_PREFIX + key
    const serialized = typeof value === 'string' ? value : JSON.stringify(value)
    uni.setStorageSync(fullKey, serialized)
  } catch (e) {
    console.error('[Storage] setItem failed:', key, e)
  }
}

export function removeItem(key: string): void {
  try {
    const fullKey = STORAGE_PREFIX + key
    uni.removeStorageSync(fullKey)
  } catch (e) {
    console.error('[Storage] removeItem failed:', key, e)
  }
}

export function clearAll(): void {
  try {
    uni.clearStorageSync()
  } catch (e) {
    console.error('[Storage] clearAll failed:', e)
  }
}

/** 常用存储键 */
export const STORAGE_KEYS = {
  TOKEN: 'token',
  USER_INFO: 'user_info',
  SETTINGS: 'settings',
  WORK_RULE: 'work_rule',
} as const
