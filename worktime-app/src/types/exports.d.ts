export type ExportType = 'DAY' | 'WEEK' | 'MONTH' | 'YEAR' | 'CUSTOM'
export type FileFormat = 'EXCEL' | 'CSV' | 'PDF'
export type ExportStatus = 'PROCESSING' | 'SUCCESS' | 'FAILED'

export interface ExportRecord {
  exportId: number
  exportType: ExportType
  startDate: string | null
  endDate: string | null
  fileFormat: FileFormat
  status: ExportStatus
  fileUrl: string | null
  fileSize: number | null
  errorMsg: string | null
  createTime: string
}

export interface CreateExportRequest {
  exportType: ExportType
  startDate?: string
  endDate?: string
  fileFormat: FileFormat
}

export interface CreateExportResponse {
  exportId: number
  status: ExportStatus
  estimatedSeconds: number
}
