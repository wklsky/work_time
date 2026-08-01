export type TimeFormat = '24H' | '12H'
export type CalendarView = 'MONTH' | 'WEEK' | 'TODAY'
export type InputMode = 'BUTTON' | 'MANUAL'

export interface UserSetting {
  showWorktimeAfterClockout: boolean
  timeFormat: TimeFormat
  homeCalendarView: CalendarView
  defaultInputMode: InputMode
  updateTime?: string
}

export interface WorkRule {
  id: number
  ruleName: string
  startTime: string
  endTime: string
  restStart: string
  restEnd: string
  bufferMinutes: number
  dayCutTime: string
  workDays: string
  isDefault: boolean
}

export interface UpdateWorkRuleRequest {
  startTime?: string
  endTime?: string
  restStart?: string
  restEnd?: string
  bufferMinutes?: number
  dayCutTime?: string
  workDays?: string
}
