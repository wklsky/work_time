<template>
  <view class="home-page">
    <!-- Zone 1: 顶部栏 -->
    <TopBar
      :dateInfo="todayData?.dateInfo"
      :currentView="calendarView"
      @open-view-picker="showViewPicker = true"
    />

    <!-- Zone 2: 日历区 -->
    <view class="calendar-zone">
      <MonthView
        v-if="calendarView === 'MONTH'"
        :days="monthDays"
        :year="monthYear"
        :month="monthMonth"
        :firstDayOfWeek="monthFirstDayOfWeek"
        @day-click="onDayClick"
        @prev="calendarPrev"
        @next="calendarNext"
      />
      <WeekView
        v-else-if="calendarView === 'WEEK'"
        :days="weekDays"
        :weekStart="weekStart"
        :weekEnd="weekEnd"
        :weekTotal="weekTotal"
        @day-click="onDayClick"
        @prev="calendarPrev"
        @next="calendarNext"
      />
      <TodayView
        v-else
        :date="todayDate"
        :records="todayRecords"
        :clockStatus="clockStatus"
        :timeline="workRuleTimeline"
      />
    </view>

    <!-- Zone 3: 打卡区 -->
    <view class="clockin-zone card">
      <!-- 模式切换 -->
      <view class="mode-switch-row">
        <view
          class="mode-tab"
          :class="{ active: inputMode === 'BUTTON' }"
          @tap="switchInputMode('BUTTON')"
        >按钮模式</view>
        <view
          class="mode-tab"
          :class="{ active: inputMode === 'MANUAL' }"
          @tap="switchInputMode('MANUAL')"
        >手动模式</view>
      </view>

      <!-- 按钮模式 -->
      <view v-if="inputMode === 'BUTTON'" class="button-mode">
        <view
          class="clock-btn"
          :class="clockBtnClass"
          @tap="handleClockAction"
        >
          <text class="clock-btn-icon">{{ clockBtnIcon }}</text>
          <text class="clock-btn-text">{{ clockBtnLabel }}</text>
        </view>
        <!-- 实时计时 -->
        <view v-if="clockStatus === 'WORKING'" class="elapsed-timer">
          <text class="timer-dot" />
          <text class="timer-text">{{ formattedElapsed }}</text>
        </view>
      </view>

      <!-- 手动模式 -->
      <view v-if="inputMode === 'MANUAL'" class="manual-mode">
        <view class="manual-row">
          <text class="manual-label">开始时间</text>
          <picker
            mode="time"
            :value="manualStartTime"
            @change="onManualStartChange"
          >
            <view class="manual-picker">{{ manualStartTime || '请选择' }}</view>
          </picker>
        </view>
        <view class="manual-row">
          <text class="manual-label">结束时间</text>
          <picker
            mode="time"
            :value="manualEndTime"
            @change="onManualEndChange"
          >
            <view class="manual-picker">{{ manualEndTime || '请选择（可选）' }}</view>
          </picker>
        </view>
        <view v-if="crossDayWarning" class="cross-day-warning">
          ⚠️ 检测到跨天记录，结束时间将按次日处理
        </view>
        <view class="manual-remark">
          <input v-model="manualRemark" class="remark-input" placeholder="备注（可选）" />
        </view>
        <button class="btn-submit" @tap="handleManualSubmit">提交记录</button>
      </view>
    </view>

    <!-- Zone 4: 工时区 (条件显示) -->
    <view v-if="showWorktimeZone && lastClockOutResult" class="worktime-zone">
      <WorktimeCard :result="lastClockOutResult" />
      <TodayAccumulated v-if="todaySummary" :summary="todaySummary" />
    </view>

    <!-- 日历视图选择弹层 -->
    <CalendarViewPicker
      :visible="showViewPicker"
      :current="calendarView"
      @select="onViewSelect"
      @close="showViewPicker = false"
    />
  </view>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted, watch } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import { useSettingsStore } from '@/stores/settings'
import { homeApi, attendanceApi } from '@/api/attendance'
import { getTodayDateString, getWeekStart, getWeekEnd, addDays, subDays, addMonths, subMonths } from '@/utils/date'
import { formatElapsedSeconds, formatDisplayTime, formatDisplayDuration } from '@/utils/time'
import { validateManualTime } from '@/utils/validator'
import type {
  TodayData, CalendarViewType, TodayAccumulated, ClockStatus,
  CalendarDay, WeekDay, WeekTotal, TimelineConfig,
} from '@/types/attendance'
import type { ClockOutResult } from '@/types/attendance'

