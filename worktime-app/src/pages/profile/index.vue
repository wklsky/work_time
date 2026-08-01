<template>
  <view class="profile-page">
    <!-- 头像 -->
    <view class="profile-section card">
      <view class="form-row" @tap="changeAvatar">
        <text class="form-label">头像</text>
        <view class="avatar">
          <text class="avatar-text">{{ avatarText }}</text>
        </view>
        <text class="form-arrow">›</text>
      </view>
    </view>

    <!-- 基本资料 -->
    <view class="profile-section card">
      <view class="form-row" @tap="editNickname">
        <text class="form-label">昵称</text>
        <text class="form-value">{{ nickname || '未设置' }}</text>
        <text class="form-arrow">›</text>
      </view>
      <view class="form-row">
        <text class="form-label">手机号</text>
        <text class="form-value">{{ phone }}</text>
      </view>
      <view class="form-row" @tap="changeTimezone">
        <text class="form-label">时区</text>
        <text class="form-value">{{ timezone }}</text>
        <text class="form-arrow">›</text>
      </view>
    </view>

    <view class="btn-area">
      <button class="btn-save" @tap="handleSave">保存修改</button>
    </view>
  </view>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import { useAuthStore } from '@/stores/auth'

const authStore = useAuthStore()

const nickname = ref(authStore.userInfo?.nickname || '')
const timezone = ref(authStore.userInfo?.timezone || 'Asia/Shanghai')
const avatarText = computed(() => (nickname.value || authStore.userInfo?.username || '?')[0])
const phone = computed(() => authStore.userInfo?.phone || '')

function changeAvatar() { uni.showToast({ title: '暂不支持修改头像', icon: 'none' }) }
function editNickname() {
  // Simple prompt
  uni.showModal({
    title: '修改昵称',
    editable: true,
    placeholderText: '请输入新昵称',
    success: (res) => {
      if (res.confirm && (res as any).content) {
        nickname.value = (res as any).content
      }
    },
  })
}
function changeTimezone() {
  const zones = ['Asia/Shanghai', 'Asia/Tokyo', 'America/New_York', 'Europe/London', 'Asia/Singapore']
  uni.showActionSheet({
    itemList: zones,
    success: (res) => { timezone.value = zones[res.tapIndex] },
  })
}

async function handleSave() {
  try {
    await authStore.updateProfile({ nickname: nickname.value, timezone: timezone.value })
    uni.showToast({ title: '保存成功', icon: 'success' })
  } catch (e: any) {
    uni.showToast({ title: e.message || '保存失败', icon: 'none' })
  }
}
</script>

<style lang="scss" scoped>
.profile-page { min-height: 100vh; background: $bg-secondary; padding-top: 12px; }

.profile-section { margin: 0 $page-padding 12px; }
.form-row { display: flex; align-items: center; padding: 14px 0; border-bottom: 1px solid $divider-color; }
.form-row:last-child { border-bottom: none; }
.form-label { font-size: $font-size-md; color: $text-primary; }
.form-value { flex: 1; text-align: right; font-size: $font-size-sm; color: $text-tertiary; margin-right: 8px; }
.form-arrow { font-size: 18px; color: $text-placeholder; }

.avatar { width: 48px; height: 48px; border-radius: 50%; background: linear-gradient(135deg, $primary-color, #5856D6); display: flex; align-items: center; justify-content: center; margin: 0 12px 0 auto; }
.avatar-text { font-size: 20px; color: #FFF; font-weight: 700; }

.btn-area { padding: 20px $page-padding; }
.btn-save { width: 100%; height: 46px; background: $primary-color; color: #FFF; font-size: $font-size-md; font-weight: 600; border-radius: $border-radius-sm; border: none; }
</style>
