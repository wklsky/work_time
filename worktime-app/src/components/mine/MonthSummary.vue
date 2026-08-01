<template>
  <view class="month-summary">
    <view class="stat-cards">
      <SummaryCard label="月工时" :value="data.workMinutes" unit="min" />
      <SummaryCard label="月加班" :value="data.overtimeMinutes" unit="min" color="warning" />
      <SummaryCard label="出勤" :value="data.attendanceDays" unit="天" />
      <SummaryCard label="日均" :value="data.avgDailyWorkMinutes" unit="min/天" />
    </view>
    <view class="highlight" v-if="data.maxOvertimeDay">
      <text>加班最多：{{ data.maxOvertimeDay.date }} ({{ formatDuration(data.maxOvertimeDay.overtimeMinutes) }})</text>
    </view>
  </view>
</template>

<script setup lang="ts">
import type { MonthSummary } from '@/types/summary'
import SummaryCard from './SummaryCard.vue'
import { formatDisplayDuration } from '@/utils/time'

defineProps<{ data: MonthSummary }>()

function formatDuration(min: number) { return formatDisplayDuration(min) }
</script>

<style lang="scss" scoped>
.stat-cards { display: flex; gap: 8px; margin-bottom: 16px; flex-wrap: wrap; }
.highlight {
  background: $primary-light; border-radius: $border-radius-sm; padding: 10px 14px;
  font-size: $font-size-sm; color: $primary-color; text-align: center;
}
</style>
