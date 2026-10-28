import type { EmploymentStatus } from './employee'

export type Granularity = 'DAILY' | 'WEEKLY' | 'MONTHLY'
export type ReportType = 'headcount' | 'attendance-trend' | 'leave-utilization' | 'onboarding-stats'

export interface CountItem {
  id: number | null
  name: string
  count: number
}

export interface StatusCount {
  status: EmploymentStatus
  count: number
}

export interface HeadcountReport {
  asOf: string
  departmentId: number | null
  total: number
  byDepartment: CountItem[]
  byStatus: StatusCount[]
}

export interface AttendancePoint {
  periodStart: string
  periodEnd: string
  present: number
  late: number
  halfDay: number
  absent: number
  onLeave: number
}

export interface AttendanceTrendReport {
  from: string
  to: string
  departmentId: number | null
  granularity: Granularity
  employees: number
  points: AttendancePoint[]
  totals: AttendancePoint
}

export interface LeaveUsageItem {
  id: number | null
  name: string
  days: number
  requests: number
}

export interface LeaveUtilizationReport {
  from: string
  to: string
  departmentId: number | null
  totalDays: number
  totalRequests: number
  byType: LeaveUsageItem[]
  byDepartment: LeaveUsageItem[]
}

export interface HiresPoint {
  monthStart: string
  hires: number
  stillEmployed: number
}

export interface OnboardingReport {
  from: string
  to: string
  departmentId: number | null
  totalHires: number
  totalStillEmployed: number
  months: HiresPoint[]
}

export interface ReportFilters {
  from: string
  to: string
  departmentId?: number
  granularity?: Granularity
}
