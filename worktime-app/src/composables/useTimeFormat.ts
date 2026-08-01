import { computed } from 'vue'
import { useSettingsStore } from '@/stores/settings'
import { formatDisplayTime as formatTime, formatDisplayDuration, to12HourDisplay } from '@/utils/time'

/**
 * 全局时间格式化 composable
 * 自动读取 settingsStore 的时间进制设置
 */
export function useTimeFormat() {
  const settingsStore = useSettingsStore()

  const is24Hour = computed(() => settingsStore.is24Hour)
  const format = computed(() => settingsStore.currentTimeFormat)

  /**
   * 根据当前设置格式化 UTC 时间为显示时间
   */
  function formatTimeDisplay(utcIsoString: string | null, showSeconds = false): string {
    return formatTime(utcIsoString, format.value, 'Asia/Shanghai', showSeconds)
  }

  /**
   * 格式化分钟数为可读时长
   */
  function formatDuration(minutes: number): string {
    return formatDisplayDuration(minutes)
  }

  /**
   * 将 24 小时制时间显示为当前格式
   */
  function displayTime24(time24: string): string {
    if (is24Hour.value) return time24
    return to12HourDisplay(time24)
  }

  return {
    is24Hour,
    format,
    formatTimeDisplay,
    formatDuration,
    displayTime24,
  }
}
