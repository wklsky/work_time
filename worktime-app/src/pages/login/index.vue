<template>
  <view class="login-page">
    <view class="login-header safe-area-top">
      <text class="login-logo">⏱️</text>
      <text class="login-title">WorkTime Pro</text>
      <text class="login-subtitle">登录以同步你的工时数据</text>
    </view>

    <view class="login-form card">
      <view class="form-item">
        <text class="form-label">手机号</text>
        <input
          v-model="phone"
          class="form-input"
          type="number"
          placeholder="请输入手机号"
          maxlength="11"
          @blur="validatePhone"
        />
        <text v-if="phoneError" class="form-error">{{ phoneError }}</text>
      </view>

      <view class="form-item">
        <text class="form-label">密码</text>
        <input
          v-model="password"
          class="form-input"
          type="password"
          placeholder="请输入密码"
          @blur="validatePassword"
        />
        <text v-if="passwordError" class="form-error">{{ passwordError }}</text>
      </view>

      <button
        class="btn-login"
        :class="{ 'btn-disabled': loading }"
        :disabled="loading"
        @tap="handleLogin"
      >
        {{ loading ? '登录中...' : '登录' }}
      </button>

      <view class="login-footer">
        <text class="link-text" @tap="goRegister">还没有账号？立即注册</text>
      </view>
    </view>

    <!-- 提示：Mock 模式 -->
    <view class="mock-hint">
      <text class="mock-hint-text">Mock 模式：手机号 13800138000 密码 Abc@123456</text>
    </view>
  </view>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { useAuthStore } from '@/stores/auth'
import { validatePhone, validatePassword } from '@/utils/validator'

const authStore = useAuthStore()

const phone = ref('')
const password = ref('')
const phoneError = ref('')
const passwordError = ref('')
const loading = ref(false)
const loginError = ref('')

function validateForm(): boolean {
  const phoneResult = validatePhone(phone.value)
  const pwdResult = validatePassword(password.value)
  phoneError.value = phoneResult.valid ? '' : phoneResult.message
  passwordError.value = pwdResult.valid ? '' : pwdResult.message
  return phoneResult.valid && pwdResult.valid
}

async function handleLogin() {
  if (!validateForm()) return
  loading.value = true
  loginError.value = ''

  try {
    await authStore.login(phone.value, password.value)
    uni.showToast({ title: '登录成功', icon: 'success' })
    setTimeout(() => {
      uni.switchTab({ url: '/pages/home/index' })
    }, 500)
  } catch (e: any) {
    loginError.value = e.message || '登录失败，请重试'
    uni.showToast({ title: loginError.value, icon: 'none' })
  } finally {
    loading.value = false
  }
}

function goRegister() {
  uni.navigateTo({ url: '/pages/register/index' })
}
</script>

<style lang="scss" scoped>
.login-page {
  min-height: 100vh;
  background: $bg-secondary;
  padding: 0 $page-padding;
}

.login-header {
  text-align: center;
  padding-top: 80px;
  padding-bottom: 40px;
}

.login-logo {
  font-size: 64px;
  display: block;
  margin-bottom: 16px;
}

.login-title {
  font-size: $font-size-xxl;
  font-weight: 700;
  color: $text-primary;
  display: block;
  margin-bottom: 8px;
}

.login-subtitle {
  font-size: $font-size-sm;
  color: $text-secondary;
}

.login-form {
  margin: 0;
}

.form-item {
  margin-bottom: 20px;
}

.form-label {
  font-size: $font-size-sm;
  color: $text-secondary;
  margin-bottom: 8px;
  display: block;
}

.form-input {
  height: 48px;
  background: $bg-secondary;
  border-radius: $border-radius-sm;
  padding: 0 12px;
  font-size: $font-size-md;
  border: 1px solid $border-color;
}

.form-input:focus {
  border-color: $primary-color;
}

.form-error {
  font-size: $font-size-xs;
  color: $danger-color;
  margin-top: 4px;
}

.btn-login {
  width: 100%;
  height: 48px;
  background: $primary-color;
  color: #FFFFFF;
  font-size: $font-size-lg;
  font-weight: 600;
  border-radius: $border-radius-sm;
  border: none;
  margin-top: 8px;
}

.btn-disabled {
  opacity: 0.6;
}

.login-footer {
  text-align: center;
  margin-top: 20px;
}

.link-text {
  font-size: $font-size-sm;
  color: $primary-color;
}

.mock-hint {
  margin-top: 20px;
  text-align: center;
}

.mock-hint-text {
  font-size: $font-size-xs;
  color: $text-tertiary;
}
</style>
