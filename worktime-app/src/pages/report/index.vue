<template>
  <view class="report-page">
    <view class="header-bar">
      <text class="page-title">报表导出</text>
      <button class="btn-new" size="mini" @tap="goCreate">新建导出</button>
    </view>

    <view v-if="exports.length === 0" class="empty">
      <EmptyState text="暂无导出记录" />
    </view>

    <view v-for="item in exports" :key="item.exportId" class="export-item">
      <view class="export-info">
        <text class="export-type">{{ typeLabel(item.exportType) }}</text>
        <text class="export-date" v-if="item.startDate">{{ item.startDate }} ~ {{ item.endDate }}</text>
        <text class="export-format">{{ item.fileFormat }}</text>
      </view>
      <view class="export-status">
        <text v-if="item.status === 'PROCESSING'" class="status processing">生成中...</text>
        <text v-else-if="item.status === 'SUCCESS'" class="status success" @tap="download(item)">下载</text>
        <text v-else class="status failed">失败</text>
      </view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { exportApi } from '@/api/export'
import type { ExportRecord } from '@/types/exports'
import EmptyState from '@/components/common/EmptyState.vue'

const exports = ref<ExportRecord[]>([])

const typeLabel = (t: string) => ({ DAY: '日', WEEK: '周', MONTH: '月', YEAR: '年', CUSTOM: '自定义' }[t] || t)

function goCreate() { uni.navigateTo({ url: '/pages/report/create' }) }

function download(item: ExportRecord) {
  if (item.fileUrl) {
    uni.showToast({ title: '下载链接: ' + item.fileUrl, icon: 'none' })
  }
}

onMounted(async () => {
  try {
    const data = await exportApi.getHistory()
    exports.value = data.records
  } catch {}
})
</script>

<style lang="scss" scoped>
.report-page { min-height: 100vh; background: $bg-secondary; }

.header-bar { display: flex; justify-content: space-between; align-items: center; padding: 16px $page-padding; background: $bg-primary; border-bottom: 1px solid $border-color; }
.page-title { font-size: $font-size-lg; font-weight: 700; }
.btn-new { background: $primary-color; color: #FFF; border: none; font-size: $font-size-sm; }

.export-item {
  display: flex; align-items: center; justify-content: space-between;
  padding: 14px $page-padding; background: $bg-primary; border-bottom: 1px solid $divider-color;
}
.export-info { flex: 1; }
.export-type { font-size: $font-size-md; font-weight: 600; display: block; }
.export-date { font-size: $font-size-xs; color: $text-tertiary; }
.export-format { font-size: $font-size-xs; color: $text-tertiary; margin-left: 8px; }

.status { font-size: $font-size-sm; padding: 4px 12px; border-radius: $border-radius-round; }
.status.processing { color: $warning-color; background: #FFF8E1; }
.status.success { color: $primary-color; background: $primary-light; }
.status.failed { color: $danger-color; background: #FFEBEE; }

.empty { padding-top: 80px; }
</style>
