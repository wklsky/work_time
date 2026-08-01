<template>
  <view class="worktime-card card">
    <view class="wt-header">
      <text class="wt-label">本次工作</text>
      <text class="wt-close" @tap="dismiss">✕</text>
    </view>

    <view class="wt-main">
      <view class="wt-item">
        <text class="wt-title">工作时长</text>
        <text class="wt-value primary">{{ formatDuration(result.workMinutes) }}</text>
      </view>
      <view class="wt-divider" />
      <view class="wt-item">
        <text class="wt-title">加班时长</text>
        <text class="wt-value overtime">{{ formatDuration(result.overtimeMinutes) }}</text>
      </view>
    </view>

    <view class="wt-times">
      <text class="time-range">
        {{ formatTime(result.clockIn) }} → {{ formatTime(result.clockOut) }}
      </text>
    </view>

    <view v-if="result.breakdown" class="wt-breakdown">
      <text class="breakdown-text">{{ result.breakdown.formula }}</text>
    </view>

    <view v-if="result.crossDay" class="wt-crossday">
      ⚠️ 跨天记录
    </view>
  </view>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import type { ClockOutResult } from '@/types/attendance'
import { formatDisplayTime, formatDisplayDuration } from '@/utils/time'

const props = defineProps<{
  result: ClockOutResult
}>()

const dismissed = ref(false)

function dismiss() {
  dismissed.value = true
}

function formatTime(utc: string): string {
  return formatDisplayTime(utc, '24H')
}

function formatDuration(min: number): string {
  return formatDisplayDuration(min)
}
</script>

<style lang="scss" scoped>
.worktime-card {
  animation: slideUp 0.4s ease;
}

.wt-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
}

.wt-label {
  font-size: $font-size-sm;
  color: $text-secondary;
}

.wt-close {
  font-size: 16px;
  color: $text-placeholder;
  padding: 4px;
}

.wt-main {
  display: flex;
  align-items: center;
}

.wt-item {
  flex: 1;
  text-align: center;
}

.wt-title {
  font-size: $font-size-xs;
  color: $text-tertiary;
  display: block;
  margin-bottom: 4px;
}

.wt-value {
  font-size: $font-size-xxl;
  font-weight: 700;
}

.wt-value.primary { color: $primary-color; }
.wt-value.overtime { color: $warning-color; }

.wt-divider {
  width: 1px;
  height: 40px;
  background: $divider-color;
}

.wt-times {
  text-align: center;
  margin-top: 10px;
}

.time-range {
  font-size: $font-size-sm;
  color: $text-secondary;
}

.wt-breakdown {
  margin-top: 8px;
  padding: 8px;
  background: $bg-secondary;
  border-radius: $border-radius-sm;
}

.breakdown-text {
  font-size: $font-size-xs;
  color: $text-tertiary;
  line-height: 1.4;
}

.wt-crossday {
  margin-top: 8px;
  font-size: $font-size-sm;
  color: $warning-color;
  text-align: center;
}
</style>
