<template>
  <view class="accumulated card">
    <text class="acc-label">今日累计</text>
    <view class="acc-row">
      <text class="acc-value">{{ formatDuration(summary.totalWorkMinutes) }}</text>
      <text class="acc-segment" v-if="summary.segmentCount > 1">{{ summary.segmentCount }}段</text>
    </view>
    <view v-if="summary.totalOvertimeMinutes > 0" class="acc-overtime">
      <text>含加班 {{ formatDuration(summary.totalOvertimeMinutes) }}</text>
    </view>
  </view>
</template>

<script setup lang="ts">
import type { TodayAccumulated } from '@/types/attendance'
import { formatDisplayDuration } from '@/utils/time'

defineProps<{
  summary: TodayAccumulated
}>()

function formatDuration(min: number): string {
  return formatDisplayDuration(min)
}
</script>

<style lang="scss" scoped>
.accumulated {
  margin: 0;
  margin-top: 12px;
}

.acc-label {
  font-size: $font-size-xs;
  color: $text-tertiary;
}

.acc-row {
  display: flex;
  align-items: baseline;
  margin-top: 4px;
}

.acc-value {
  font-size: $font-size-xl;
  font-weight: 700;
  color: $text-primary;
}

.acc-segment {
  font-size: $font-size-xs;
  color: $text-tertiary;
  margin-left: 8px;
}

.acc-overtime {
  margin-top: 4px;
  font-size: $font-size-sm;
  color: $warning-color;
}
</style>
