<template>
  <view class="empty-state">
    <text class="empty-icon">📭</text>
    <text class="empty-text">{{ text }}</text>
  </view>
</template>

<script setup lang="ts">
defineProps<{ text: string }>()
</script>

<style lang="scss" scoped>
.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 60px 20px;
}
.empty-icon { font-size: 48px; margin-bottom: 12px; }
.empty-text { font-size: $font-size-sm; color: $text-tertiary; }
</style>