// Components
import TopBar from '@/components/home/TopBar.vue'
import CalendarViewPicker from '@/components/home/CalendarViewPicker.vue'
import MonthView from '@/components/home/MonthView.vue'
import WeekView from '@/components/home/WeekView.vue'
import TodayView from '@/components/home/TodayView.vue'
import WorktimeCard from '@/components/home/WorktimeCard.vue'
import TodayAccumulated from '@/components/home/TodayAccumulated.vue'

const settingsStore = useSettingsStore()

// ========== State ==========
const todayData = ref<TodayData | null>(null)
const clockStatus = ref<ClockStatus>('NOT_STARTED')
const todayRecords = ref<any[]>([])
const todaySummary = ref<TodayAccumulated | null>(null)
const inputMode = ref<'BUTTON' | 'MANUAL'>('BUTTON')
const showWorktimeZone = ref(false)
const lastClockOutResult = ref<ClockOutResult | null>(null)
const showViewPicker = ref(false)
const calendarView = ref<CalendarViewType>('TODAY')

// Calendar state
const todayDate = ref(getTodayDateString())
const monthDays = ref<CalendarDay[]>([])
const monthYear = ref(2026)
const monthMonth = ref(8)
const monthFirstDayOfWeek = ref(7)
const weekDays = ref<WeekDay[]>([])
const weekStart = ref('')
const weekEnd = ref('')
const weekTotal = ref<WeekTotal | null>(null)

// Manual input state
const manualStartTime = ref('')
const manualEndTime = ref('')
const manualRemark = ref('')
const crossDayWarning = ref(false)

// Timer state
const elapsedSeconds = ref(0)
let timerInterval: ReturnType<typeof setInterval> | null = null

// ========== Computed ==========
const clockBtnLabel = computed(() => {
  if (clockStatus.value === 'WORKING') return '结束工作'
  return '开始工作'
})
const clockBtnIcon = computed(() => {
  if (clockStatus.value === 'WORKING') return '⏹'
  return '▶'
})
const clockBtnClass = computed(() => ({
  'btn-working': clockStatus.value === 'WORKING',
  'btn-idle': clockStatus.value !== 'WORKING',
}))
const formattedElapsed = computed(() => formatElapsedSeconds(elapsedSeconds.value))

const workRuleTimeline = computed<TimelineConfig>(() => {
  const rule = settingsStore.workRule
  return {
    standardStart: rule?.startTime || '09:00',
    standardEnd: rule?.endTime || '18:00',
    restStart: rule?.restStart || '12:00',
    restEnd: rule?.restEnd || '13:00',
  }
})

// ========== Lifecycle ==========
onMounted(async () => {
  calendarView.value = settingsStore.homeCalendarView
  await fetchTodayData()
  await fetchCalendarData()
  startTimerIfNeeded()
})

onShow(async () => {
  await fetchTodayData()
  await fetchCalendarData()
})

onUnmounted(() => {
  stopTimer()
})

// ========== Data Fetching ==========
async function fetchTodayData() {
  try {
    const data = await homeApi.getToday()
    todayData.value = data
    clockStatus.value = data.clockStatus ?? 'NOT_STARTED'
    todayRecords.value = data.todayRecords || []
    todaySummary.value = data.todaySummary || null
    inputMode.value = (data.userSetting?.defaultInputMode as 'BUTTON' | 'MANUAL') || settingsStore.defaultInputMode

    // 如果有进行中的记录，启动计时器
    if (data.clockStatus === 'WORKING' && data.currentRecord) {
      const inTime = new Date(data.currentRecord.clockIn).getTime()
      elapsedSeconds.value = Math.floor((Date.now() - inTime) / 1000)
      startTimer()
    } else {
      stopTimer()
      elapsedSeconds.value = 0
    }
  } catch (e) {
    console.error('[Home] fetchTodayData failed:', e)
  }
}

