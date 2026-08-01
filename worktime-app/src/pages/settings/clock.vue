<template>
  <view class="clock-page">
    <view class="setting-item">
      <view class="setting-info">
        <text class="setting-label">默认录入模式</text>
        <text class="setting-desc">打开 App 时首页打卡区的默认模式</text>
      </view>
    </view>
    <view class="option-list">
      <view
        class="option-item"
        :class="{ selected: settingsStore.defaultInputMode === 'BUTTON' }"
        @tap="setMode('BUTTON')"
      >
        <text class="option-title">按钮模式</text>
        <text class="option-desc">点击开始/结束工作按钮打卡，使用服务端时间</text>
        <text v-if="settingsStore.defaultInputMode === 'BUTTON'" class="option-check">✓</text>
      </view>
      <view
        class="option-item"
        :class="{ selected: settingsStore.defaultInputMode === 'MANUAL' }"
        @tap="setMode('MANUAL')"
      >
        <text class="option-title">手动模式</text>
        <text class="option-desc">自行输入开始与结束时间，支持补填和修改</text>
        <text v-if="settingsStore.defaultInputMode === 'MANUAL'" class="option-check">✓</text>
      </view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { useSettingsStore } from '@/stores/settings'
import type { InputMode } from '@/types/settings'

const settingsStore = useSettingsStore()

function setMode(mode: InputMode) {
  settingsStore.updateSettings({ defaultInputMode: mode })
}
</script>

<style lang="scss" scoped>
.clock-page { min-height: 100vh; background: $bg-secondary; padding-top: 8px; }
.setting-item { background: $bg-primary; padding: 16px $page-padding; }
.setting-label { font-size: $font-size-md; color: $text-primary; display: block; }
.setting-desc { font-size: $font-size-xs; color: $text-tertiary; margin-top: 4px; display: block; }

.option-list { margin-top: 12px; }
.option-item {
  display: flex; align-items: center; flex-wrap: wrap;
  padding: 16px $page-padding; background: $bg-primary; border-bottom: 1px solid $divider-color;
}
.option-item.selected { background: $primary-light; }
.option-title { font-size: $font-size-md; font-weight: 600; width: 100%; display: block; }
.option-desc { font-size: $font-size-xs; color: $text-tertiary; margin-top: 4px; flex: 1; }
.option-check { font-size: 20px; color: $primary-color; font-weight: 700; }
</style>
