<template>
  <view class="day-summary">
    <view class="stat-cards">
      <view class="stat-card">
        <text class="stat-value">{{ formatDuration(data.workMinutes) }}</text>
        <text class="stat-label">工作时长</text>
      </view>
      <view class="stat-card">
        <text class="stat-value overtime">{{ formatDuration(data.overtimeMinutes) }}</text>
        <text class="stat-label">加班时长</text>
      </view>
    </view>
    <view v-for="rec in data.records" :key="rec.id" class="record-item">
      <text class="rec-time">{{ formatTime(rec.clockIn) }} → {{ rec.clockOut ? formatTime(rec.clockOut) : '--' }}</text>
      <text class="rec-dur">{{ formatDuration(rec.workMinutes) }}</text>
      <text v-if="rec.source === 'MANUAL'" class="rec-source">手动</text>
    </view>
  </view>
</template>

<script setup lang="ts">
import type { DaySummary } from '@/types/summary'
import { formatDisplayTime, formatDisplayDuration } from '@/utils/time'

defineProps<{ data: DaySummary }>()

function formatTime(utc: string) { return formatDisplayTime(utc, '24H') }
function formatDuration(min: number) { return formatDisplayDuration(min) }
</script>

<style lang="scss" scoped>
.stat-cards { display: flex; gap: 12px; margin-bottom: 16px; }
.stat-card {
  flex: 1; text-align: center; background: $bg-primary; border-radius: $border-radius-md; padding: 16px 12px; box-shadow: $shadow-sm;
}
.stat-value { font-size: $font-size-xl; font-weight: 700; color: $primary-color; display: block; }
.stat-value.overtime { color: $warning-color; }
.stat-label { font-size: $font-size-xs; color: $text-tertiary; margin-top: 4px; display: block; }

.record-item {
  display: flex; align-items: center; padding: 12px; background: $bg-primary;
  border-radius: $border-radius-sm; margin-bottom: 8px;
}
.rec-time { flex: 1; font-size: $font-size-sm; color: $text-primary; }
.rec-dur { font-size: $font-size-sm; color: $text-secondary; font-weight: 500; }
.rec-source { font-size: $font-size-xs; color: $text-tertiary; margin-left: 8px; background: $bg-tertiary; padding: 1px 6px; border-radius: $border-radius-round; }
</style>
