<template>
  <view class="year-summary">
    <view class="stat-cards">
      <SummaryCard label="年工时" :value="data.workMinutes" unit="min" />
      <SummaryCard label="年加班" :value="data.overtimeMinutes" unit="min" color="warning" />
      <SummaryCard label="出勤" :value="data.attendanceDays" unit="天" />
      <SummaryCard label="日均" :value="data.avgDailyWorkMinutes" unit="min/天" />
    </view>
    <view class="highlight" v-if="data.maxOvertimeMonth">
      <text>加班最多：{{ data.maxOvertimeMonth.month }}月 ({{ formatDuration(data.maxOvertimeMonth.overtimeMinutes) }})</text>
    </view>
    <!-- 月度条 -->
    <view class="month-bars">
      <view v-for="m in data.monthlyBreakdown" :key="m.month" class="month-bar-row">
        <text class="month-name">{{ m.month }}月</text>
        <view class="month-bar-bg">
          <view class="month-bar-fill" :style="{ width: (m.workMinutes / maxWork) * 100 + '%' }" />
        </view>
        <text class="month-val">{{ formatDuration(m.workMinutes) }}</text>
      </view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import type { YearSummary } from '@/types/summary'
import SummaryCard from './SummaryCard.vue'
import { formatDisplayDuration } from '@/utils/time'

const props = defineProps<{ data: YearSummary }>()

const maxWork = computed(() => Math.max(...props.data.monthlyBreakdown.map(m => m.workMinutes), 1))

function formatDuration(min: number) { return formatDisplayDuration(min) }
</script>

<style lang="scss" scoped>
.stat-cards { display: flex; gap: 8px; margin-bottom: 16px; flex-wrap: wrap; }
.highlight {
  background: $primary-light; border-radius: $border-radius-sm; padding: 10px 14px;
  font-size: $font-size-sm; color: $primary-color; text-align: center; margin-bottom: 12px;
}
.month-bars { background: $bg-primary; border-radius: $border-radius-md; padding: 12px; }
.month-bar-row { display: flex; align-items: center; padding: 6px 0; }
.month-name { width: 36px; font-size: $font-size-sm; color: $text-secondary; }
.month-bar-bg { flex: 1; height: 8px; background: $bg-secondary; border-radius: 4px; margin: 0 10px; overflow: hidden; }
.month-bar-fill { height: 100%; background: $primary-color; border-radius: 4px; }
.month-val { font-size: $font-size-xs; color: $text-tertiary; width: 64px; text-align: right; }
</style>
