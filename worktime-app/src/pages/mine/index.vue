<template>
  <view class="mine-page">
    <!-- 个人信息卡 -->
    <view class="profile-card" @tap="goProfile">
      <view class="avatar">
        <text class="avatar-text">{{ avatarText }}</text>
      </view>
      <view class="profile-info">
        <text class="profile-name">{{ displayName }}</text>
        <text class="profile-phone">{{ phone }}</text>
      </view>
      <text class="profile-arrow">›</text>
    </view>

    <!-- 汇总维度 Tab -->
    <view class="summary-tabs card">
      <scroll-view scroll-x class="tabs-scroll">
        <view
          v-for="tab in tabs"
          :key="tab.value"
          class="tab-item"
          :class="{ active: activeTab === tab.value }"
          @tap="switchTab(tab.value)"
        >
          <text>{{ tab.label }}</text>
        </view>
      </scroll-view>
    </view>

    <!-- 汇总内容区 -->
    <view class="summary-content">
      <!-- 日 -->
      <view v-if="activeTab === 'DAY'" class="summary-section">
        <view class="section-header">
          <text class="section-title">日汇总</text>
          <picker mode="date" :value="dayDate" @change="onDayChange">
            <text class="section-date">{{ dayDate }}</text>
          </picker>
        </view>
        <view v-if="dayLoading" class="loading-text">加载中...</view>
        <DaySummaryComp v-else-if="dayData" :data="dayData" />
        <EmptyState v-else text="暂无数据" />
      </view>

      <!-- 周 -->
      <view v-if="activeTab === 'WEEK'" class="summary-section">
        <view class="section-header">
          <text class="nav-btn" @tap="weekPrev">‹</text>
          <text class="section-title">{{ weekData?.weekStart }} ~ {{ weekData?.weekEnd }}</text>
          <text class="nav-btn" @tap="weekNext">›</text>
        </view>
        <view v-if="weekLoading" class="loading-text">加载中...</view>
        <WeekSummaryComp v-else-if="weekData" :data="weekData" />
        <EmptyState v-else text="暂无数据" />
      </view>

      <!-- 月 -->
      <view v-if="activeTab === 'MONTH'" class="summary-section">
        <view class="section-header">
          <text class="nav-btn" @tap="monthPrev">‹</text>
          <text class="section-title">{{ monthYear }}年{{ monthMonth }}月</text>
          <text class="nav-btn" @tap="monthNext">›</text>
        </view>
        <view v-if="monthLoading" class="loading-text">加载中...</view>
        <MonthSummaryComp v-else-if="monthData" :data="monthData" />
        <EmptyState v-else text="暂无数据" />
      </view>

      <!-- 年 -->
      <view v-if="activeTab === 'YEAR'" class="summary-section">
        <view class="section-header">
          <text class="nav-btn" @tap="yearPrev">‹</text>
          <text class="section-title">{{ yearYear }}年</text>
          <text class="nav-btn" @tap="yearNext">›</text>
        </view>
        <view v-if="yearLoading" class="loading-text">加载中...</view>
        <YearSummaryComp v-else-if="yearData" :data="yearData" />
        <EmptyState v-else text="暂无数据" />
      </view>

      <!-- 自定义 -->
      <view v-if="activeTab === 'CUSTOM'" class="summary-section">
        <view class="section-header">
          <text class="section-title">自定义区间</text>
          <text class="link" @tap="goCustomRange">设置区间 ›</text>
        </view>
        <view v-if="customLoading" class="loading-text">加载中...</view>
        <CustomSummaryComp v-else-if="customData" :data="customData" @export="handleExport" />
        <view v-else class="empty-hint" @tap="goCustomRange">
          <text>点击选择日期区间查看汇总</text>
        </view>
      </view>
    </view>

    <!-- 功能入口 -->
    <view class="menu-section card">
      <view class="menu-item" @tap="goSettings">
        <text>⚙️</text>
        <text class="menu-label">设置</text>
        <text class="menu-arrow">›</text>
      </view>
      <view class="menu-item" @tap="goReport">
        <text>📊</text>
        <text class="menu-label">报表导出</text>
        <text class="menu-arrow">›</text>
      </view>
      <view class="menu-item" @tap="goAbout">
        <text>ℹ️</text>
        <text class="menu-label">关于</text>
        <text class="menu-arrow">›</text>
      </view>
      <view class="menu-item logout" @tap="handleLogout">
        <text>🚪</text>
        <text class="menu-label">退出登录</text>
        <text class="menu-arrow">›</text>
      </view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { ref, computed, watch } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import { useAuthStore } from '@/stores/auth'
