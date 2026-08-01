import { registerHandler, mockSuccess } from './index'
import { mockSettings, mockWorkRule } from './mock-data'

let settings = { ...mockSettings }
let workRule = { ...mockWorkRule }

registerHandler('/setting', (config) => {
  if (config.method === 'GET') {
    return mockSuccess({ ...settings, updateTime: new Date().toISOString() })
  }
  // PUT
  const data = config.data as any
  if (data) {
    Object.assign(settings, data)
  }
  settings.updateTime = new Date().toISOString()
  return mockSuccess({ ...settings })
})

registerHandler('/setting/work-rule', (config) => {
  if (config.method === 'GET') {
    return mockSuccess({ ...workRule })
  }
  // PUT
  const data = config.data as any
  if (data) {
    Object.assign(workRule, data)
  }
  return mockSuccess({ ...workRule })
})
