export type LeaveStatus = 'PENDING' | 'APPROVED' | 'REJECTED' | 'CANCELLED'
export const LEAVE_STATUSES: LeaveStatus[] = ['PENDING', 'APPROVED', 'REJECTED', 'CANCELLED']

export type LeaveScope = 'MINE' | 'TEAM' | 'ALL'

export interface LeaveType {
  id: number
  name: string
  description: string | null
  annualEntitlementDays: number
  carryForwardAllowed: boolean
  maxCarryForwardDays: number
  active: boolean
}

export interface LeaveTypeInput {
  name: string
  description?: string
  annualEntitlementDays: number
  carryForwardAllowed: boolean
  maxCarryForwardDays: number
  active?: boolean
}

export interface Holiday {
  id: number
  name: string
  holidayDate: string
  description: string | null
  optional: boolean
}

export interface HolidayInput {
  name: string
  holidayDate: string
  description?: string
  optional: boolean
}

export interface LeaveBalance {
  leaveTypeId: number
  leaveTypeName: string
  year: number
  allocatedDays: number
  carriedForwardDays: number
  usedDays: number
  remainingDays: number
  pendingDays: number
  availableDays: number
}

export interface LeaveRequest {
  id: number
  employeeId: number
  employeeCode: string
  employeeName: string
  leaveTypeId: number
  leaveTypeName: string
  startDate: string
  endDate: string
  totalDays: number
  reason: string | null
  status: LeaveStatus
  approverId: number | null
  approverName: string | null
  approverComment: string | null
  hasAttachment: boolean
  decidedAt: string | null
  createdAt: string
}

export interface LeaveApplyInput {
  leaveTypeId: number
  startDate: string
  endDate: string
  reason?: string
  attachmentUrl?: string
}

export interface CalendarLeave {
  requestId: number
  employeeId: number
  employeeName: string
  leaveTypeName: string
  startDate: string
  endDate: string
  totalDays: number
}

export interface LeaveCalendar {
  holidays: Holiday[]
  leaves: CalendarLeave[]
}
