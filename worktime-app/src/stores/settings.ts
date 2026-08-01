import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import type { UserSetting, WorkRule, TimeFormat, CalendarView, InputMode } from '@/types/settings'
import { DEFAULT_SETTINGS, DEFAULT_WORK_RULE } from '@/types/common'
import { settingApi } from '@/api/setting'
import { setItem, getItem, STORAGE_KEYS } from '@/utils/storage'

export const useSettingsStore = defineStore('settings', () => {
  // ========== State ==========
  const showWorktimeAfterClockout = ref<boolean>(DEFAULT_SETTINGS.showWorktimeAfterClockout as boolean)
  const timeFormat = ref<TimeFormat>(DEFAULT_SETTINGS.timeFormat as TimeFormat)
  const homeCalendarView = ref<CalendarView>(DEFAULT_SETTINGS.homeCalendarView as CalendarView)
  const defaultInputMode = ref<InputMode>(DEFAULT_SETTINGS.defaultInputMode as InputMode)
  const updateTime = ref<string | null>(null)
  const workRule = ref<WorkRule | null>(null)
  const isLoaded = ref(false)
  const isSyncing = ref(false)

  // ========== Getters ==========
  const is24Hour = computed(() => timeFormat.value === '24H')
  const currentTimeFormat = computed(() => timeFormat.value)
  const currentCalendarView = computed(() => homeCalendarView.value)

  /** 获取当前设置的纯对象形式 */
  const settingsObject = computed<UserSetting>(() => ({
    showWorktimeAfterClockout: showWorktimeAfterClockout.value,
    timeFormat: timeFormat.value,
    homeCalendarView: homeCalendarView.value,
    defaultInputMode: defaultInputMode.value,
    updateTime: updateTime.value ?? undefined,
  }))

  // ========== Actions ==========

  /** 初始化设置（本地缓存优先，后台合并服务端） */
  async function initSettings(): Promise<void> {
    // 1. 先加载本地缓存
    const cached = loadFromCache()
    if (cached) {
      applySettings(cached)
    }

    // 2. 如果已登录，从服务端获取并合并
    try {
      await fetchSettings()
    } catch {
      // 网络错误，使用本地缓存
      console.log('[SettingsStore] Using cached settings')
    }

    // 3. 获取工作规则
    try {
      await fetchWorkRule()
    } catch {
      console.log('[SettingsStore] Using default work rule')
    }

    isLoaded.value = true
  }

  /** 从服务器获取设置 */
  async function fetchSettings(): Promise<void> {
    const serverSettings = await settingApi.getSetting()
    const cached = loadFromCache()

    // 合并策略：以 update_time 较新者为准
    if (!cached || !cached.updateTime || !serverSettings.updateTime ||
        serverSettings.updateTime >= cached.updateTime) {
      applySettings(serverSettings)
      saveToCache()
    }
  }

  /** 更新设置 */
  async function updateSettings(partial: Partial<UserSetting>): Promise<void> {
    isSyncing.value = true
    try {
      // 1. 乐观更新本地
      if (partial.showWorktimeAfterClockout !== undefined) showWorktimeAfterClockout.value = partial.showWorktimeAfterClockout
      if (partial.timeFormat !== undefined) timeFormat.value = partial.timeFormat
      if (partial.homeCalendarView !== undefined) homeCalendarView.value = partial.homeCalendarView
      if (partial.defaultInputMode !== undefined) defaultInputMode.value = partial.defaultInputMode
      saveToCache()

      // 2. 同步到服务端
      const result = await settingApi.updateSetting(partial)
      updateTime.value = result.updateTime || null
      saveToCache()
    } catch (e) {
      console.error('[SettingsStore] updateSettings failed:', e)
      // 本地已保存，服务端下次同步时合并
    } finally {
      isSyncing.value = false
    }
  }

  /** 获取工作规则 */
  async function fetchWorkRule(): Promise<void> {
    const rule = await settingApi.getWorkRule()
    workRule.value = rule
    setItem(STORAGE_KEYS.WORK_RULE, rule)
  }

  /** 更新工作规则 */
  async function updateWorkRule(partial: Partial<WorkRule>): Promise<void> {
    const result = await settingApi.updateWorkRule(partial)
    workRule.value = result
    setItem(STORAGE_KEYS.WORK_RULE, result)
  }

  /** 设置首页日历视图 */
  async function setCalendarView(view: CalendarView): Promise<void> {
    homeCalendarView.value = view
    await updateSettings({ homeCalendarView: view })
  }

  /** 设置时间格式 */
  async function setTimeFormat(format: TimeFormat): Promise<void> {
    timeFormat.value = format
    await updateSettings({ timeFormat: format })
  }

  /** 应用设置到 state */
  function applySettings(s: UserSetting): void {
    if (s.showWorktimeAfterClockout !== undefined) showWorktimeAfterClockout.value = s.showWorktimeAfterClockout
    if (s.timeFormat) timeFormat.value = s.timeFormat
    if (s.homeCalendarView) homeCalendarView.value = s.homeCalendarView
    if (s.defaultInputMode) defaultInputMode.value = s.defaultInputMode
    if (s.updateTime) updateTime.value = s.updateTime
  }

  /** 从本地缓存读取 */
  function loadFromCache(): UserSetting | null {
    return getItem<UserSetting>(STORAGE_KEYS.SETTINGS)
  }

  /** 保存到本地缓存 */
  function saveToCache(): void {
    setItem(STORAGE_KEYS.SETTINGS, settingsObject.value)
  }

  return {
    // State
    showWorktimeAfterClockout,
    timeFormat,
    homeCalendarView,
    defaultInputMode,
    updateTime,
    workRule,
    isLoaded,
    isSyncing,
    // Getters
    is24Hour,
    currentTimeFormat,
    currentCalendarView,
    settingsObject,
    // Actions
    initSettings,
    fetchSettings,
    updateSettings,
    fetchWorkRule,
    updateWorkRule,
    setCalendarView,
    setTimeFormat,
    loadFromCache,
  }
})
