<template>
  <view class="app-container">
    <router-view />
  </view>
</template>

<script setup lang="ts">
import { onLaunch, onShow, onHide } from '@dcloudio/uni-app'
import { useAuthStore } from '@/stores/auth'
import { useSettingsStore } from '@/stores/settings'

// 开发环境下开启 Mock 时，必须提前注册所有 Mock handler，否则请求会命中 "No handler" 分支
// 注意：此处使用 fire-and-forget 方式注册，不能在 setup 顶层 await，否则 setup 会变成异步函数，
// 而 App 作为根组件没有 <Suspense> 边界，会触发 Vue 警告并导致组件无法正常渲染
if (import.meta.env.VITE_USE_MOCK === 'true') {
  void import('@/mock/index')
}

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
