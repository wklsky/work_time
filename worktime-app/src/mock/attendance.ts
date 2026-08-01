import { registerHandler, mockSuccess, mockError } from './index'
import { mockAttendanceRecords, getTodayDate } from './mock-data'

let records = [...mockAttendanceRecords]
let nextId = 300

function getTodayRecs() {
  const today = getTodayDate()
  return records.filter(r => r.workDate === today)
}

registerHandler('/index/today', () => {
  const today = getTodayDate()
  const todayRecs = getTodayRecs().sort((a, b) => a.clockIn.localeCompare(b.clockIn))
  const pendingRec = todayRecs.find(r => r.status === 'PENDING')
  const completedRecs = todayRecs.filter(r => r.status === 'COMPLETED')

  let clockStatus = 'NOT_STARTED'
  if (pendingRec) clockStatus = 'WORKING'
  else if (completedRecs.length > 0) clockStatus = 'COMPLETED'

  const totalWork = completedRecs.reduce((s, r) => s + r.workMinutes, 0)
  const totalOvertime = completedRecs.reduce((s, r) => s + r.overtimeMinutes, 0)

  return mockSuccess({
    dateInfo: {
      date: today,
      weekday: '星期六',
      weekdayNum: 6,
      isToday: true,
    },
    clockStatus,
    currentRecord: pendingRec || null,
    todaySummary: {
      totalWorkMinutes: totalWork,
      totalOvertimeMinutes: totalOvertime,
      segmentCount: completedRecs.length + (pendingRec ? 1 : 0),
      firstClockIn: todayRecs.length > 0 ? todayRecs[0].clockIn : null,
      lastClockOut: completedRecs.length > 0 ? completedRecs[completedRecs.length - 1].clockOut : null,
    },
    todayRecords: todayRecs,
    userSetting: {
      showWorktimeAfterClockout: true,
      timeFormat: '24H',
      homeCalendarView: 'TODAY',
      defaultInputMode: 'BUTTON',
    },
  })
})

registerHandler('/attendance/clock-in', () => {
  const today = getTodayDate()
  const pendingRec = records.find(r => r.workDate === today && r.status === 'PENDING')
  if (pendingRec) {
    return mockError(409, '已存在进行中的工作记录，请先结束当前工作')
  }

  const now = new Date()
  const newRec: any = {
    id: nextId++,
    userId: 1,
    workDate: today,
    clockIn: now.toISOString(),
    clockOut: null,
    workMinutes: 0,
    overtimeMinutes: 0,
    source: 'BUTTON',
    crossDay: 0,
    status: 'PENDING',
    remark: null,
    createTime: now.toISOString(),
  }
  records.unshift(newRec)
  return mockSuccess(newRec)
})

registerHandler('/attendance/clock-out', () => {
  const today = getTodayDate()
  const pendingIndex = records.findIndex(r => r.workDate === today && r.status === 'PENDING')
  if (pendingIndex === -1) {
    return mockError(400, '当前没有进行中的工作记录')
  }

  const now = new Date()
  const rec = records[pendingIndex]
  rec.clockOut = now.toISOString()
  rec.status = 'COMPLETED'

  // 模拟工时计算
  const clockInTime = new Date(rec.clockIn).getTime()
  const clockOutTime = new Date(rec.clockOut).getTime()
  const totalMin = Math.round((clockOutTime - clockInTime) / 60000)
  rec.workMinutes = Math.max(0, totalMin - 60) // 扣除午休
  rec.overtimeMinutes = Math.max(0, rec.workMinutes - 480 - 30) // 标准480分 + 缓冲30分

  const completedRecs = records.filter(r => r.workDate === today && r.status === 'COMPLETED')
  const totalWork = completedRecs.reduce((s, r) => s + r.workMinutes, 0)
  const totalOvertime = completedRecs.reduce((s, r) => s + r.overtimeMinutes, 0)

  return mockSuccess({
    ...rec,
    breakdown: {
      regularWorkMinutes: Math.min(rec.workMinutes, 480),
      restMinutes: 60,
      overtimeMinutes: rec.overtimeMinutes,
      formula: `下班 - 上班 - 午休(60分) - 标准工时(480分) - 缓冲(30分)`,
    },
    todayAccumulated: {
      totalWorkMinutes: totalWork,
      totalOvertimeMinutes: totalOvertime,
      segmentCount: completedRecs.length,
    },
  })
})

