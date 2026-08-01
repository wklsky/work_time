import { http } from './index'
import type { UserSetting, WorkRule, UpdateWorkRuleRequest } from '@/types/settings'

const BASE = '/setting'

export const settingApi = {
  /** 获取用户设置 */
  getSetting(): Promise<UserSetting> {
    return http.get(BASE)
  },

  /** 更新用户设置 */
  updateSetting(data: Partial<UserSetting>): Promise<UserSetting> {
    return http.put(BASE, data as unknown as Record<string, unknown>)
  },

  /** 获取工作规则 */
  getWorkRule(): Promise<WorkRule> {
    return http.get(`${BASE}/work-rule`)
  },

  /** 更新工作规则 */
  updateWorkRule(data: UpdateWorkRuleRequest): Promise<WorkRule> {
    return http.put(`${BASE}/work-rule`, data as unknown as Record<string, unknown>)
  },
}