import { summaryApi } from '@/api/summary'
import { getTodayDateString, getWeekStart, addDays, subDays, addMonths, subMonths } from '@/utils/date'
import type { SummaryDimension, DaySummary, WeekSummary, MonthSummary, YearSummary, CustomSummary } from '@/types/summary'

// Components
import DaySummaryComp from '@/components/mine/DaySummary.vue'
import WeekSummaryComp from '@/components/mine/WeekSummary.vue'
import MonthSummaryComp from '@/components/mine/MonthSummary.vue'
import YearSummaryComp from '@/components/mine/YearSummary.vue'
import CustomSummaryComp from '@/components/mine/CustomSummary.vue'
import EmptyState from '@/components/common/EmptyState.vue'

const authStore = useAuthStore()

const displayName = computed(() => authStore.displayName)
const phone = computed(() => authStore.userInfo?.phone || '')
const avatarText = computed(() => (authStore.userInfo?.nickname || authStore.userInfo?.username || '?')[0])

const activeTab = ref<SummaryDimension>('DAY')
const tabs = [
  { label: '日', value: 'DAY' as SummaryDimension },
  { label: '周', value: 'WEEK' as SummaryDimension },
  { label: '月', value: 'MONTH' as SummaryDimension },
  { label: '年', value: 'YEAR' as SummaryDimension },
  { label: '自定义', value: 'CUSTOM' as SummaryDimension },
]

// Day state
const dayDate = ref(getTodayDateString())
const dayData = ref<DaySummary | null>(null)
const dayLoading = ref(false)

// Week state
const weekAnchor = ref(getTodayDateString())
const weekData = ref<WeekSummary | null>(null)
const weekLoading = ref(false)

// Month state
const monthYear = ref(new Date().getFullYear())
const monthMonth = ref(new Date().getMonth() + 1)
const monthData = ref<MonthSummary | null>(null)
const monthLoading = ref(false)

// Year state
const yearYear = ref(new Date().getFullYear())
const yearData = ref<YearSummary | null>(null)
const yearLoading = ref(false)

// Custom state
const customStart = ref('')
const customEnd = ref('')
const customData = ref<CustomSummary | null>(null)
const customLoading = ref(false)

// ========== Actions ==========
async function switchTab(dim: SummaryDimension) {
  activeTab.value = dim
  if (dim === 'DAY') await fetchDay()
  else if (dim === 'WEEK') await fetchWeek()
  else if (dim === 'MONTH') await fetchMonth()
  else if (dim === 'YEAR') await fetchYear()
}

async function fetchDay() {
  dayLoading.value = true
  try { dayData.value = await summaryApi.getDay(dayDate.value) } catch {}
  dayLoading.value = false
}

async function fetchWeek() {
  weekLoading.value = true
  try { weekData.value = await summaryApi.getWeek(weekAnchor.value) } catch {}
  weekLoading.value = false
}

async function fetchMonth() {
  monthLoading.value = true
  try { monthData.value = await summaryApi.getMonth(monthYear.value, monthMonth.value) } catch {}
  monthLoading.value = false
}

async function fetchYear() {
  yearLoading.value = true
  try { yearData.value = await summaryApi.getYear(yearYear.value) } catch {}
  yearLoading.value = false
}

async function fetchCustom() {
  if (!customStart.value || !customEnd.value) return
  customLoading.value = true
  try { customData.value = await summaryApi.getCustom(customStart.value, customEnd.value) } catch {}
  customLoading.value = false
}

// Navigation
function onDayChange(e: any) { dayDate.value = e.detail.value; fetchDay() }

