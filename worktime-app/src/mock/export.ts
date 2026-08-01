import { registerHandler, mockSuccess } from './index'
import { mockExportRecords } from './mock-data'

let exports = [...mockExportRecords]
let nextExportId = 10

registerHandler('/export', (config) => {
  // POST - create
  const data = config.data as any
  const newExport = {
    exportId: nextExportId++,
    exportType: data?.exportType || 'CUSTOM',
    startDate: data?.startDate || null,
    endDate: data?.endDate || null,
    fileFormat: data?.fileFormat || 'EXCEL',
    status: 'PROCESSING' as const,
    fileUrl: null,
    fileSize: null,
    errorMsg: null,
    createTime: new Date().toISOString(),
  }
  exports.unshift(newExport)

  // 模拟 5 秒后完成
  setTimeout(() => {
    const idx = exports.findIndex(e => e.exportId === newExport.exportId)
    if (idx >= 0) {
      exports[idx].status = 'SUCCESS'
      exports[idx].fileUrl = `https://example.com/exports/${newExport.exportId}.xlsx`
      exports[idx].fileSize = Math.floor(Math.random() * 200000) + 10000
    }
  }, 5000)

  return mockSuccess({ exportId: newExport.exportId, status: 'PROCESSING', estimatedSeconds: 30 })
})

registerHandler('/export/history', () => {
  return mockSuccess({
    records: exports,
    total: exports.length,
    page: 1,
    pageSize: 10,
    totalPages: 1,
  })
})

// 动态路由: /export/:id
registerHandler('/export/:id', (config) => {
  const exportIdMatch = config.url.match(/\/export\/(\d+)/)
  const exportId = exportIdMatch ? parseInt(exportIdMatch[1]) : 0
  const record = exports.find(e => e.exportId === exportId)
  if (!record) return mockError(404, '导出记录不存在')
  return mockSuccess(record)
})
