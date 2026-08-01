<template>
  <view class="create-page">
    <view class="form-section card">
      <text class="form-section-title">导出类型</text>
      <view class="type-grid">
        <view
          v-for="t in types" :key="t.value"
          class="type-item"
          :class="{ active: form.exportType === t.value }"
          @tap="form.exportType = t.value"
        >{{ t.label }}</view>
      </view>
    </view>

    <view class="form-section card" v-if="form.exportType === 'CUSTOM'">
      <text class="form-section-title">日期区间</text>
      <view class="date-row">
        <picker mode="date" :value="form.startDate" @change="onStartChange">
          <text class="date-picker">{{ form.startDate || '开始日期' }}</text>
        </picker>
        <text class="date-sep">~</text>
        <picker mode="date" :value="form.endDate" @change="onEndChange">
          <text class="date-picker">{{ form.endDate || '结束日期' }}</text>
        </picker>
      </view>
    </view>

    <view class="form-section card">
      <text class="form-section-title">文件格式</text>
      <view class="type-grid">
        <view
          v-for="f in formats" :key="f.value"
          class="type-item"
          :class="{ active: form.fileFormat === f.value }"
          @tap="form.fileFormat = f.value"
        >{{ f.label }}</view>
      </view>
    </view>

    <view class="btn-area">
      <button class="btn-submit" :disabled="submitting" @tap="handleSubmit">
        {{ submitting ? '创建中...' : '创建导出任务' }}
      </button>
    </view>

    <!-- 结果提示 -->
    <view v-if="exportStatus" class="status-card card">
      <text v-if="exportStatus === 'PROCESSING'" class="s-processing">⏳ 生成中，预计 {{ estimatedSeconds }} 秒...</text>
      <text v-else-if="exportStatus === 'SUCCESS'" class="s-success">✅ 导出成功</text>
      <text v-else class="s-failed">❌ 导出失败</text>
    </view>
  </view>
</template>

<script setup lang="ts">
import { reactive, ref } from 'vue'
import { exportApi } from '@/api/export'
import { validateDateRange } from '@/utils/validator'
import type { ExportType, FileFormat, ExportStatus } from '@/types/exports'

const types = [
  { label: '日', value: 'DAY' as ExportType },
  { label: '周', value: 'WEEK' as ExportType },
  { label: '月', value: 'MONTH' as ExportType },
  { label: '年', value: 'YEAR' as ExportType },
  { label: '自定义', value: 'CUSTOM' as ExportType },
]
const formats = [
  { label: 'Excel', value: 'EXCEL' as FileFormat },
  { label: 'CSV', value: 'CSV' as FileFormat },
  { label: 'PDF', value: 'PDF' as FileFormat },
]

const form = reactive({
  exportType: 'MONTH' as ExportType,
  startDate: '',
  endDate: '',
  fileFormat: 'EXCEL' as FileFormat,
})
const submitting = ref(false)
const exportStatus = ref<ExportStatus | null>(null)
const estimatedSeconds = ref(30)
let pollInterval: ReturnType<typeof setInterval> | null = null

function onStartChange(e: any) { form.startDate = e.detail.value }
function onEndChange(e: any) { form.endDate = e.detail.value }

async function handleSubmit() {
  if (form.exportType === 'CUSTOM') {
    const v = validateDateRange(form.startDate, form.endDate)
    if (!v.valid) { uni.showToast({ title: v.message, icon: 'none' }); return }
  }

  submitting.value = true
  try {
    const result = await exportApi.create({
      exportType: form.exportType,
      startDate: form.exportType === 'CUSTOM' ? form.startDate : undefined,
      endDate: form.exportType === 'CUSTOM' ? form.endDate : undefined,
      fileFormat: form.fileFormat,
    })
    exportStatus.value = 'PROCESSING'
    estimatedSeconds.value = result.estimatedSeconds

    // 轮询状态
    let polls = 0
    pollInterval = setInterval(async () => {
      try {
        const status = await exportApi.getStatus(result.exportId)
        if (status.status === 'SUCCESS') {
          exportStatus.value = 'SUCCESS'
          clearInterval(pollInterval!)
          uni.showToast({ title: '导出成功', icon: 'success' })
        } else if (status.status === 'FAILED') {
          exportStatus.value = 'FAILED'
          clearInterval(pollInterval!)
          uni.showToast({ title: status.errorMsg || '导出失败', icon: 'none' })
        }
        if (++polls >= 60) {
          clearInterval(pollInterval!)
          uni.showToast({ title: '导出超时，请稍后查看', icon: 'none' })
        }
      } catch {
        clearInterval(pollInterval!)
      }
    }, 3000)
  } catch (e: any) {
    uni.showToast({ title: e.message || '创建失败', icon: 'none' })
  } finally {
    submitting.value = false
  }
}
</script>

<style lang="scss" scoped>
.create-page { min-height: 100vh; background: $bg-secondary; padding-top: 12px; }

.form-section { margin: 0 $page-padding 12px; }
.form-section-title { font-size: $font-size-sm; color: $text-secondary; margin-bottom: 12px; display: block; }

.type-grid { display: flex; gap: 8px; flex-wrap: wrap; }
.type-item {
  padding: 8px 18px; font-size: $font-size-sm; background: $bg-secondary;
  border-radius: $border-radius-round; color: $text-secondary;
}
.type-item.active { background: $primary-color; color: #FFF; font-weight: 600; }

.date-row { display: flex; align-items: center; gap: 8px; }
.date-picker { font-size: $font-size-sm; color: $primary-color; padding: 6px 12px; background: $primary-light; border-radius: $border-radius-sm; }
.date-sep { color: $text-tertiary; }

.btn-area { padding: 8px $page-padding; }
.btn-submit { width: 100%; height: 46px; background: $primary-color; color: #FFF; font-size: $font-size-md; font-weight: 600; border-radius: $border-radius-sm; border: none; }

.status-card { text-align: center; padding: 20px; }
.s-processing { color: $warning-color; font-size: $font-size-md; }
.s-success { color: $success-color; font-size: $font-size-md; }
.s-failed { color: $danger-color; font-size: $font-size-md; }
</style>
