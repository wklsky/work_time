<template>
  <view class="week-summary">
    <view class="stat-cards">
      <SummaryCard label="周工时" :value="data.workMinutes" unit="min" />
      <SummaryCard label="周加班" :value="data.overtimeMinutes" unit="min" color="warning" />
      <SummaryCard label="出勤" :value="data.attendanceDays" unit="天" />
    </view>
    <!-- 简易柱状图 -->
    <view class="bar-chart">
      <view v-for="day in data.dailyBreakdown" :key="day.date" class="bar-col">
        <view class="bar-wrapper">
          <view class="bar-fill" :style="{ height: barHeight(day.workMinutes) + '%' }" />
          <view v-if="day.overtimeMinutes > 0" class="bar-ot" :style="{ height: barHeight(day.overtimeMinutes) + '%' }" />
        </view>
        <text class="bar-label">{{ day.weekdayName }}</text>
      </view>
    </view>
  </view>
</template>

<script setup lang="ts">
import type { WeekSummary } from '@/types/summary'
import SummaryCard from './SummaryCard.vue'

defineProps<{ data: WeekSummary }>()

function barHeight(min: number): number {
  return Math.min(100, (min / 600) * 100)
}
</script>

<style lang="scss" scoped>
.stat-cards { display: flex; gap: 8px; margin-bottom: 16px; flex-wrap: wrap; }

.bar-chart {
  display: flex; justify-content: space-around; align-items: flex-end;
  height: 160px; background: $bg-primary; border-radius: $border-radius-md; padding: 16px 8px;
}

.bar-col { display: flex; flex-direction: column; align-items: center; height: 100%; }
.bar-wrapper { flex: 1; display: flex; flex-direction: column; justify-content: flex-end; align-items: center; width: 24px; gap: 2px; }
.bar-fill { width: 20px; background: $primary-color; border-radius: 3px 3px 0 0; min-height: 2px; }
.bar-ot { width: 20px; background: $warning-color; border-radius: 3px 3px 0 0; min-height: 2px; }
.bar-label { font-size: $font-size-xs; color: $text-tertiary; margin-top: 6px; }
</style>
