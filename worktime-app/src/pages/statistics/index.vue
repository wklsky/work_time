<template>
  <view class="statistics-page">
    <view class="stats-header">
      <picker mode="date" fields="month" :value="`${year}-${String(month).padStart(2,'0')}-01`" @change="onMonthChange">
        <text class="stats-title">{{ year }}年{{ month }}月</text>
        <text class="stats-arrow">▼</text>
      </picker>
    </view>

    <!-- 月统计卡片 -->
    <view class="stat-cards" v-if="monthData">
      <view class="stat-card">
        <text class="card-num">{{ monthData.attendanceDays }}</text>
        <text class="card-label">出勤天数</text>
      </view>
      <view class="stat-card">
        <text class="card-num primary">{{ formatDuration(monthData.workMinutes) }}</text>
        <text class="card-label">月工时</text>
      </view>
      <view class="stat-card">
        <text class="card-num warning">{{ formatDuration(monthData.overtimeMinutes) }}</text>
        <text class="card-label">月加班</text>
      </view>
      <view class="stat-card">
        <text class="card-num">{{ monthData.avgDailyWorkMinutes }}分</text>
        <text class="card-label">日均工时</text>
      </view>
    </view>

    <!-- 年度趋势 -->
    <view class="section">
      <text class="section-title">{{ year }}年趋势</text>
      <view v-if="yearData" class="trend-chart">
        <view v-for="m in yearData.monthlyBreakdown" :key="m.month" class="trend-col">
          <view class="trend-bar-wrap">
            <view class="trend-bar" :style="{ height: (m.workMinutes / yearMaxWork * 100) + '%' }" />
            <view v-if="m.overtimeMinutes > 0" class="trend-ot" :style="{ height: (m.overtimeMinutes / yearMaxOt * 100) + '%' }" />
          </view>
          <text class="trend-label">{{ m.month }}月</text>
        </view>
      </view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { summaryApi } from '@/api/summary'
import { formatDisplayDuration } from '@/utils/time'
import type { MonthSummary, YearSummary } from '@/types/summary'

const year = ref(new Date().getFullYear())
const month = ref(new Date().getMonth() + 1)
const monthData = ref<MonthSummary | null>(null)
const yearData = ref<YearSummary | null>(null)

const yearMaxWork = computed(() => Math.max(...(yearData.value?.monthlyBreakdown?.map(m => m.workMinutes) || [1])))
const yearMaxOt = computed(() => Math.max(...(yearData.value?.monthlyBreakdown?.map(m => m.overtimeMinutes) || [1]), 1))

function formatDuration(min: number) { return formatDisplayDuration(min) }

function onMonthChange(e: any) {
  const [y, m] = e.detail.value.split('-').map(Number)
  year.value = y; month.value = m
  fetchMonth()
}

async function fetchMonth() {
  try { monthData.value = await summaryApi.getMonth(year.value, month.value) } catch {}
}
async function fetchYear() {
  try { yearData.value = await summaryApi.getYear(year.value) } catch {}
}

onMounted(() => {
  fetchMonth()
  fetchYear()
})
</script>

<style lang="scss" scoped>
.statistics-page { min-height: 100vh; background: $bg-secondary; padding-bottom: 40px; }

.stats-header { padding: 16px $page-padding; text-align: center; }
.stats-title { font-size: $font-size-lg; font-weight: 700; color: $text-primary; }
.stats-arrow { font-size: $font-size-xs; color: $text-tertiary; margin-left: 6px; }

.stat-cards { display: flex; flex-wrap: wrap; gap: 8px; padding: 0 $page-padding; margin-bottom: 20px; }
.stat-card {
  flex: 1 1 calc(50% - 8px); min-width: 140px;
  text-align: center; background: $bg-primary; border-radius: $border-radius-md; padding: 20px 12px; box-shadow: $shadow-sm;
}
.card-num { font-size: $font-size-xl; font-weight: 700; color: $text-primary; display: block; }
.card-num.primary { color: $primary-color; }
.card-num.warning { color: $warning-color; }
.card-label { font-size: $font-size-xs; color: $text-tertiary; margin-top: 4px; display: block; }

.section { margin: 0 $page-padding; }
.section-title { font-size: $font-size-md; font-weight: 600; display: block; margin-bottom: 12px; }

.trend-chart {
  display: flex; justify-content: space-around; align-items: flex-end;
  height: 160px; background: $bg-primary; border-radius: $border-radius-md; padding: 16px 8px;
}
.trend-col { display: flex; flex-direction: column; align-items: center; height: 100%; }
.trend-bar-wrap { flex: 1; display: flex; flex-direction: column; justify-content: flex-end; align-items: center; width: 16px; gap: 2px; }
.trend-bar { width: 14px; background: $primary-color; border-radius: 2px 2px 0 0; min-height: 2px; }
.trend-ot { width: 14px; background: $warning-color; border-radius: 2px 2px 0 0; min-height: 2px; }
.trend-label { font-size: $font-size-xs; color: $text-tertiary; margin-top: 4px; }
</style>
