import { ref, onUnmounted } from 'vue'

/**
 * 实时计时器 composable
 * 用于打卡"工作中"状态的实时耗时显示
 */
export function useClockTimer() {
  const elapsedSeconds = ref(0)
  let timerInterval: ReturnType<typeof setInterval> | null = null

  function start(initialSeconds = 0) {
    elapsedSeconds.value = initialSeconds
    if (timerInterval) return
    timerInterval = setInterval(() => {
      elapsedSeconds.value++
    }, 1000)
  }

  function stop() {
    if (timerInterval) {
      clearInterval(timerInterval)
      timerInterval = null
    }
  }

  function reset() {
    stop()
    elapsedSeconds.value = 0
  }

  // 自动清理
  onUnmounted(() => stop())

  return {
    elapsedSeconds,
    start,
    stop,
    reset,
  }
}
