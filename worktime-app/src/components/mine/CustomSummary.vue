<template>
  <view class="custom-summary">
    <view class="stat-cards">
      <SummaryCard label="区间工时" :value="data.workMinutes" unit="min" />
      <SummaryCard label="区间加班" :value="data.overtimeMinutes" unit="min" color="warning" />
      <SummaryCard label="出勤" :value="data.attendanceDays" unit="天" />
      <SummaryCard label="日均" :value="data.avgDailyWorkMinutes" unit="min/天" />
    </view>
    <button class="btn-export" @tap="$emit('export')">一键导出报表</button>
  </view>
</template>

<script setup lang="ts">
import type { CustomSummary } from '@/types/summary'
import SummaryCard from './SummaryCard.vue'

defineProps<{ data: CustomSummary }>()
defineEmits<{ export: [] }>()
</script>

<style lang="scss" scoped>
.stat-cards { display: flex; gap: 8px; margin-bottom: 16px; flex-wrap: wrap; }
.btn-export {
  width: 100%; height: 44px; background: $primary-color; color: #FFF;
  font-size: $font-size-md; font-weight: 600; border-radius: $border-radius-sm; border: none;
}
</style>
