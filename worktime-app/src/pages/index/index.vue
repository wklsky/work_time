<template>
  <view class="splash-container">
    <view class="splash-content">
      <text class="splash-title">WorkTime Pro</text>
      <text class="splash-subtitle">智能工时管理</text>
    </view>
  </view>
</template>

<script setup lang="ts">
import { onMounted } from 'vue'
import { useAuthStore } from '@/stores/auth'

const authStore = useAuthStore()

onMounted(() => {
  // 检查登录状态并跳转
  setTimeout(() => {
    if (authStore.isLoggedIn) {
      uni.switchTab({ url: '/pages/home/index' })
    } else {
      uni.redirectTo({ url: '/pages/login/index' })
    }
  }, 500)
})
</script>

<style lang="scss" scoped>
.splash-container {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 100vh;
  background: linear-gradient(135deg, $primary-color, #5856D6);
}

.splash-content {
  text-align: center;
}

.splash-title {
  font-size: $font-size-hero;
  font-weight: 700;
  color: #FFFFFF;
  display: block;
  margin-bottom: 8px;
}

.splash-subtitle {
  font-size: $font-size-lg;
  color: rgba(255, 255, 255, 0.8);
}
</style>