async function fetchCalendarData() {
  try {
    const anchorDate = todayDate.value
    const data = await attendanceApi.getCalendar(calendarView.value, anchorDate)

    if (data.view === 'MONTH') {
      monthDays.value = data.days || []
      monthYear.value = data.year
      monthMonth.value = data.month
      monthFirstDayOfWeek.value = data.firstDayOfWeek
    } else if (data.view === 'WEEK') {
      weekDays.value = data.days || []
      weekStart.value = data.weekStart
      weekEnd.value = data.weekEnd
      weekTotal.value = data.weekTotal
    }
    // TODAY view data comes from fetchTodayData
  } catch (e) {
    console.error('[Home] fetchCalendarData failed:', e)
  }
}

// ========== Clock Actions ==========
async function handleClockAction() {
  try {
    if (clockStatus.value === 'NOT_STARTED' || clockStatus.value === 'COMPLETED') {
      // 开始工作
      await attendanceApi.clockIn()
      uni.showToast({ title: '开始工作', icon: 'success' })
      await fetchTodayData()
      startTimer()
    } else if (clockStatus.value === 'WORKING') {
      // 结束工作
      const result = await attendanceApi.clockOut()
      lastClockOutResult.value = result
      showWorktimeZone.value = settingsStore.showWorktimeAfterClockout
      stopTimer()

      const workStr = formatDisplayDuration(result.workMinutes)
      const overtimeStr = formatDisplayDuration(result.overtimeMinutes)
      uni.showToast({
        title: `本次${workStr}，加班${overtimeStr}`,
        icon: 'none',
        duration: 3000,
      })
      await fetchTodayData()
    }
  } catch (e: any) {
    uni.showToast({ title: e.message || '操作失败', icon: 'none' })
  }
}

async function handleManualSubmit() {
  // 校验
  const validation = validateManualTime(manualStartTime.value, manualEndTime.value || undefined)
  if (!validation.valid) {
    uni.showToast({ title: validation.message, icon: 'none' })
    return
  }
  crossDayWarning.value = !!validation.crossDay

  try {
    const clockIn = new Date().toISOString().split('T')[0] + 'T' + manualStartTime.value + ':00Z'
    let clockOut: string | undefined
    if (manualEndTime.value) {
      clockOut = new Date().toISOString().split('T')[0] + 'T' + manualEndTime.value + ':00Z'
      // Cross-day: use next day for clockOut
      if (validation.crossDay) {
        const nextDay = addDays(getTodayDateString(), 1)
        clockOut = nextDay + 'T' + manualEndTime.value + ':00Z'
      }
    }

    const result = await attendanceApi.manualRecord({
      clockIn,
      clockOut,
      remark: manualRemark.value || undefined,
    })

    uni.showToast({ title: '手动录入成功', icon: 'success' })

    // 如果返回了完整结果且有 clockOut
    if (result && 'clockOut' in result && result.clockOut) {
      lastClockOutResult.value = result as ClockOutResult
      showWorktimeZone.value = settingsStore.showWorktimeAfterClockout
    }

    // 重置表单
    manualStartTime.value = ''
    manualEndTime.value = ''
    manualRemark.value = ''
    crossDayWarning.value = false
    await fetchTodayData()
  } catch (e: any) {
    uni.showToast({ title: e.message || '录入失败', icon: 'none' })
  }
}

// ========== Timer ==========
function startTimer() {
  if (timerInterval) return
  timerInterval = setInterval(() => {
    elapsedSeconds.value++
  }, 1000)
}

function startTimerIfNeeded() {
  if (clockStatus.value === 'WORKING') {
    startTimer()
  }
}

function stopTimer() {
  if (timerInterval) {
    clearInterval(timerInterval)
    timerInterval = null
  }
}

// ========== Mode ==========
function switchInputMode(mode: 'BUTTON' | 'MANUAL') {
  inputMode.value = mode
}

// ========== Calendar Navigation ==========
function calendarPrev() {
  if (calendarView.value === 'MONTH') {
    const prev = subMonths(monthYear.value, monthMonth.value, 1)
    monthYear.value = prev.year
    monthMonth.value = prev.month
  } else if (calendarView.value === 'WEEK') {
    todayDate.value = subDays(weekStart.value, 7)
  }
  fetchCalendarData()
}

