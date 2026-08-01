<template>
  <view class="app-container">
    <router-view />
  </view>
</template>

<script setup lang="ts">
import { onLaunch, onShow, onHide } from '@dcloudio/uni-app'
import { useAuthStore } from '@/stores/auth'
import { useSettingsStore } from '@/stores/settings'

const authStore = useAuthStore()
const settingsStore = useSettingsStore()

onLaunch(async () => {
  console.log('[App] onLaunch')
  // 初始化认证状态（从本地缓存恢复 token）
  await authStore.initAuth()

  // 初始化设置（本地缓存优先，登录后从服务端合并）
  await settingsStore.initSettings()
})

onShow(() => {
  console.log('[App] onShow')
})

onHide(() => {
  console.log('[App] onHide')
})
</script>

<style lang="scss">
.app-container {
  min-height: 100vh;
  background-color: $bg-secondary;
}
</style>
