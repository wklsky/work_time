/**
 * Mock 内存数据库
 * 模拟真实的 API 数据用于开发环境
 */

import type { UserInfo } from '@/types/auth'
import type { AttendanceRecord } from '@/types/attendance'
import type { UserSetting, WorkRule } from '@/types/settings'
import type { ExportRecord } from '@/types/exports'

// ========== 用户 ==========
export const mockUser: UserInfo = {
  userId: 1,
  username: 'testuser',
  nickname: '测试用户',
  avatar: null,
  phone: '138****8000',
  timezone: 'Asia/Shanghai',
  createTime: '2026-01-15T08:00:00Z',
}

// ========== 今日记录（包含进行中的记录） ==========
export function getTodayDate(): string {
  const now = new Date()
  return now.toISOString().split('T')[0]
}

// ========== 打卡记录生成器 ==========
function generateRecords(): AttendanceRecord[] {
  const today = getTodayDate()
  const records: AttendanceRecord[] = [
    // 昨天 - 正常下班
    {
      id: 95, userId: 1, workDate: '2026-07-31',
      clockIn: '2026-07-31T09:00:00Z', clockOut: '2026-07-31T18:00:00Z',
      workMinutes: 480, overtimeMinutes: 0,
      source: 'BUTTON', crossDay: 0, status: 'COMPLETED',
      remark: null, createTime: '2026-07-31T09:00:00Z',
    },
    // 今天 - 第一段（已完成）
    {
      id: 100, userId: 1, workDate: today,
      clockIn: `${today}T07:30:00Z`, clockOut: `${today}T09:00:00Z`,
      workMinutes: 60, overtimeMinutes: 30,
      source: 'BUTTON', crossDay: 0, status: 'COMPLETED',
      remark: null, createTime: `${today}T07:30:00Z`,
    },
    // 今天 - 第二段（进行中）
    {
      id: 101, userId: 1, workDate: today,
      clockIn: `${today}T09:00:00Z`, clockOut: null,
      workMinutes: 0, overtimeMinutes: 0,
      source: 'BUTTON', crossDay: 0, status: 'PENDING',
      remark: null, createTime: `${today}T09:00:00Z`,
    },
  ]

  // 生成过去 60 天的历史记录
  for (let i = 1; i <= 60; i++) {
    const d = new Date()
    d.setDate(d.getDate() - i)
    const ds = d.toISOString().split('T')[0]
    const dow = d.getDay() // 0=周日

    // 周末可能不工作
    if (dow === 0 || dow === 6) {
      if (Math.random() > 0.7) {
        // 偶尔周末加班
        const startH = 9 + Math.floor(Math.random() * 3)
        const workMin = 120 + Math.floor(Math.random() * 360)
        records.push({
          id: 100 + i, userId: 1, workDate: ds,
          clockIn: `${ds}T0${startH}:00:00Z`,
          clockOut: `${ds}T${String(startH + Math.floor(workMin / 60)).padStart(2, '0')}:${String(Math.floor(workMin % 60)).padStart(2, '0')}:00Z`,
          workMinutes: workMin, overtimeMinutes: workMin,
          source: 'MANUAL', crossDay: 0, status: 'COMPLETED',
          remark: '周末加班', createTime: `${ds}T09:00:00Z`,
        })
      }
      continue
    }

    // 工作日
    const startH = 8.5 + Math.random() * 1
    const startHInt = Math.floor(startH)
    const startMin = Math.round((startH - startHInt) * 60)
    const endH = 17.5 + Math.random() * 3
    const endHInt = Math.floor(endH)
    const endMin = Math.round((endH - endHInt) * 60)
    const restMin = 60
    const standardWork = 480
    const totalWork = Math.round((endH - startH) * 60) - restMin
    const overtime = Math.max(0, totalWork - standardWork - 30)

    records.push({
      id: 100 + i, userId: 1, workDate: ds,
      clockIn: `${ds}T${String(startHInt).padStart(2, '0')}:${String(startMin).padStart(2, '0')}:00Z`,
      clockOut: `${ds}T${String(endHInt).padStart(2, '0')}:${String(endMin).padStart(2, '0')}:00Z`,
      workMinutes: totalWork, overtimeMinutes: overtime,
      source: i % 10 === 0 ? 'MANUAL' : 'BUTTON',
      crossDay: 0, status: 'COMPLETED',
      remark: null, createTime: `${ds}T09:00:00Z`,
    })
  }

  // 添加一个跨天记录
  if (records.length > 10) {
    const crossDay = new Date()
    crossDay.setDate(crossDay.getDate() - 15)
    const ds = crossDay.toISOString().split('T')[0]
    records.push({
      id: 200, userId: 1, workDate: ds,
      clockIn: `${ds}T22:00:00Z`,
      clockOut: `${ds}T23:59:59Z`.replace(ds, (() => {
        const next = new Date(crossDay)
        next.setDate(next.getDate() + 1)
        return next.toISOString().split('T')[0]
      })()) + `${ds}T22:00:00Z`.replace(ds, (() => {
        const next = new Date(crossDay)
        next.setDate(next.getDate() + 1)
        return next.toISOString().split('T')[0]
      })()),
      workMinutes: 180, overtimeMinutes: 180,
      source: 'BUTTON', crossDay: 1, status: 'COMPLETED',
      remark: '通宵上线', createTime: `${ds}T22:00:00Z`,
    })
  }

  return records.sort((a, b) => b.workDate.localeCompare(a.workDate) || a.clockIn.localeCompare(b.clockIn))
}

