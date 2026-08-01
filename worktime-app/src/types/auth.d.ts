export interface UserInfo {
  userId: number
  username: string
  nickname: string | null
  avatar: string | null
  phone: string
  timezone: string
  createTime?: string
}

export interface LoginRequest {
  phone: string
  password: string
}

export interface LoginResponse {
  token: string
  tokenType: string
  expiresIn: number
  userInfo: UserInfo
}

export interface RegisterRequest {
  username: string
  password: string
  phone: string
}

export interface UpdateProfileRequest {
  nickname?: string
  avatar?: string
  timezone?: string
}

export interface ChangePasswordRequest {
  oldPassword: string
  newPassword: string
}
