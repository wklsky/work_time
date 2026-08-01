import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import type { UserInfo } from '@/types/auth'
import { userApi } from '@/api/user'
import { setItem, getItem, removeItem } from '@/utils/storage'
import { STORAGE_KEYS } from '@/utils/storage'

export const useAuthStore = defineStore('auth', () => {
  // ========== State ==========
  const token = ref<string | null>(null)
  const tokenType = ref<string>('Bearer')
  const expiresIn = ref<number>(0)
  const userInfo = ref<UserInfo | null>(null)
  const isLoggedIn = ref(false)

  // ========== Getters ==========
  const userId = computed(() => userInfo.value?.userId ?? null)
  const timezone = computed(() => userInfo.value?.timezone ?? 'Asia/Shanghai')
  const displayName = computed(() => userInfo.value?.nickname || userInfo.value?.username || '未命名')

  // ========== Actions ==========

  /** 从本地缓存初始化认证状态 */
  async function initAuth(): Promise<void> {
    try {
      const savedToken = getItem<string>(STORAGE_KEYS.TOKEN)
      const savedUser = getItem<UserInfo>(STORAGE_KEYS.USER_INFO)

      if (savedToken) {
        token.value = savedToken
        isLoggedIn.value = true
        if (savedUser) {
          userInfo.value = savedUser
        }
        // 尝试从服务端刷新用户信息
        try {
          await fetchProfile()
        } catch {
          // 网络失败使用本地缓存
        }
      }
    } catch (e) {
      console.error('[AuthStore] initAuth failed:', e)
    }
  }

  /** 登录 */
  async function login(phone: string, password: string): Promise<void> {
    const result = await userApi.login({ phone, password })
    token.value = result.token
    tokenType.value = result.tokenType
    expiresIn.value = result.expiresIn
    userInfo.value = result.userInfo
    isLoggedIn.value = true

    // 持久化
    setItem(STORAGE_KEYS.TOKEN, result.token)
    setItem(STORAGE_KEYS.USER_INFO, result.userInfo)
  }

  /** 注册 */
  async function register(username: string, password: string, phone: string): Promise<void> {
    await userApi.register({ username, password, phone })
    // 注册成功后自动登录
    await login(phone, password)
  }

  /** 注销 */
  async function logout(): Promise<void> {
    try {
      await userApi.logout()
    } catch {
      // 即使服务器请求失败也清除本地状态
    } finally {
      token.value = null
      userInfo.value = null
      isLoggedIn.value = false
      removeItem(STORAGE_KEYS.TOKEN)
      removeItem(STORAGE_KEYS.USER_INFO)
    }
  }

  /** 获取用户信息 */
  async function fetchProfile(): Promise<void> {
    const info = await userApi.getProfile()
    userInfo.value = info
    setItem(STORAGE_KEYS.USER_INFO, info)
  }

  /** 更新用户信息 */
  async function updateProfile(data: Partial<UserInfo>): Promise<void> {
    await userApi.updateProfile({
      nickname: data.nickname ?? undefined,
      avatar: data.avatar ?? undefined,
      timezone: data.timezone ?? undefined,
    })
    if (userInfo.value) {
      Object.assign(userInfo.value, data)
      setItem(STORAGE_KEYS.USER_INFO, userInfo.value)
    }
  }

  /** 修改密码 */
  async function changePassword(oldPassword: string, newPassword: string): Promise<void> {
    await userApi.changePassword({ oldPassword, newPassword })
  }

  return {
    // State
    token,
    tokenType,
    expiresIn,
    userInfo,
    isLoggedIn,
    // Getters
    userId,
    timezone,
    displayName,
    // Actions
    initAuth,
    login,
    register,
    logout,
    fetchProfile,
    updateProfile,
    changePassword,
  }
})