// 修复跨天记录的 clockOut
function fixCrossDayRecord(records: AttendanceRecord[]): void {
  const crossDayRec = records.find(r => r.id === 200)
  if (crossDayRec && crossDayRec.clockOut && crossDayRec.clockOut.includes(crossDayRec.workDate)) {
    const nextDay = new Date(crossDayRec.workDate)
    nextDay.setDate(nextDay.getDate() + 1)
    const nd = nextDay.toISOString().split('T')[0]
    crossDayRec.clockOut = `${nd}T02:00:00Z`
  }
}

export const mockAttendanceRecords = generateRecords()
fixCrossDayRecord(mockAttendanceRecords)

// ========== 设置 ==========
export const mockSettings: UserSetting = {
  showWorktimeAfterClockout: true,
  timeFormat: '24H',
  homeCalendarView: 'TODAY',
  defaultInputMode: 'BUTTON',
  updateTime: '2026-08-01T10:00:00Z',
}

// ========== 工作规则 ==========
export const mockWorkRule: WorkRule = {
  id: 1,
  ruleName: '默认规则',
  startTime: '09:00',
  endTime: '18:00',
  restStart: '12:00',
  restEnd: '13:00',
  bufferMinutes: 30,
  dayCutTime: '04:00',
  workDays: '1,2,3,4,5',
  isDefault: true,
}

// ========== 导出记录 ==========
export const mockExportRecords: ExportRecord[] = [
  {
    exportId: 1, exportType: 'MONTH', startDate: '2026-07-01', endDate: '2026-07-31',
    fileFormat: 'EXCEL', status: 'SUCCESS',
    fileUrl: 'https://example.com/exports/1.xlsx', fileSize: 45000,
    errorMsg: null, createTime: '2026-08-01T10:00:00Z',
  },
  {
    exportId: 2, exportType: 'CUSTOM', startDate: '2026-01-01', endDate: '2026-06-30',
    fileFormat: 'EXCEL', status: 'SUCCESS',
    fileUrl: 'https://example.com/exports/2.xlsx', fileSize: 120000,
    errorMsg: null, createTime: '2026-07-15T14:00:00Z',
  },
  {
    exportId: 3, exportType: 'WEEK', startDate: null, endDate: null,
    fileFormat: 'PDF', status: 'FAILED',
    fileUrl: null, fileSize: null,
    errorMsg: '生成失败：数据格式异常', createTime: '2026-07-10T09:00:00Z',
  },
]
