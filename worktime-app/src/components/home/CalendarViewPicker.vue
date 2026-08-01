<template>
  <view v-if="visible" class="picker-overlay" @tap="$emit('close')">
    <view class="picker-sheet" @tap.stop>
      <view class="picker-header">
        <text class="picker-title">日历展示方式</text>
        <text class="picker-close" @tap="$emit('close')">✕</text>
      </view>
      <view class="picker-options">
        <view
          v-for="opt in options"
          :key="opt.value"
          class="picker-option"
          :class="{ selected: current === opt.value }"
          @tap="$emit('select', opt.value)"
        >
          <text class="option-icon">{{ opt.icon }}</text>
          <view class="option-text">
            <text class="option-label">{{ opt.label }}</text>
            <text class="option-desc">{{ opt.desc }}</text>
          </view>
          <text v-if="current === opt.value" class="option-check">✓</text>
        </view>
      </view>
    </view>
  </view>
</template>

<script setup lang="ts">
import type { CalendarViewType } from '@/types/attendance'

defineProps<{
  visible: boolean
  current: CalendarViewType
}>()

defineEmits<{
  select: [view: CalendarViewType]
  close: []
}>()

const options = [
  { icon: '📅', label: '月', desc: '完整日历网格，适合查看全月记录', value: 'MONTH' as CalendarViewType },
  { icon: '📊', label: '星期', desc: '7天横向展示，聚焦本周安排', value: 'WEEK' as CalendarViewType },
  { icon: '📍', label: '今日', desc: '仅今天，打卡区与时间轴放大', value: 'TODAY' as CalendarViewType },
]
</script>

<style lang="scss" scoped>
.picker-overlay {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: rgba(0, 0, 0, 0.4);
  z-index: 1000;
  display: flex;
  align-items: flex-end;
}

.picker-sheet {
  width: 100%;
  background: $bg-primary;
  border-radius: $border-radius-lg $border-radius-lg 0 0;
  padding-bottom: $safe-area-bottom;
  animation: slideUp 0.3s ease;
}

.picker-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 16px $page-padding;
  border-bottom: 1px solid $divider-color;
}

.picker-title {
  font-size: $font-size-lg;
  font-weight: 600;
}

.picker-close {
  font-size: 20px;
  color: $text-tertiary;
  padding: 4px;
}

.picker-options {
  padding: 8px 0;
}

.picker-option {
  display: flex;
  align-items: center;
  padding: 16px $page-padding;
  transition: background 0.15s;
}

.picker-option:active {
  background: $bg-secondary;
}

.picker-option.selected {
  background: $primary-light;
}

.option-icon {
  font-size: 28px;
  margin-right: 14px;
}

.option-text {
  flex: 1;
}

.option-label {
  font-size: $font-size-md;
  font-weight: 600;
  display: block;
}

.option-desc {
  font-size: $font-size-xs;
  color: $text-tertiary;
  margin-top: 2px;
}

.option-check {
  font-size: 18px;
  color: $primary-color;
  font-weight: 700;
}
</style>
