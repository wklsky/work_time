<template>
  <view class="calendar-page">
    <!-- 月份导航 -->
    <view class="cal-nav">
      <view class="nav-btn" @tap="goPrev"><text>‹</text></view>
      <text class="cal-title">{{ year }}年{{ month }}月</text>
      <view class="nav-btn" @tap="goNext"><text>›</text></view>
    </view>

    <view class="weekday-row">
      <text v-for="wd in weekdays" :key="wd" class="weekday-cell" :class="{ weekend: wd === '六' || wd === '日' }">{{ wd }}</text>
    </view>

    <view class="day-grid">
      <view v-for="i in firstDayOfWeek - 1" :key="'p' + i" class="day-cell empty" />

      <view
        v-for="day in days"
        :key="day.date"
        class="day-cell"
        :class="{
          'is-today': day.isToday,
          'is-weekend': day.weekday === 0 || day.weekday === 6,
        }"
        @tap="onDayTap(day)"
      >
        <text class="day-num">{{ getDayNum(day.date) }}</text>
        <view v-if="day.hasRecord" class="day-indicators">
          <view v-if="day.overtimeLevel" class="day-dot" :class="'dot-' + day.overtimeLevel.toLowerCase()" />
          <text class="day-work">{{ formatShort(day.workMinutes) }}</text>
        </view>
      </view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { attendanceApi } from '@/api/attendance'
import { getTodayDateString, getDaysInMonth, getFirstDayOfWeek, addMonths, subMonths } from '@/utils/date'
import { formatDisplayDuration } from '@/utils/time'
import type { CalendarDay } from '@/types/attendance'

const year = ref(new Date().getFullYear())
const month = ref(new Date().getMonth() + 1)
const days = ref<CalendarDay[]>([])
const firstDayOfWeek = ref(7)
const weekdays = ['一', '二', '三', '四', '五', '六', '日']

function getDayNum(date: string) { return String(parseInt(date.split('-')[2], 10)) }
function formatShort(min: number) { return formatDisplayDuration(min) }

function goPrev() {
  const r = subMonths(year.value, month.value, 1)
  year.value = r.year; month.value = r.month; fetchData()
}
function goNext() {
  const r = addMonths(year.value, month.value, 1)
  year.value = r.year; month.value = r.month; fetchData()
}
function onDayTap(day: CalendarDay) {
  uni.showToast({ title: `${day.date} ${formatShort(day.workMinutes)}`, icon: 'none' })
}

async function fetchData() {
  const ds = `${year.value}-${String(month.value).padStart(2, '0')}-01`
  try {
    const data = await attendanceApi.getCalendar('MONTH', ds)
    if (data.view === 'MONTH') {
      days.value = data.days
      firstDayOfWeek.value = data.firstDayOfWeek
    }
  } catch {}
}

onMounted(fetchData)
</script>

<style lang="scss" scoped>
.calendar-page { min-height: 100vh; background: $bg-primary; padding: 8px $page-padding; }

.cal-nav { display: flex; align-items: center; justify-content: space-between; padding: 12px 0; }
.nav-btn { width: 40px; height: 40px; display: flex; align-items: center; justify-content: center; font-size: 26px; color: $primary-color; }
.cal-title { font-size: $font-size-lg; font-weight: 700; }

.weekday-row { display: flex; margin-bottom: 4px; }
.weekday-cell { flex: 1; text-align: center; font-size: $font-size-xs; color: $text-tertiary; padding: 8px 0; }
.weekday-cell.weekend { color: $danger-color; }

.day-grid { display: flex; flex-wrap: wrap; }
.day-cell {
  width: calc(100% / 7); aspect-ratio: 1;
  display: flex; flex-direction: column; align-items: center; justify-content: flex-start;
  padding-top: 6px; font-size: $font-size-md; border-radius: 8px; position: relative;
}
.day-cell.empty { visibility: hidden; }
.day-cell.is-today { background: $primary-light; }
.day-num { font-weight: 500; }
.is-today .day-num { color: $primary-color; font-weight: 700; }
.is-weekend .day-num { color: $danger-color; }
.day-indicators { display: flex; flex-direction: column; align-items: center; margin-top: 2px; }
.day-dot { width: 6px; height: 6px; border-radius: 50%; }
.dot-low { background: $overtime-low; }
.dot-medium { background: $overtime-medium; }
.dot-high { background: $overtime-high; }
.dot-none { background: $bg-tertiary; }
.day-work { font-size: 8px; color: $text-tertiary; margin-top: 1px; }
</style>