function weekPrev() { weekAnchor.value = subDays(getWeekStart(weekAnchor.value), 1); fetchWeek() }
function weekNext() { weekAnchor.value = addDays(getWeekStart(weekAnchor.value), 8); fetchWeek() }
function monthPrev() { const r = subMonths(monthYear.value, monthMonth.value, 1); monthYear.value = r.year; monthMonth.value = r.month; fetchMonth() }
function monthNext() { const r = addMonths(monthYear.value, monthMonth.value, 1); monthYear.value = r.year; monthMonth.value = r.month; fetchMonth() }
function yearPrev() { yearYear.value--; fetchYear() }
function yearNext() { yearYear.value++; fetchYear() }

function goCustomRange() { uni.navigateTo({ url: '/pages/mine/custom-range' }) }
function goProfile() { uni.navigateTo({ url: '/pages/profile/index' }) }
function goSettings() { uni.navigateTo({ url: '/pages/settings/index' }) }
function goReport() { uni.navigateTo({ url: '/pages/report/index' }) }
function goAbout() { uni.showToast({ title: 'WorkTime Pro v1.1.0', icon: 'none' }) }

function handleExport() {
  if (customStart.value && customEnd.value) {
    uni.navigateTo({ url: `/pages/report/create?start=${customStart.value}&end=${customEnd.value}` })
  }
}

async function handleLogout() {
  const res = await new Promise<{ confirm: boolean }>((resolve) => {
    uni.showModal({ title: '确认退出', content: '退出登录后需要重新登录', success: (r) => resolve(r as any) })
  })
  if (res.confirm) {
    await authStore.logout()
    uni.reLaunch({ url: '/pages/login/index' })
  }
}

// Init
onShow(() => { fetchDay() })
</script>

<style lang="scss" scoped>
.mine-page { min-height: 100vh; background: $bg-secondary; padding-bottom: 60px; }

// Profile
.profile-card {
  display: flex;
  align-items: center;
  padding: 20px $page-padding;
  background: $bg-primary;
  margin-bottom: 8px;
}

.avatar {
  width: 56px; height: 56px; border-radius: 50%;
  background: linear-gradient(135deg, $primary-color, #5856D6);
  display: flex; align-items: center; justify-content: center;
}

.avatar-text { font-size: 24px; color: #FFF; font-weight: 700; }

.profile-info { flex: 1; margin-left: 14px; }
.profile-name { font-size: $font-size-lg; font-weight: 600; display: block; }
.profile-phone { font-size: $font-size-sm; color: $text-tertiary; margin-top: 2px; }

.profile-arrow { font-size: 22px; color: $text-placeholder; }

// Tabs
.summary-tabs { margin: 7px $page-padding; padding: 12px; }
.tabs-scroll { white-space: nowrap; display: flex; }
.tab-item { display: inline-block; padding: 8px 20px; font-size: $font-size-md; color: $text-secondary; border-radius: $border-radius-round; margin-right: 4px; }
.tab-item.active { background: $primary-color; color: #FFF; font-weight: 600; }

// Summary sections
.summary-content { margin: 0 $page-padding; }

.section-header {
  display: flex; align-items: center; justify-content: space-between;
  padding: 16px 0 8px;
}

.section-title { font-size: $font-size-lg; font-weight: 600; }
.section-date { font-size: $font-size-sm; color: $primary-color; }
.nav-btn { font-size: 22px; color: $primary-color; padding: 0 12px; }
.link { font-size: $font-size-sm; color: $primary-color; }
.loading-text { text-align: center; padding: 40px 0; color: $text-tertiary; }

.empty-hint { text-align: center; padding: 60px 20px; color: $primary-color; background: $bg-primary; border-radius: $border-radius-md; }

// Menu
.menu-section { margin-top: 16px; }
.menu-item { display: flex; align-items: center; padding: 14px 0; border-bottom: 1px solid $divider-color; font-size: $font-size-md; gap: 10px; }
.menu-item:last-child { border-bottom: none; }
.menu-label { flex: 1; }
.menu-arrow { color: $text-placeholder; font-size: 18px; }
.menu-item.logout .menu-label { color: $danger-color; }
</style>
