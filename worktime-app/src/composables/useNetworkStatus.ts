import { ref, onMounted, onUnmounted } from 'vue'

/**
 * 网络状态检测 composable
 */
export function useNetworkStatus() {
  const isOnline = ref(true)
  const networkType = ref<string>('unknown')

  function updateStatus() {
    uni.getNetworkType({
      success: (res) => {
        isOnline.value = res.networkType !== 'none'
        networkType.value = res.networkType || 'unknown'
      },
    })
  }

  function onNetworkChange(res: any) {
    isOnline.value = res.isConnected
    networkType.value = res.networkType
  }

  onMounted(() => {
    updateStatus()
    uni.onNetworkStatusChange(onNetworkChange)
  })

  onUnmounted(() => {
    uni.offNetworkStatusChange(onNetworkChange)
  })

  return {
    isOnline,
    networkType,
    updateStatus,
  }
}
