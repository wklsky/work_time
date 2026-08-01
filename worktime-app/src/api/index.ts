import { useAuthStore } from '@/stores/auth'
import { API_TIMEOUT } from '@/types/common'
import type { ApiResponse } from '@/types/api'

/** 模拟请求延迟 (ms) */
const MOCK_DELAY = 300

/** 请求配置 */
interface RequestConfig {
  url: string
  method?: 'GET' | 'POST' | 'PUT' | 'DELETE'
  data?: Record<string, unknown>
  params?: Record<string, string>
  header?: Record<string, string>
  showLoading?: boolean
  showError?: boolean
}

/** 基础请求函数 */
async function baseRequest<T>(config: RequestConfig): Promise<T> {
  const { url, method = 'GET', data, params, header = {} } = config

  // 获取 token
  const authStore = useAuthStore()
  const token = authStore.token

  // 构建请求头
  const headers: Record<string, string> = {
    'Content-Type': 'application/json',
    ...header,
  }

  if (token) {
    headers['Authorization'] = `Bearer ${token}`
  }

  // 构建完整 URL
  let fullUrl = url
  if (params && Object.keys(params).length > 0) {
    const query = Object.entries(params)
      .map(([k, v]) => `${k}=${encodeURIComponent(v)}`)
      .join('&')
    fullUrl += `?${query}`
  }

  // 如果启用 Mock，走 Mock 处理器
  // #ifdef H5
  if (import.meta.env.VITE_USE_MOCK === 'true') {
    return mockRequest<T>(config)
  }
  // #endif

  // 实际请求
  return new Promise<T>((resolve, reject) => {
    uni.request({
      url: `${import.meta.env.VITE_API_BASE_URL || ''}${fullUrl}`,
      method,
      data,
      header: headers,
      timeout: API_TIMEOUT,
      success: (res) => {
        const response = res.data as ApiResponse<T>
        if (response.code === 200) {
          resolve(response.data)
        } else if (response.code === 401) {
          authStore.logout()
          uni.reLaunch({ url: '/pages/login/index' })
          reject(new Error(response.message || '认证失效'))
        } else {
          reject(new Error(response.message || '请求失败'))
        }
      },
      fail: (err) => {
        console.error('[API] Request failed:', fullUrl, err)
        reject(new Error('网络请求失败，请检查网络连接'))
      },
    })
  })
}

/** Mock 请求处理 */
async function mockRequest<T>(config: RequestConfig): Promise<T> {
  return new Promise<T>((resolve, _reject) => {
    setTimeout(() => {
      // Mock 处理器由 mock/index.ts 注册到全局
      const matchUrl = (globalThis as any).__mockMatchUrl
      const handler = matchUrl
        ? matchUrl(config.url)
        : (globalThis as any).__mockHandlers?.[config.url]
      if (handler) {
        const result = handler(config)
        resolve(result as T)
      } else {
        console.warn(`[Mock] No handler for ${config.url}`)
        resolve({} as T)
      }
    }, MOCK_DELAY)
  })
}

/** 导出 HTTP 方法 */
export const http = {
  get<T>(url: string, params?: Record<string, string>): Promise<T> {
    return baseRequest<T>({ url, method: 'GET', params })
  },
  post<T>(url: string, data?: Record<string, unknown>): Promise<T> {
    return baseRequest<T>({ url, method: 'POST', data })
  },
  put<T>(url: string, data?: Record<string, unknown>): Promise<T> {
    return baseRequest<T>({ url, method: 'PUT', data })
  },
  delete<T>(url: string): Promise<T> {
    return baseRequest<T>({ url, method: 'DELETE' })
  },
}
