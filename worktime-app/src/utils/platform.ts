/**
 * 平台检测工具
 */

/** 是否运行在微信小程序 */
export function isWechatMP(): boolean {
  // #ifdef MP-WEIXIN
  return true
  // #endif
  return false
}

/** 是否运行在 App (Android/iOS) */
export function isApp(): boolean {
  // #ifdef APP-PLUS
  return true
  // #endif
  return false
}

/** 是否运行在 H5 */
export function isH5(): boolean {
  // #ifdef H5
  return true
  // #endif
  return false
}

/** 获取平台名称 */
export function getPlatformName(): string {
  // #ifdef APP-PLUS
  return 'app'
  // #endif
  // #ifdef MP-WEIXIN
  return 'mp-weixin'
  // #endif
  // #ifdef H5
  return 'h5'
  // #endif
  return 'unknown'
}
