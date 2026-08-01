import { ref } from 'vue'
import { exportApi } from '@/api/export'
import { EXPORT_POLL_INTERVAL, EXPORT_POLL_MAX } from '@/types/common'

/**
 * 导出任务轮询 composable
 */
export function useExportPolling() {
  const status = ref<string | null>(null)
  const polling = ref(false)
  const pollsCount = ref(0)

  let pollInterval: ReturnType<typeof setInterval> | null = null

  function startPolling(exportId: number) {
    status.value = 'PROCESSING'
    polling.value = true
    pollsCount.value = 0

    pollInterval = setInterval(async () => {
      try {
        const result = await exportApi.getStatus(exportId)
        status.value = result.status
        pollsCount.value++

        if (result.status === 'SUCCESS' || result.status === 'FAILED') {
          stopPolling()
        }
        if (pollsCount.value >= EXPORT_POLL_MAX) {
          stopPolling()
          status.value = 'TIMEOUT'
        }
      } catch {
        stopPolling()
      }
    }, EXPORT_POLL_INTERVAL)
  }

  function stopPolling() {
    if (pollInterval) {
      clearInterval(pollInterval)
      pollInterval = null
    }
    polling.value = false
  }

  return {
    status,
    polling,
    pollsCount,
    startPolling,
    stopPolling,
  }
}
