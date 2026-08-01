<template>
  <view class="register-page">
    <view class="register-header safe-area-top">
      <text class="register-title">创建账号</text>
      <text class="register-subtitle">注册后开启智能工时管理</text>
    </view>

    <view class="register-form card">
      <view class="form-item">
        <text class="form-label">用户名</text>
        <input
          v-model="username"
          class="form-input"
          placeholder="4-20位字母、数字或下划线"
          @blur="validateUsernameField"
        />
        <text v-if="usernameError" class="form-error">{{ usernameError }}</text>
      </view>

      <view class="form-item">
        <text class="form-label">手机号</text>
        <input
          v-model="phone"
          class="form-input"
          type="number"
          placeholder="请输入手机号"
          maxlength="11"
          @blur="validatePhoneField"
        />
        <text v-if="phoneError" class="form-error">{{ phoneError }}</text>
      </view>

      <view class="form-item">
        <text class="form-label">密码</text>
        <input
          v-model="password"
          class="form-input"
          type="password"
          placeholder="8-20位，包含大小写字母和数字"
          @blur="validatePasswordField"
        />
        <text v-if="passwordError" class="form-error">{{ passwordError }}</text>
      </view>

      <view class="form-item">
        <text class="form-label">确认密码</text>
        <input
          v-model="confirmPassword"
          class="form-input"
          type="password"
          placeholder="再次输入密码"
        />
        <text v-if="confirmError" class="form-error">{{ confirmError }}</text>
      </view>

      <button
        class="btn-register"
        :class="{ 'btn-disabled': loading }"
        :disabled="loading"
        @tap="handleRegister"
      >
        {{ loading ? '注册中...' : '注册' }}
      </button>

      <view class="register-footer">
        <text class="link-text" @tap="goLogin">已有账号？立即登录</text>
      </view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { useAuthStore } from '@/stores/auth'
import { validateUsername, validatePhone, validatePassword } from '@/utils/validator'

const authStore = useAuthStore()

const username = ref('')
const phone = ref('')
const password = ref('')
const confirmPassword = ref('')
const usernameError = ref('')
const phoneError = ref('')
const passwordError = ref('')
const confirmError = ref('')
const loading = ref(false)

function validateUsernameField() {
  const r = validateUsername(username.value)
  usernameError.value = r.valid ? '' : r.message
}
function validatePhoneField() {
  const r = validatePhone(phone.value)
  phoneError.value = r.valid ? '' : r.message
}
function validatePasswordField() {
  const r = validatePassword(password.value)
  passwordError.value = r.valid ? '' : r.message
}

function validateForm(): boolean {
  validateUsernameField()
  validatePhoneField()
  validatePasswordField()
  if (password.value !== confirmPassword.value) {
    confirmError.value = '两次输入的密码不一致'
  } else {
    confirmError.value = ''
  }
  return !usernameError.value && !phoneError.value && !passwordError.value && !confirmError.value
}

async function handleRegister() {
  if (!validateForm()) return
  loading.value = true

  try {
    await authStore.register(username.value, password.value, phone.value)
    uni.showToast({ title: '注册成功', icon: 'success' })
    setTimeout(() => {
      uni.switchTab({ url: '/pages/home/index' })
    }, 500)
  } catch (e: any) {
    uni.showToast({ title: e.message || '注册失败', icon: 'none' })
  } finally {
    loading.value = false
  }
}

function goLogin() {
  uni.navigateBack()
}
</script>

<style lang="scss" scoped>
.register-page {
  min-height: 100vh;
  background: $bg-secondary;
  padding: 0 $page-padding;
}

.register-header {
  text-align: center;
  padding-top: 60px;
  padding-bottom: 30px;
}

.register-title {
  font-size: $font-size-xxl;
  font-weight: 700;
  color: $text-primary;
  display: block;
  margin-bottom: 8px;
}

.register-subtitle {
  font-size: $font-size-sm;
  color: $text-secondary;
}

.register-form {
  margin: 0;
}

.form-item { margin-bottom: 18px; }
.form-label { font-size: $font-size-sm; color: $text-secondary; margin-bottom: 6px; display: block; }
.form-input {
  height: 46px; background: $bg-secondary; border-radius: $border-radius-sm;
  padding: 0 12px; font-size: $font-size-md; border: 1px solid $border-color;
}
.form-input:focus { border-color: $primary-color; }
.form-error { font-size: $font-size-xs; color: $danger-color; margin-top: 4px; }

.btn-register {
  width: 100%; height: 48px; background: $primary-color; color: #FFFFFF;
  font-size: $font-size-lg; font-weight: 600; border-radius: $border-radius-sm;
  border: none; margin-top: 12px;
}
.btn-disabled { opacity: 0.6; }
.register-footer { text-align: center; margin-top: 18px; }
.link-text { font-size: $font-size-sm; color: $primary-color; }
</style>
