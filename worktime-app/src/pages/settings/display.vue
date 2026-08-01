<template>
  <view class="display-page">
    <!-- 工时显示开关 -->
    <view class="setting-item">
      <view class="setting-info">
        <text class="setting-label">结束工作后显示工时</text>
        <text class="setting-desc">关闭后首页不展示结果卡片，数据仍正常保存</text>
      </view>
      <switch :checked="settingsStore.showWorktimeAfterClockout" @change="onToggleWorktime" color="#007AFF" />
    </view>

    <!-- 时间格式 -->
    <view class="setting-item">
      <view class="setting-info">
        <text class="setting-label">时间格式</text>
        <text class="setting-desc">全局时间显示格式</text>
      </view>
      <view class="radio-group">
        <view
          class="radio-item"
          :class="{ active: settingsStore.timeFormat === '24H' }"
          @tap="setTimeFormat('24H')"
        >24小时制</view>
        <view
          class="radio-item"
          :class="{ active: settingsStore.timeFormat === '12H' }"
          @tap="setTimeFormat('12H')"
        >12小时制</view>
      </view>
    </view>

    <!-- 日历默认视图 -->
    <view class="setting-item">
      <view class="setting-info">
        <text class="setting-label">首页日历展示方式</text>
        <text class="setting-desc">打开 App 时默认显示的日历视图</text>
      </view>
      <view class="radio-group col">
        <view
          v-for="opt in viewOptions"
          :key="opt.value"
          class="radio-item"
          :class="{ active: settingsStore.homeCalendarView === opt.value }"
          @tap="setCalendarView(opt.value)"
        >{{ opt.label }}</view>
      </view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { useSettingsStore } from '@/stores/settings'
import type { CalendarView, TimeFormat } from '@/types/settings'

const settingsStore = useSettingsStore()

const viewOptions = [
  { label: '今日', value: 'TODAY' as CalendarView },
  { label: '星期', value: 'WEEK' as CalendarView },
  { label: '月', value: 'MONTH' as CalendarView },
]

function onToggleWorktime(e: any) {
  settingsStore.updateSettings({ showWorktimeAfterClockout: !!e.detail.value })
}

function setTimeFormat(format: TimeFormat) {
  settingsStore.setTimeFormat(format)
}

function setCalendarView(view: CalendarView) {
  settingsStore.setCalendarView(view)
}
</script>

<style lang="scss" scoped>
.display-page { min-height: 100vh; background: $bg-secondary; padding-top: 8px; }

.setting-item {
  background: $bg-primary; padding: 16px $page-padding; border-bottom: 1px solid $divider-color;
}
.setting-label { font-size: $font-size-md; color: $text-primary; display: block; }
.setting-desc { font-size: $font-size-xs; color: $text-tertiary; margin-top: 4px; display: block; }

.radio-group { display: flex; margin-top: 12px; gap: 8px; }
.radio-group.col { flex-direction: column; }
.radio-item {
  flex: 1; text-align: center; padding: 10px; font-size: $font-size-sm;
  background: $bg-secondary; border-radius: $border-radius-sm; color: $text-secondary;
}
.radio-item.active { background: $primary-color; color: #FFF; font-weight: 600; }
</style>
