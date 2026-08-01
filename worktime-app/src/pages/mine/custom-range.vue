<template>
  <view class="custom-range-page">
    <!-- 快捷区间 -->
    <view class="section">
      <text class="section-title">快捷区间</text>
      <view class="quick-presets">
        <view
          v-for="p in presets"
          :key="p.value"
          class="preset-item"
          @tap="applyPreset(p.value)"
        >{{ p.label }}</view>
      </view>
    </view>

    <!-- 自定义区间 -->
    <view class="section">
      <text class="section-title">自定义区间</text>
      <view class="date-row">
        <picker mode="date" :value="startDate" :end="endDate || undefined" @change="onStartChange">
          <text class="date-val">{{ startDate || '开始日期' }}</text>
        </picker>
        <text class="date-sep">至</text>
        <picker mode="date" :value="endDate" :start="startDate || undefined" @change="onEndChange">
          <text class="date-val">{{ endDate || '结束日期' }}</text>
        </picker>
      </view>
      <text v-if="errorMsg" class="error-text">{{ errorMsg }}</text>
      <text class="hint-text">上限 {{ MAX_DAYS }} 天</text>
    </view>

    <view class="btn-area">
      <button class="btn-query" :disabled="loading" @tap="handleQuery">{{ loading ? '查询中...' : '查询' }}</button>
    </view>

    <!-- 结果 -->
    <view v-if="result" class="result-section">
      <view class="stat-cards">
        <view class="stat-card"><text class="s-val">{{ formatDuration(result.workMinutes) }}</text><text class="s-label">区间工时</text></view>
        <view class="stat-card"><text class="s-val overtime">{{ formatDuration(result.overtimeMinutes) }}</text><text class="s-label">加班</text></view>
        <view class="stat-card"><text class="s-val">{{ result.attendanceDays }}</text><text class="s-label">出勤天数</text></view>
        <view class="stat-card"><text class="s-val">{{ result.avgDailyWorkMinutes }}分</text><text class="s-label">日均工时</text></view>
      </view>
      <button class="btn-export" @tap="handleExport">一键导出报表</button>
    </view>
  </view>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { summaryApi } from '@/api/summary'
import { validateDateRange } from '@/utils/validator'
import { formatDisplayDuration } from '@/utils/time'
import { MAX_CUSTOM_RANGE_DAYS } from '@/types/common'
import type { CustomSummary } from '@/types/summary'

const presets = [
  { label: '近7天', value: 'LAST_7_DAYS' },
  { label: '近30天', value: 'LAST_30_DAYS' },
  { label: '近90天', value: 'LAST_90_DAYS' },
  { label: '本季度', value: 'THIS_QUARTER' },
]

const startDate = ref('')
const endDate = ref('')
const errorMsg = ref('')
const loading = ref(false)
const result = ref<CustomSummary | null>(null)

function formatDuration(min: number) { return formatDisplayDuration(min) }

function onStartChange(e: any) { startDate.value = e.detail.value; errorMsg.value = '' }
function onEndChange(e: any) { endDate.value = e.detail.value; errorMsg.value = '' }

async function applyPreset(preset: string) {
  loading.value = true; errorMsg.value = ''
  try { result.value = await summaryApi.getQuick(preset) } catch {}
  loading.value = false
}

async function handleQuery() {
  const v = validateDateRange(startDate.value, endDate.value, MAX_CUSTOM_RANGE_DAYS)
  if (!v.valid) { errorMsg.value = v.message; return }
  errorMsg.value = ''
  loading.value = true
  try {
    result.value = await summaryApi.getCustom(startDate.value, endDate.value)
  } catch (e: any) {
    uni.showToast({ title: e.message || '查询失败', icon: 'none' })
  }
  loading.value = false
}

function handleExport() {
  uni.navigateTo({ url: `/pages/report/create?start=${startDate.value}&end=${endDate.value}` })
}
</script>

<style lang="scss" scoped>
.custom-range-page { min-height: 100vh; background: $bg-secondary; padding-top: 12px; }

.section { margin: 0 $page-padding 16px; }
.section-title { font-size: $font-size-sm; color: $text-secondary; margin-bottom: 10px; display: block; }

.quick-presets { display: flex; gap: 8px; }
.preset-item { padding: 8px 16px; font-size: $font-size-sm; background: $bg-primary; border-radius: $border-radius-round; color: $primary-color; }

.date-row { display: flex; align-items: center; gap: 10px; }
.date-val { font-size: $font-size-md; color: $primary-color; padding: 8px 14px; background: $bg-primary; border-radius: $border-radius-sm; }
.date-sep { color: $text-tertiary; }

.error-text { font-size: $font-size-xs; color: $danger-color; margin-top: 6px; display: block; }
.hint-text { font-size: $font-size-xs; color: $text-tertiary; margin-top: 6px; display: block; }

.btn-area { padding: 8px $page-padding; }
.btn-query { width: 100%; height: 46px; background: $primary-color; color: #FFF; font-size: $font-size-md; font-weight: 600; border-radius: $border-radius-sm; border: none; }

.result-section { margin: 12px $page-padding; }
.stat-cards { display: flex; flex-wrap: wrap; gap: 8px; margin-bottom: 14px; }
.stat-card { flex: 1 1 calc(50% - 8px); text-align: center; background: $bg-primary; border-radius: $border-radius-md; padding: 16px 8px; }
.s-val { font-size: $font-size-lg; font-weight: 700; color: $primary-color; display: block; }
.s-val.overtime { color: $warning-color; }
.s-label { font-size: $font-size-xs; color: $text-tertiary; margin-top: 2px; display: block; }

.btn-export { width: 100%; height: 44px; background: $success-color; color: #FFF; font-size: $font-size-md; font-weight: 600; border-radius: $border-radius-sm; border: none; }
</style>
