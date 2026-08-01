/**
 * Mock 系统入口
 * 拦截 API 请求并返回模拟数据
 */

import type { ApiResponse } from '@/types/api'

// Mock 处理器映射
type MockHandler = (config: { url: string; method: string; data?: Record<string, unknown>; params?: Record<string, string> }) => unknown

const handlers = new Map<string, MockHandler>()

/** 注册 Mock 处理器 */
export function registerHandler(url: string, handler: MockHandler): void {
  handlers.set(url, handler)
}

/** 获取 Mock 处理器 */
export function getHandler(url: string): MockHandler | undefined {
  return handlers.get(url)
}

/** 创建成功响应 */
export function mockSuccess<T>(data: T): ApiResponse<T> {
  return {
    code: 200,
    message: 'success',
    data,
    timestamp: Date.now(),
  }
}

/** 创建错误响应 */
export function mockError(code: number, message: string, errors?: Array<{ field: string; message: string }>): ApiResponse<null> {
  return {
    code,
    message,
    data: null,
    timestamp: Date.now(),
  }
}

// 批量导入所有处理器
import './user'
import './attendance'
import './summary'
import './setting'
import './export'

// 动态路由匹配（如 /export/123）
function matchUrl(requestUrl: string): MockHandler | undefined {
  // 先精确匹配
  if (handlers.has(requestUrl)) return handlers.get(requestUrl)

  // 再尝试正则匹配
  for (const [pattern, handler] of handlers) {
    if (pattern.includes(':')) {
      const regex = new RegExp('^' + pattern.replace(/:[^/]+/g, '[^/]+') + '$')
      if (regex.test(requestUrl)) return handler
    }
  }
  return undefined
}

// 将处理器暴露到 window 供 api/index.ts 的 mockRequest 使用
if (typeof globalThis !== 'undefined') {
  ;(globalThis as any).__mockHandlers = Object.fromEntries(handlers)
  ;(globalThis as any).__mockMatchUrl = matchUrl
}
