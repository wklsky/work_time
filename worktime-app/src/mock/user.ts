import { registerHandler, mockSuccess, mockError } from './index'
import { mockUser } from './mock-data'
import type { LoginRequest, RegisterRequest } from '@/types/auth'

registerHandler('/user/register', (config) => {
  const data = config.data as unknown as RegisterRequest
  if (!data?.phone || !data?.password || !data?.username) {
    return mockError(400, '参数不完整')
  }
  return mockSuccess({ userId: 1, username: data.username })
})

registerHandler('/user/login', (config) => {
  const data = config.data as unknown as LoginRequest
  if (data?.phone === '13800138000' && data?.password === 'Abc@123456') {
    return mockSuccess({
      token: 'mock_jwt_token_eyJhbGciOiJIUzI1NiIs...',
      tokenType: 'Bearer',
      expiresIn: 604800,
      userInfo: mockUser,
    })
  }
  return mockError(401, '手机号或密码错误')
})

registerHandler('/user/profile', () => {
  return mockSuccess(mockUser)
})

registerHandler('/user/password', () => {
  return mockSuccess(null)
})

registerHandler('/user/logout', () => {
  return mockSuccess(null)
})
