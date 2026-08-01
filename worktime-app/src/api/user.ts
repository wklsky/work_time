import { http } from './index'
import type { LoginRequest, LoginResponse, UserInfo, RegisterRequest, UpdateProfileRequest, ChangePasswordRequest } from '@/types/auth'

const BASE = '/user'

export const userApi = {
  /** 用户注册 */
  register(data: RegisterRequest): Promise<{ userId: number; username: string }> {
    return http.post(`${BASE}/register`, data as unknown as Record<string, unknown>)
  },

  /** 用户登录 */
  login(data: LoginRequest): Promise<LoginResponse> {
    return http.post(`${BASE}/login`, data as unknown as Record<string, unknown>)
  },

  /** 获取用户信息 */
  getProfile(): Promise<UserInfo> {
    return http.get(`${BASE}/profile`)
  },

  /** 更新用户信息 */
  updateProfile(data: UpdateProfileRequest): Promise<void> {
    return http.put(`${BASE}/profile`, data as unknown as Record<string, unknown>)
  },

  /** 修改密码 */
  changePassword(data: ChangePasswordRequest): Promise<void> {
    return http.put(`${BASE}/password`, data as unknown as Record<string, unknown>)
  },

  /** 注销 */
  logout(): Promise<void> {
    return http.post(`${BASE}/logout`)
  },
}
