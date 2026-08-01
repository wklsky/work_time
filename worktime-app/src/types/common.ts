/** 应用常量 */
export enum ClockStatus {
  NOT_STARTED = 'NOT_STARTED',
  WORKING = 'WORKING',
  COMPLETED = 'COMPLETED',
}

export enum OvertimeLevel {
  NONE = 'NONE',
  LOW = 'LOW',
  MEDIUM = 'MEDIUM',
  HIGH = 'HIGH',
}

export enum CalendarView {
  MONTH = 'MONTH',
  WEEK = 'WEEK',
  TODAY = 'TODAY',
}

export enum TimeFormat {
  H24 = '24H',
  H12 = '12H',
}

export enum InputMode {
  BUTTON = 'BUTTON',
  MANUAL = 'MANUAL',
}

export enum SummaryDimension {
  DAY = 'DAY',
  WEEK = 'WEEK',
  MONTH = 'MONTH',
  YEAR = 'YEAR',
  CUSTOM = 'CUSTOM',
}

export enum ExportType {
  DAY = 'DAY',
  WEEK = 'WEEK',
  MONTH = 'MONTH',
  YEAR = 'YEAR',
  CUSTOM = 'CUSTOM',
}

export enum FileFormat {
  EXCEL = 'EXCEL',
  CSV = 'CSV',
  PDF = 'PDF',
}

export enum ExportStatus {
  PROCESSING = 'PROCESSING',
  SUCCESS = 'SUCCESS',
  FAILED = 'FAILED',
}

/** 加班色阶阈值 */
export const OVERTIME_THRESHOLDS = {
  NONE: 0,
  LOW_MAX: 60,
  MEDIUM_MAX: 180,
} as const

/** 加班色阶颜色 */
export const OVERTIME_COLORS: Record<string, string> = {
  NONE: '#transparent',
  LOW: '#FFF3E0',
  MEDIUM: '#FFB74D',
  HIGH: '#E65100',
}

/** API 超时 */
export const API_TIMEOUT = 15000

/** 导出轮询 */
export const EXPORT_POLL_INTERVAL = 3000
export const EXPORT_POLL_MAX = 60

/** 缓存 TTL (毫秒) */
export const CACHE_TTL = {
  TODAY_DATA: 60000,
  CALENDAR: 120000,
  SETTINGS: 3600000,
  SUMMARY: 300000,
  YEAR_SUMMARY: 600000,
} as const

/** 默认设置 */
export const DEFAULT_SETTINGS = {
  showWorktimeAfterClockout: true,
  timeFormat: TimeFormat.H24 as string,
  homeCalendarView: CalendarView.TODAY as string,
  defaultInputMode: InputMode.BUTTON as string,
}

/** 默认工作规则 */
export const DEFAULT_WORK_RULE = {
  startTime: '09:00',
  endTime: '18:00',
  restStart: '12:00',
  restEnd: '13:00',
  bufferMinutes: 30,
  dayCutTime: '04:00',
  workDays: '1,2,3,4,5',
}

/** 快捷区间预设 */
export const QUICK_DATE_PRESETS = [
  { label: '近7天', value: 'LAST_7_DAYS' },
  { label: '近30天', value: 'LAST_30_DAYS' },
  { label: '近90天', value: 'LAST_90_DAYS' },
  { label: '本季度', value: 'THIS_QUARTER' },
] as const

/** 自定义区间最大天数 */
export const MAX_CUSTOM_RANGE_DAYS = 366

/** 单段工作时长上限（分钟） */
export const MAX_SINGLE_WORK_MINUTES = 24 * 60
