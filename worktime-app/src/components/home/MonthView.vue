<template>
  <view class="month-view">
    <!-- 月份导航 -->
    <view class="month-nav">
      <view class="nav-arrow" @tap="$emit('prev')"><text>‹</text></view>
      <text class="month-title">{{ year }}年{{ month }}月</text>
      <view class="nav-arrow" @tap="$emit('next')"><text>›</text></view>
    </view>

    <!-- 星期头 -->
    <view class="weekday-row">
      <text v-for="wd in weekdays" :key="wd" class="weekday-cell">{{ wd }}</text>
    </view>

    <!-- 日期网格 (6x7) -->
    <view class="day-grid">
      <!-- 前置空白 -->
      <view
        v-for="i in firstDayOfWeek - 1"
        :key="'pad-' + i"
        class="day-cell empty"
      />

      <!-- 日期 -->
      <view
        v-for="day in days"
        :key="day.date"
        class="day-cell"
        :class="{
          'is-today': day.isToday,
          'has-record': day.hasRecord,
        }"
        @tap="$emit('dayClick', day.date)"
      >
        <text class="day-num">{{ dayNum(day.date) }}</text>
        <view
          v-if="day.hasRecord && day.overtimeLevel"
          class="day-dot"
          :class="'dot-' + (day.overtimeLevel?.toLowerCase())"
        />
      </view>
    </view>
  </view>
</template>

<script setup lang="ts">
import type { CalendarDay } from '@/types/attendance'

defineProps<{
  days: CalendarDay[]
  year: number
  month: number
  firstDayOfWeek: number
}>()

defineEmits<{
  prev: []
  next: []
  dayClick: [date: string]
}>()

const weekdays = ['一', '二', '三', '四', '五', '六', '日']

function dayNum(dateStr: string): string {
  const parts = dateStr.split('-')
  return String(parseInt(parts[2], 10))
}
</script>

<style lang="scss" scoped>
.month-view { padding: 8px $page-padding 12px; }

.month-nav {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 8px 0;
}

.nav-arrow {
  width: 36px;
  height: 36px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 24px;
  color: $primary-color;
}

.month-title {
  font-size: $font-size-md;
  font-weight: 600;
}

.weekday-row {
  display: flex;
  margin-bottom: 4px;
}

.weekday-cell {
  flex: 1;
  text-align: center;
  font-size: $font-size-xs;
  color: $text-tertiary;
  padding: 6px 0;
}

.day-grid {
  display: flex;
  flex-wrap: wrap;
}

.day-cell {
  width: calc(100% / 7);
  aspect-ratio: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  position: relative;
  font-size: $font-size-sm;
  border-radius: $border-radius-sm;
}

.day-cell.empty { visibility: hidden; }

.day-cell.is-today {
  background: $primary-light;
}

.day-num {
  color: $text-primary;
  font-weight: 500;
}

.is-today .day-num {
  color: $primary-color;
  font-weight: 700;
}

.day-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  margin-top: 2px;
}

.dot-low { background: $overtime-low; }
.dot-medium { background: $overtime-medium; }
.dot-high { background: $overtime-high; }
</style>
