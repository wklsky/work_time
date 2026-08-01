export type SummaryDimension = 'DAY' | 'WEEK' | 'MONTH' | 'YEAR' | 'CUSTOM'

export interface DaySummary {
  dimension: 'DAY'
  date: string
  workMinutes: number
  overtimeMinutes: number
  attendanceCount: number
  records: DayRecord[]
}

export interface DayRecord {
  id: number
  clockIn: string
  clockOut: string | null
  workMinutes: number
  overtimeMinutes: number
  source: string
  crossDay: number
  remark: string | null
}

export interface WeekSummary {
  dimension: 'WEEK'
  weekStart: string
  weekEnd: string
  workMinutes: number
  overtimeMinutes: number
  attendanceDays: number
  avgDailyWorkMinutes: number
  dailyBreakdown: DailyBreakdown[]
}

export interface MonthSummary {
  dimension: 'MONTH'
  year: number
  month: number
  workMinutes: number
  overtimeMinutes: number
  attendanceDays: number
  avgDailyWorkMinutes: number
  maxOvertimeDay: {
    date: string
    overtimeMinutes: number
  }
  dailyBreakdown: DailyBreakdown[]
  trend: TrendData
}

export interface YearSummary {
  dimension: 'YEAR'
  year: number
  workMinutes: number
  overtimeMinutes: number
  attendanceDays: number
  avgDailyWorkMinutes: number
  maxOvertimeMonth: {
    month: number
    overtimeMinutes: number
  }
  monthlyBreakdown: MonthlyBreakdown[]
  trend: TrendData
}

export interface CustomSummary {
  dimension: 'CUSTOM'
  startDate: string
  endDate: string
  totalDays: number
  workMinutes: number
  overtimeMinutes: number
  attendanceDays: number
  avgDailyWorkMinutes: number
  maxOvertimeDay: {
    date: string
    overtimeMinutes: number
  }
  dailyBreakdown: DailyBreakdown[]
}

export interface DailyBreakdown {
  date: string
  weekday: number
  weekdayName: string
  workMinutes: number
  overtimeMinutes: number
}

export interface MonthlyBreakdown {
  month: number
  workMinutes: number
  overtimeMinutes: number
  attendanceDays: number
}

export interface TrendData {
  standardLine?: number
  values: number[]
}
