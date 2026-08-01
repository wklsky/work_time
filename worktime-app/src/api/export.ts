import { http } from './index'
import type { CreateExportRequest, CreateExportResponse, ExportRecord } from '@/types/exports'

const BASE = '/export'

export const exportApi = {
  /** 创建导出任务 */
  create(data: CreateExportRequest): Promise<CreateExportResponse> {
    return http.post(BASE, data as unknown as Record<string, unknown>)
  },

  /** 查询导出状态 */
  getStatus(exportId: number): Promise<ExportRecord> {
    return http.get(`${BASE}/${exportId}`)
  },

  /** 导出历史 */
  getHistory(page = 1, pageSize = 10): Promise<{ records: ExportRecord[]; total: number; page: number; pageSize: number; totalPages: number }> {
    return http.get(`${BASE}/history`, { page: String(page), pageSize: String(pageSize) })
  },
}
