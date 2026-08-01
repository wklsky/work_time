<template>
  <view class="work-rule-page">
    <view class="form-card card">
      <view class="form-row">
        <text class="form-label">标准上班时间</text>
        <picker mode="time" :value="form.startTime" @change="onFieldChange('startTime', $event)">
          <text class="form-value">{{ form.startTime }}</text>
        </picker>
      </view>
      <view class="form-row">
        <text class="form-label">标准下班时间</text>
        <picker mode="time" :value="form.endTime" @change="onFieldChange('endTime', $event)">
          <text class="form-value">{{ form.endTime }}</text>
        </picker>
      </view>
      <view class="form-row">
        <text class="form-label">午休开始</text>
        <picker mode="time" :value="form.restStart" @change="onFieldChange('restStart', $event)">
          <text class="form-value">{{ form.restStart }}</text>
        </picker>
      </view>
      <view class="form-row">
        <text class="form-label">午休结束</text>
        <picker mode="time" :value="form.restEnd" @change="onFieldChange('restEnd', $event)">
          <text class="form-value">{{ form.restEnd }}</text>
        </picker>
      </view>
    </view>

    <view class="form-card card">
      <view class="form-row">
        <text class="form-label">加班缓冲时间</text>
        <view class="form-input-row">
          <input v-model.number="form.bufferMinutes" type="number" class="num-input" />
          <text class="unit">分钟</text>
        </view>
      </view>
      <view class="form-row">
        <text class="form-label">日切时间</text>
        <picker mode="time" :value="form.dayCutTime" @change="onFieldChange('dayCutTime', $event)">
          <text class="form-value">{{ form.dayCutTime }}</text>
        </picker>
      </view>
      <text class="form-hint">日切时间前的记录归属前一日。如设为 04:00，凌晨 3 点打卡计入前一天</text>
    </view>

    <view class="btn-area">
      <button class="btn-save" @tap="handleSave">保存</button>
    </view>

    <view class="notice card">
      <text class="notice-title">⚠️ 重要提示</text>
      <text class="notice-text">工作规则修改仅对新记录生效，不回溯历史数据。如需重算历史记录，请在报表页手动触发重算。</text>
    </view>
  </view>
</template>

<script setup lang="ts">
import { reactive, onMounted } from 'vue'
import { useSettingsStore } from '@/stores/settings'

const settingsStore = useSettingsStore()

const form = reactive({
  startTime: '09:00',
  endTime: '18:00',
  restStart: '12:00',
  restEnd: '13:00',
  bufferMinutes: 30,
  dayCutTime: '04:00',
})

onMounted(() => {
  const r = settingsStore.workRule
  if (r) {
    form.startTime = r.startTime
    form.endTime = r.endTime
    form.restStart = r.restStart
    form.restEnd = r.restEnd
    form.bufferMinutes = r.bufferMinutes
    form.dayCutTime = r.dayCutTime
  }
})

function onFieldChange(field: string, e: any) {
  ;(form as any)[field] = e.detail.value
}

async function handleSave() {
  try {
    await settingsStore.updateWorkRule({
      startTime: form.startTime,
      endTime: form.endTime,
      restStart: form.restStart,
      restEnd: form.restEnd,
      bufferMinutes: form.bufferMinutes,
      dayCutTime: form.dayCutTime,
    })
    uni.showToast({ title: '保存成功', icon: 'success' })
  } catch (e: any) {
    uni.showToast({ title: e.message || '保存失败', icon: 'none' })
  }
}
</script>

<style lang="scss" scoped>
.work-rule-page { min-height: 100vh; background: $bg-secondary; padding-top: 8px; padding-bottom: 40px; }

.form-card { margin: 12px $page-padding; }
.form-row { display: flex; align-items: center; justify-content: space-between; padding: 12px 0; border-bottom: 1px solid $divider-color; }
.form-row:last-child { border-bottom: none; }
.form-label { font-size: $font-size-md; color: $text-primary; }
.form-value { font-size: $font-size-md; color: $primary-color; font-weight: 500; }
.form-input-row { display: flex; align-items: center; gap: 6px; }
.num-input { width: 60px; height: 36px; text-align: center; background: $bg-secondary; border-radius: $border-radius-sm; border: 1px solid $border-color; font-size: $font-size-md; }
.unit { font-size: $font-size-sm; color: $text-secondary; }
.form-hint { font-size: $font-size-xs; color: $text-tertiary; margin-top: 8px; display: block; line-height: 1.5; }

.btn-area { padding: 16px $page-padding; }
.btn-save { width: 100%; height: 46px; background: $primary-color; color: #FFF; font-size: $font-size-md; font-weight: 600; border-radius: $border-radius-sm; border: none; }

.notice { background: #FFF8E1; }
.notice-title { font-size: $font-size-sm; font-weight: 600; color: $warning-color; display: block; margin-bottom: 6px; }
.notice-text { font-size: $font-size-xs; color: $text-secondary; line-height: 1.5; }
</style>
