import { registerHandler, mockSuccess } from './index'

registerHandler('/summary/day', () => {
  return mockSuccess({
    dimension: 'DAY',
    date: '2026-08-01',
    workMinutes: 480,
    overtimeMinutes: 30,
    attendanceCount: 1,
    records: [{
      id: 100, clockIn: '2026-08-01T09:00:00Z', clockOut: '2026-08-01T18:30:00Z',
      workMinutes: 480, overtimeMinutes: 30, source: 'BUTTON', crossDay: 0, remark: null,
    }],
  })
})

registerHandler('/summary/week', () => {
  const days = ['周一', '周二', '周三', '周四', '周五', '周六', '周日']
  const breakdown = days.map((name, i) => ({
    date: `2026-07-${27 + i}`,
    weekday: i + 1,
    weekdayName: name,
    workMinutes: i < 5 ? 450 + Math.floor(Math.random() * 120) : (Math.random() > 0.7 ? 180 : 0),
    overtimeMinutes: i < 5 ? Math.max(0, Math.floor(Math.random() * 90)) : (Math.random() > 0.7 ? 180 : 0),
  }))
  return mockSuccess({
    dimension: 'WEEK',
    weekStart: '2026-07-27',
    weekEnd: '2026-08-02',
    workMinutes: breakdown.reduce((s, d) => s + d.workMinutes, 0),
    overtimeMinutes: breakdown.reduce((s, d) => s + d.overtimeMinutes, 0),
    attendanceDays: breakdown.filter(d => d.workMinutes > 0).length,
    avgDailyWorkMinutes: 480,
    dailyBreakdown: breakdown,
  })
})

registerHandler('/summary/month', () => {
  const breakdown = []
  for (let d = 1; d <= 31; d++) {
    breakdown.push({
      date: `2026-08-${String(d).padStart(2, '0')}`,
      weekday: d % 7,
      weekdayName: ['周一', '周二', '周三', '周四', '周五', '周六', '周日'][d % 7],
      workMinutes: d % 7 < 5 ? 420 + Math.floor(Math.random() * 180) : (Math.random() > 0.8 ? 240 : 0),
      overtimeMinutes: Math.max(0, Math.floor(Math.random() * 120) - 30),
    })
  }
  return mockSuccess({
    dimension: 'MONTH',
    year: 2026,
    month: 8,
    workMinutes: breakdown.reduce((s, d) => s + d.workMinutes, 0),
    overtimeMinutes: breakdown.reduce((s, d) => s + d.overtimeMinutes, 0),
    attendanceDays: breakdown.filter(d => d.workMinutes > 0).length,
    avgDailyWorkMinutes: 506,
    maxOvertimeDay: { date: '2026-08-15', overtimeMinutes: 120 },
    dailyBreakdown: breakdown,
    trend: { standardLine: 480, values: breakdown.map(d => d.workMinutes) },
  })
})

registerHandler('/summary/year', () => {
  const months = []
  for (let m = 1; m <= 12; m++) {
    months.push({
      month: m,
      workMinutes: 9000 + Math.floor(Math.random() * 4000),
      overtimeMinutes: 400 + Math.floor(Math.random() * 1200),
      attendanceDays: 18 + Math.floor(Math.random() * 5),
    })
  }
  return mockSuccess({
    dimension: 'YEAR',
    year: 2026,
    workMinutes: months.reduce((s, m) => s + m.workMinutes, 0),
    overtimeMinutes: months.reduce((s, m) => s + m.overtimeMinutes, 0),
    attendanceDays: months.reduce((s, m) => s + m.attendanceDays, 0),
    avgDailyWorkMinutes: 506,
    maxOvertimeMonth: { month: 3, overtimeMinutes: 1200 },
    monthlyBreakdown: months,
    trend: { values: months.map(m => m.workMinutes) },
  })
})

registerHandler('/summary/custom', (config) => {
  const breakdown = []
  const startD = new Date(config.params?.startDate || '2026-01-01')
  const endD = new Date(config.params?.endDate || '2026-01-07')
  for (let d = new Date(startD); d <= endD; d.setDate(d.getDate() + 1)) {
    const ds = d.toISOString().split('T')[0]
    breakdown.push({
      date: ds, weekday: d.getDay() || 7, weekdayName: ['周日', '周一', '周二', '周三', '周四', '周五', '周六'][d.getDay()],
      workMinutes: d.getDay() % 6 !== 0 ? 450 + Math.floor(Math.random() * 120) : 0,
      overtimeMinutes: d.getDay() % 6 !== 0 ? Math.max(0, Math.floor(Math.random() * 90)) : 0,
    })
  }
  return mockSuccess({
    dimension: 'CUSTOM',
    startDate: config.params?.startDate || '2026-01-01',
    endDate: config.params?.endDate || '2026-01-07',
    totalDays: breakdown.length,
    workMinutes: breakdown.reduce((s, d) => s + d.workMinutes, 0),
    overtimeMinutes: breakdown.reduce((s, d) => s + d.overtimeMinutes, 0),
    attendanceDays: breakdown.filter(d => d.workMinutes > 0).length,
    avgDailyWorkMinutes: Math.round(breakdown.reduce((s, d) => s + d.workMinutes, 0) / Math.max(1, breakdown.filter(d => d.workMinutes > 0).length)),
    maxOvertimeDay: { date: breakdown[0]?.date || '', overtimeMinutes: 90 },
    dailyBreakdown: breakdown,
  })
})

registerHandler('/summary/quick', () => {
  const breakdown = []
  for (let i = 6; i >= 0; i--) {
    const d = new Date()
    d.setDate(d.getDate() - i)
    const ds = d.toISOString().split('T')[0]
    breakdown.push({
      date: ds, weekday: d.getDay() || 7, weekdayName: ['周日', '周一', '周二', '周三', '周四', '周五', '周六'][d.getDay()],
      workMinutes: d.getDay() % 6 !== 0 ? 450 + Math.floor(Math.random() * 120) : 0,
      overtimeMinutes: d.getDay() % 6 !== 0 ? Math.max(0, Math.floor(Math.random() * 90)) : 0,
    })
  }
  return mockSuccess({
    dimension: 'CUSTOM',
    startDate: breakdown[0].date,
    endDate: breakdown[6].date,
    totalDays: 7,
    workMinutes: breakdown.reduce((s, d) => s + d.workMinutes, 0),
    overtimeMinutes: breakdown.reduce((s, d) => s + d.overtimeMinutes, 0),
    attendanceDays: breakdown.filter(d => d.workMinutes > 0).length,
    avgDailyWorkMinutes: 480,
    maxOvertimeDay: { date: breakdown[0].date, overtimeMinutes: 60 },
    dailyBreakdown: breakdown,
  })
})
