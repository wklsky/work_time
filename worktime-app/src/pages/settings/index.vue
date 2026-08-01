<template>
  <view class="settings-page">
    <view class="section">
      <text class="section-title">显示设置</text>
      <view class="menu-item" @tap="goDisplay">
        <text class="menu-icon">🎨</text>
        <text class="menu-label">显示设置</text>
        <text class="menu-value">{{ timeFormatLabel }} · {{ calendarViewLabel }}</text>
        <text class="menu-arrow">›</text>
      </view>
    </view>

    <view class="section">
      <text class="section-title">打卡设置</text>
      <view class="menu-item" @tap="goClock">
        <text class="menu-icon">⏱️</text>
        <text class="menu-label">默认录入模式</text>
        <text class="menu-value">{{ inputModeLabel }}</text>
        <text class="menu-arrow">›</text>
      </view>
    </view>

    <view class="section">
      <text class="section-title">工作规则</text>
      <view class="menu-item" @tap="goWorkRule">
        <text class="menu-icon">📋</text>
        <text class="menu-label">工作规则设置</text>
        <text class="menu-value">{{ workTimeLabel }}</text>
        <text class="menu-arrow">›</text>
      </view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useSettingsStore } from '@/stores/settings'

const settingsStore = useSettingsStore()

const timeFormatLabel = computed(() => settingsStore.is24Hour ? '24小时制' : '12小时制')
const calendarViewLabel = computed(() => {
  const map: Record<string, string> = { MONTH: '月视图', WEEK: '周视图', TODAY: '今日视图' }
  return map[settingsStore.homeCalendarView] || '今日视图'
})
const inputModeLabel = computed(() => settingsStore.defaultInputMode === 'BUTTON' ? '按钮模式' : '手动模式')
const workTimeLabel = computed(() => {
  const r = settingsStore.workRule
  return r ? `${r.startTime}-${r.endTime}` : '09:00-18:00'
})

function goDisplay() { uni.navigateTo({ url: '/pages/settings/display' }) }
function goClock() { uni.navigateTo({ url: '/pages/settings/clock' }) }
function goWorkRule() { uni.navigateTo({ url: '/pages/settings/work-rule' }) }
</script>

<style lang="scss" scoped>
.settings-page { min-height: 100vh; background: $bg-secondary; }

.section { margin-top: 16px; }
.section-title { font-size: $font-size-xs; color: $text-tertiary; padding: 8px $page-padding; display: block; text-transform: uppercase; }

.menu-item {
  display: flex; align-items: center; padding: 14px $page-padding; background: $bg-primary;
  border-bottom: 1px solid $divider-color; gap: 10px;
}
.menu-icon { font-size: 20px; }
.menu-label { flex: 0 0 auto; font-size: $font-size-md; color: $text-primary; }
.menu-value { flex: 1; text-align: right; font-size: $font-size-sm; color: $text-tertiary; }
.menu-arrow { color: $text-placeholder; font-size: 18px; }
</style>
