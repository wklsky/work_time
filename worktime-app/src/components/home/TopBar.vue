<template>
  <view class="top-bar safe-area-top">
    <view class="top-left" @tap="$emit('openViewPicker')">
      <text class="calendar-icon">📅</text>
    </view>
    <view class="top-center">
      <text class="date-text" v-if="dateInfo">{{ formattedDate }}</text>
      <text class="date-text" v-else>加载中...</text>
    </view>
    <view class="top-right">
      <!-- 可扩展设置入口 -->
    </view>
  </view>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import type { DateInfo } from '@/types/attendance'

const props = defineProps<{
  dateInfo?: DateInfo | null
  currentView: string
}>()

defineEmits<{
  openViewPicker: []
}>()

const formattedDate = computed(() => {
  if (!props.dateInfo) return ''
  return `${props.dateInfo.date} ${props.dateInfo.weekday}`
})
</script>

<style lang="scss" scoped>
.top-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 8px $page-padding;
  background: $bg-primary;
  border-bottom: 1px solid $border-color;
  min-height: 44px;
}

.top-left {
  width: 40px;
  height: 40px;
  display: flex;
  align-items: center;
  justify-content: center;
}

.calendar-icon {
  font-size: 22px;
}

.top-center {
  flex: 1;
  text-align: center;
}

.date-text {
  font-size: $font-size-md;
  font-weight: 600;
  color: $text-primary;
}

.top-right {
  width: 40px;
}
</style>