registerHandler('/attendance/manual', (config) => {
  const data = config.data as any
  const today = getTodayDate()
  const clockIn = data.clockIn || new Date().toISOString()
  const clockOut = data.clockOut || null

  const rec: any = {
    id: nextId++,
    userId: 1,
    workDate: today,
    clockIn,
    clockOut,
    workMinutes: 0,
    overtimeMinutes: 0,
    source: 'MANUAL',
    crossDay: clockOut && clockOut < clockIn ? 1 : 0,
    status: clockOut ? 'COMPLETED' : 'PENDING',
    remark: data.remark || null,
    createTime: new Date().toISOString(),
  }

  if (clockOut) {
    const inTime = new Date(clockIn).getTime()
    const outTime = new Date(clockOut).getTime()
    const totalMin = Math.round((outTime > inTime ? outTime - inTime : outTime - inTime + 24 * 60 * 60000) / 60000)
    rec.workMinutes = Math.max(0, totalMin - 60)
    rec.overtimeMinutes = Math.max(0, rec.workMinutes - 480 - 30)
  }

  records.unshift(rec)
  return mockSuccess(rec)
})

registerHandler('/attendance/calendar', (config) => {
  const view = config.params?.view || 'TODAY'
  const date = config.params?.date || getTodayDate()

  if (view === 'TODAY') {
    const todayRecs = records.filter(r => r.workDate === date)
    return mockSuccess({
      view: 'TODAY',
      date,
      weekday: '星期六',
      timeline: { standardStart: '09:00', standardEnd: '18:00', restStart: '12:00', restEnd: '13:00' },
      records: todayRecs,
      currentStatus: todayRecs.some(r => r.status === 'PENDING') ? 'WORKING' : 'COMPLETED',
      todayAccumulated: { workMinutes: 540, overtimeMinutes: 60, segmentCount: 2 },
    })
  }

  if (view === 'WEEK') {
    const d = new Date(date)
    const day = d.getDay()
    const mondayOffset = day === 0 ? -6 : 1 - day
    const monday = new Date(d)
    monday.setDate(monday.getDate() + mondayOffset)
    const days: any[] = []
    for (let i = 0; i < 7; i++) {
      const cd = new Date(monday)
      cd.setDate(cd.getDate() + i)
      const ds = cd.toISOString().split('T')[0]
      const dayRecs = records.filter(r => r.workDate === ds)
      days.push({
        date: ds,
        weekday: i + 1,
        weekdayName: ['周一', '周二', '周三', '周四', '周五', '周六', '周日'][i],
        hasRecord: dayRecs.length > 0,
        workMinutes: dayRecs.reduce((s, r) => s + r.workMinutes, 0),
        overtimeMinutes: dayRecs.reduce((s, r) => s + r.overtimeMinutes, 0),
        firstClockIn: dayRecs.length > 0 ? dayRecs[0].clockIn : null,
        lastClockOut: dayRecs.length > 0 ? dayRecs[dayRecs.length - 1].clockOut : null,
        isToday: ds === getTodayDate(),
        workPercentage: dayRecs.length > 0 ? Math.min(100, Math.round(dayRecs.reduce((s, r) => s + r.workMinutes, 0) / 480 * 100)) : 0,
        records: dayRecs,
      })
    }
    return mockSuccess({
      view: 'WEEK',
      weekStart: days[0].date,
      weekEnd: days[6].date,
      days,
      weekTotal: {
        workMinutes: days.reduce((s, d) => s + d.workMinutes, 0),
        overtimeMinutes: days.reduce((s, d) => s + d.overtimeMinutes, 0),
        attendanceDays: days.filter(d => d.hasRecord).length,
        avgDailyMinutes: Math.round(days.reduce((s, d) => s + d.workMinutes, 0) / Math.max(1, days.filter(d => d.hasRecord).length)),
      },
    })
  }

  // MONTH view
  const [year, month] = date.split('-').map(Number)
  const daysInMonth = new Date(year, month, 1).getFullYear() === year && new Date(year, month, 1).getMonth() + 1 === month
    ? new Date(year, month, 0).getDate() : 30
  const firstDow = new Date(year, month - 1, 1).getDay()
  const days: any[] = []
  for (let d = 1; d <= daysInMonth; d++) {
    const ds = `${year}-${String(month).padStart(2, '0')}-${String(d).padStart(2, '0')}`
    const dayRecs = records.filter(r => r.workDate === ds)
    const overtime = dayRecs.reduce((s, r) => s + r.overtimeMinutes, 0)
    let level: string | null = null
    if (overtime > 180) level = 'HIGH'
    else if (overtime > 60) level = 'MEDIUM'
    else if (overtime > 0) level = 'LOW'
    days.push({
      date: ds,
      weekday: (firstDow + d - 1) % 7,
      hasRecord: dayRecs.length > 0,
      workMinutes: dayRecs.reduce((s, r) => s + r.workMinutes, 0),
      overtimeMinutes: overtime,
      overtimeLevel: level,
      isToday: ds === getTodayDate(),
      recordCount: dayRecs.length,
    })
  }
  return mockSuccess({
    view: 'MONTH',
    year,
    month,
    dayCount: daysInMonth,
    firstDayOfWeek: firstDow === 0 ? 7 : firstDow,
    days,
  })
})
