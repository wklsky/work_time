<template>
  <view class="today-view">
    <view class="today-header">
      <text class="today-label">{{ date }}</text>
      <text class="today-status" :class="statusClass">{{ statusLabel }}</text>
    </view>

    <!-- 时间轴 -->
    <view class="timeline">
      <view class="tl-standard">
        <text class="tl-time">{{ timeline.standardStart }}</text>
        <view class="tl-line" />
        <text class="tl-time">{{ timeline.standardEnd }}</text>
      </view>
      <view class="tl-rest">
        <text class="tl-rest-text">午休 {{ timeline.restStart }}-{{ timeline.restEnd }}</text>
      </view>

      <!-- 记录块 -->
      <view
        v-for="rec in records"
        :key="rec.id"
        class="tl-record"
        :class="{ 'is-pending': rec.status === 'PENDING' }"
      >
        <text class="rec-time">{{ displayTime(rec.clockIn) }}</text>
        <text class="rec-arrow">→</text>
        <text class="rec-time">{{ rec.clockOut ? displayTime(rec.clockOut) : '进行中' }}</text>
        <text class="rec-duration">{{ formatMinutes(rec.workMinutes) }}</text>
        <text v-if="rec.source === 'MANUAL'" class="rec-source">手动</text>
      </view>

      <view v-if="records.length === 0" class="tl-empty">
        <text>今日暂无记录</text>
      </view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import type { ClockStatus, TimelineConfig, AttendanceRecord } from '@/types/attendance'
import { formatDisplayTime, formatDisplayDuration } from '@/utils/time'

const props = defineProps<{
  date: string
  records: AttendanceRecord[]
  clockStatus: ClockStatus
  timeline: TimelineConfig
}>()

const statusLabel = computed(() => {
  switch (props.clockStatus) {
    case 'WORKING': return '工作中'
    case 'COMPLETED': return '已完成'
    default: return '未开始'
  }
})

const statusClass = computed(() => ({
  's-working': props.clockStatus === 'WORKING',
  's-completed': props.clockStatus === 'COMPLETED',
  's-idle': props.clockStatus === 'NOT_STARTED',
}))

function displayTime(utc: string): string {
  return formatDisplayTime(utc, '24H')
}

function formatMinutes(min: number): string {
  return formatDisplayDuration(min)
}
</script>

<style lang="scss" scoped>
.today-view { padding: 12px $page-padding; }

.today-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
}

.today-label { font-size: $font-size-lg; font-weight: 700; }

.today-status {
  font-size: $font-size-sm;
  padding: 4px 12px;
  border-radius: $border-radius-round;
}
.s-working { background: #E8F5E9; color: $success-color; }
.s-completed { background: $bg-secondary; color: $text-secondary; }
.s-idle { background: $bg-secondary; color: $text-tertiary; }

// 时间轴
.timeline { padding: 4px 0; }

.tl-standard {
  display: flex;
  align-items: center;
  margin-bottom: 4px;
}

.tl-time { font-size: $font-size-sm; color: $text-secondary; min-width: 44px; }

.tl-line {
  flex: 1;
  height: 2px;
  background: $primary-color;
  margin: 0 8px;
  opacity: 0.3;
}

.tl-rest {
  text-align: center;
  margin-bottom: 12px;
}

.tl-rest-text {
  font-size: $font-size-xs;
  color: $text-tertiary;
  background: $bg-secondary;
  padding: 2px 10px;
  border-radius: $border-radius-round;
}

.tl-record {
  display: flex;
  align-items: center;
  padding: 10px 12px;
  background: $bg-secondary;
  border-radius: $border-radius-sm;
  margin-bottom: 8px;
}

.tl-record.is-pending {
  background: $primary-light;
  border-left: 3px solid $primary-color;
}

.rec-time { font-size: $font-size-sm; color: $text-primary; font-weight: 500; }
.rec-arrow { margin: 0 6px; color: $text-tertiary; font-size: $font-size-xs; }
.rec-duration { margin-left: auto; font-size: $font-size-sm; color: $text-secondary; }
.rec-source { font-size: $font-size-xs; color: $text-tertiary; margin-left: 8px; background: $bg-tertiary; padding: 1px 6px; border-radius: $border-radius-round; }

.tl-empty { text-align: center; padding: 20px 0; color: $text-tertiary; font-size: $font-size-sm; }
</style>
