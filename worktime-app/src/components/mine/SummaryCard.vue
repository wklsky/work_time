<template>
  <view class="summary-card">
    <text class="card-value" :class="colorClass">{{ displayValue }}</text>
    <text class="card-label">{{ label }}</text>
  </view>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { formatDisplayDuration } from '@/utils/time'

const props = defineProps<{
  label: string
  value: number
  unit: string
  color?: string
}>()

const displayValue = computed(() => {
  return formatDisplayDuration(props.value)
})

const colorClass = computed(() => ({
  'c-warning': props.color === 'warning',
  'c-primary': !props.color || props.color === 'primary',
}))
</script>

<style lang="scss" scoped>
.summary-card {
  flex: 1 1 calc(50% - 12px);
  min-width: 100px;
  text-align: center;
  background: $bg-primary;
  border-radius: $border-radius-md;
  padding: 14px 8px;
  box-shadow: $shadow-sm;
}
.card-value {
  font-size: $font-size-lg;
  font-weight: 700;
  display: block;
}
.card-value.c-primary { color: $primary-color; }
.card-value.c-warning { color: $warning-color; }
.card-label {
  font-size: $font-size-xs;
  color: $text-tertiary;
  margin-top: 2px;
  display: block;
}
</style>