function calendarNext() {
  if (calendarView.value === 'MONTH') {
    const next = addMonths(monthYear.value, monthMonth.value, 1)
    monthYear.value = next.year
    monthMonth.value = next.month
  } else if (calendarView.value === 'WEEK') {
    todayDate.value = addDays(weekStart.value, 14)
  }
  fetchCalendarData()
}

function onDayClick(date: string) {
  todayDate.value = date
  calendarView.value = 'TODAY'
  settingsStore.setCalendarView('TODAY')
  fetchTodayData()
}

function onViewSelect(view: CalendarViewType) {
  calendarView.value = view
  settingsStore.setCalendarView(view)
  showViewPicker.value = false
  todayDate.value = getTodayDateString()
  fetchCalendarData()
}
</script>

<style lang="scss" scoped>
.home-page {
  min-height: 100vh;
  background: $bg-secondary;
  padding-bottom: 120px;
}

// Zone 2: Calendar
.calendar-zone {
  background: $bg-primary;
  border-bottom: 1px solid $border-color;
}

// Zone 3: Clock-in
.clockin-zone {
  margin: 12px $page-padding;
}

.mode-switch-row {
  display: flex;
  background: $bg-secondary;
  border-radius: $border-radius-sm;
  padding: 3px;
  margin-bottom: 20px;
}

.mode-tab {
  flex: 1;
  text-align: center;
  padding: 8px;
  font-size: $font-size-sm;
  color: $text-secondary;
  border-radius: $border-radius-sm - 1;
  transition: all 0.2s;
}

.mode-tab.active {
  background: $bg-primary;
  color: $primary-color;
  font-weight: 600;
  box-shadow: $shadow-sm;
}

// Button mode
.button-mode {
  text-align: center;
}

.clock-btn {
  width: 160px;
  height: 160px;
  border-radius: 50%;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  margin: 0 auto;
  transition: all 0.3s;
  cursor: pointer;
}

.clock-btn.btn-idle {
  background: linear-gradient(135deg, $primary-color, #5856D6);
  box-shadow: 0 4px 20px rgba(0, 122, 255, 0.3);
}

.clock-btn.btn-working {
  background: linear-gradient(135deg, $danger-color, #FF6B6B);
  box-shadow: 0 4px 20px rgba(255, 59, 48, 0.3);
  animation: pulse 2s infinite;
}

.clock-btn-icon {
  font-size: 36px;
  color: #FFFFFF;
}

.clock-btn-text {
  font-size: $font-size-lg;
  color: #FFFFFF;
  font-weight: 600;
  margin-top: 8px;
}

.elapsed-timer {
  display: flex;
  align-items: center;
  justify-content: center;
  margin-top: 16px;
}

.timer-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: $success-color;
  margin-right: 8px;
  animation: pulse 1.5s infinite;
}

.timer-text {
  font-size: $font-size-xl;
  font-weight: 700;
  color: $text-primary;
  font-variant-numeric: tabular-nums;
}

// Manual mode
.manual-mode {
  padding: 4px 0;
}

.manual-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 0;
  border-bottom: 1px solid $divider-color;
}

.manual-label {
  font-size: $font-size-md;
  color: $text-primary;
  font-weight: 500;
}

.manual-picker {
  font-size: $font-size-md;
  color: $primary-color;
  padding: 6px 12px;
  background: $primary-light;
  border-radius: $border-radius-sm;
}

.cross-day-warning {
  background: #FFF8E1;
  color: $warning-color;
  font-size: $font-size-sm;
  padding: 10px 12px;
  border-radius: $border-radius-sm;
  margin-top: 12px;
}

.manual-remark {
  margin-top: 12px;
}

.remark-input {
  height: 40px;
  background: $bg-secondary;
  border-radius: $border-radius-sm;
  padding: 0 12px;
  font-size: $font-size-sm;
  border: 1px solid $border-color;
}

.btn-submit {
  width: 100%;
  height: 44px;
  background: $primary-color;
  color: #FFFFFF;
  font-size: $font-size-md;
  font-weight: 600;
  border-radius: $border-radius-sm;
  border: none;
  margin-top: 16px;
}

// Zone 4: Worktime
.worktime-zone {
  margin: 0 $page-padding;
}

@keyframes pulse {
  0%, 100% { transform: scale(1); }
  50% { transform: scale(1.05); }
}
</style>
