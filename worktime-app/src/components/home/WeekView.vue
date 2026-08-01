<template>
  <view class="week-view">
    <!-- 周导航 -->
    <view class="week-nav">
      <view class="nav-arrow" @tap="$emit('prev')"><text>‹</text></view>
      <text class="week-title">{{ weekStart }} ~ {{ weekEnd }}</text>
      <view class="nav-arrow" @tap="$emit('next')"><text>›</text></view>
    </view>

    <!-- 7天列表 -->
    <view class="week-list">
      <view
        v-for="day in days"
        :key="day.date"
        class="week-day-item"
        :class="{ 'is-today': day.isToday }"
        @tap="$emit('dayClick', day.date)"
      >
        <view class="day-info">
          <text class="day-name" :class="{ 'is-today-text': day.isToday }">{{ day.weekdayName }}</text>
          <text class="day-date">{{ shortDate(day.date) }}</text>
        </view>

        <view v-if="day.hasRecord" class="day-bars">
          <view class="bar-bg">
            <view
              class="bar-work"
              :style="{ width: day.workPercentage + '%' }"
            />
          </view>
          <view class="day-times">
            <text v-if="day.firstClockIn" class="time-text">{{ formatTime(day.firstClockIn) }}</text>
            <text v-if="day.lastClockOut" class="time-text">{{ formatTime(day.lastClockOut) }}</text>
          </view>
        </view>
        <view v-else class="day-empty">
          <text class="empty-text">无记录</text>
        </view>

        <text v-if="day.hasRecord" class="day-worktime">{{ formatDuration(day.workMinutes) }}</text>
      </view>
    </view>

    <!-- 周合计 -->
    <view v-if="weekTotal" class="week-total-bar">
      <view class="total-item">
        <text class="total-label">周合计</text>
        <text class="total-value">{{ formatDuration(weekTotal.workMinutes) }}</text>
      </view>
      <view class="total-item">
        <text class="total-label">加班</text>
        <text class="total-value overtime">{{ formatDuration(weekTotal.overtimeMinutes) }}</text>
      </view>
      <view class="total-item">
        <text class="total-label">出勤</text>
        <text class="total-value">{{ weekTotal.attendanceDays }}天</text>
      </view>
    </view>
  </view>
</template>

<script setup lang="ts">
import type { WeekDay, WeekTotal } from '@/types/attendance'
import { formatDisplayTime, formatDisplayDuration } from '@/utils/time'

defineProps<{
  days: WeekDay[]
  weekStart: string
  weekEnd: string
  weekTotal: WeekTotal | null
}>()

defineEmits<{
  prev: []
  next: []
  dayClick: [date: string]
}>()

function shortDate(dateStr: string): string {
  const parts = dateStr.split('-')
  return `${parseInt(parts[1])}/${parseInt(parts[2])}`
}

function formatTime(utcStr: string | null): string {
  if (!utcStr) return ''
  return formatDisplayTime(utcStr, '24H')
}

function formatDuration(min: number): string {
  return formatDisplayDuration(min)
}
</script>

<style lang="scss" scoped>
.week-view { padding: 8px $page-padding 12px; }

.week-nav {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 8px 0;
}

.nav-arrow { width: 36px; height: 36px; display: flex; align-items: center; justify-content: center; font-size: 24px; color: $primary-color; }
.week-title { font-size: $font-size-sm; color: $text-secondary; }

.week-list { margin-top: 4px; }

.week-day-item {
  display: flex;
  align-items: center;
  padding: 12px 0;
  border-bottom: 1px solid $divider-color;
}

.is-today { background: $primary-light; border-radius: $border-radius-sm; padding-left: 10px; padding-right: 10px; }

.day-info { width: 48px; }
.day-name { font-size: $font-size-sm; font-weight: 600; display: block; }
.is-today-text { color: $primary-color; }
.day-date { font-size: $font-size-xs; color: $text-tertiary; }

.day-bars { flex: 1; margin: 0 12px; }
.bar-bg { height: 6px; background: $bg-tertiary; border-radius: 3px; overflow: hidden; }
.bar-work { height: 100%; background: $primary-color; border-radius: 3px; transition: width 0.3s; }
.day-times { display: flex; justify-content: space-between; margin-top: 4px; }
.time-text { font-size: $font-size-xs; color: $text-tertiary; }

.day-empty { flex: 1; }
.empty-text { font-size: $font-size-xs; color: $text-placeholder; }

.day-worktime { font-size: $font-size-sm; color: $text-secondary; font-weight: 500; min-width: 60px; text-align: right; }

// 周合计
.week-total-bar {
  display: flex;
  justify-content: space-around;
  padding: 14px 0;
  margin-top: 8px;
  background: $bg-secondary;
  border-radius: $border-radius-sm;
}

.total-item { text-align: center; }
.total-label { font-size: $font-size-xs; color: $text-tertiary; display: block; }
.total-value { font-size: $font-size-md; font-weight: 700; color: $text-primary; margin-top: 2px; }
.total-value.overtime { color: $warning-color; }
</style>
