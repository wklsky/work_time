/** 统一 API 响应结构 */
export interface ApiResponse<T = unknown> {
  code: number
  message: string
  data: T
  timestamp: number
}

/** 分页响应 */
export interface PaginatedData<T> {
  records: T[]
  total: number
  page: number
  pageSize: number
  totalPages: number
}

export type PaginatedResponse<T> = ApiResponse<PaginatedData<T>>

/** 错误响应 */
export interface ApiError {
  code: number
  message: string
  errors?: ValidationError[]
  timestamp: number
}

export interface ValidationError {
  field: string
  message: string
}
