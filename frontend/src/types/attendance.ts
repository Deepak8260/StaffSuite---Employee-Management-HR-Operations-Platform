export type AttendanceStatus = 'PRESENT' | 'ABSENT' | 'HALF_DAY' | 'LATE' | 'ON_LEAVE'
export type DayType = 'WORKING' | 'WEEKEND' | 'HOLIDAY' | 'BEFORE_JOINING'
export type CorrectionStatus = 'PENDING' | 'APPROVED' | 'REJECTED'
export const CORRECTION_STATUSES: CorrectionStatus[] = ['PENDING', 'APPROVED', 'REJECTED']

export interface AttendanceDay {
  date: string
  dayType: DayType
  holidayName: string | null
  status: AttendanceStatus | null
  recordId: number | null
  checkInTime: string | null
  checkOutTime: string | null
  workingHours: number | null
  leaveTypeName: string | null
  pendingCorrection: boolean
}

export interface SummaryRow {
  id: number | null
  label: string
  employeeCode: string | null
  employeeCount: number
  present: number
  late: number
  halfDay: number
  absent: number
  onLeave: number
  totalHours: number
}

export interface AttendanceSummary {
  from: string
  to: string
  groupBy: 'DEPARTMENT' | 'EMPLOYEE'
  rows: SummaryRow[]
  totals: SummaryRow
}

export interface Correction {
  id: number
  attendanceRecordId: number
  attendanceDate: string
  employeeId: number
  employeeCode: string
  employeeName: string
  currentCheckIn: string | null
  currentCheckOut: string | null
  requestedCheckIn: string | null
  requestedCheckOut: string | null
  reason: string
  status: CorrectionStatus
  reviewedByName: string | null
  reviewComment: string | null
  reviewedAt: string | null
  createdAt: string
}

export interface CorrectionInput {
  attendanceRecordId?: number
  attendanceDate?: string
  requestedCheckIn?: string
  requestedCheckOut?: string
  reason: string
}
