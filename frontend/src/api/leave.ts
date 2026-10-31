import type { PageResponse } from '../types/auth'
import type {
  Holiday,
  HolidayInput,
  LeaveApplyInput,
  LeaveBalance,
  LeaveCalendar,
  LeaveRequest,
  LeaveScope,
  LeaveStatus,
  LeaveType,
  LeaveTypeInput,
} from '../types/leave'
import { api } from './axiosInstance'

// ---- leave types
export async function listLeaveTypes(includeInactive = false): Promise<LeaveType[]> {
  const { data } = await api.get<LeaveType[]>('/api/v1/leave-types', { params: { includeInactive } })
  return data
}

export async function createLeaveType(input: LeaveTypeInput): Promise<LeaveType> {
  const { data } = await api.post<LeaveType>('/api/v1/leave-types', input)
  return data
}

export async function updateLeaveType(id: number, input: LeaveTypeInput): Promise<LeaveType> {
  const { data } = await api.put<LeaveType>(`/api/v1/leave-types/${id}`, input)
  return data
}

// ---- public holidays
export async function listHolidays(year: number): Promise<Holiday[]> {
  const { data } = await api.get<Holiday[]>('/api/v1/public-holidays', { params: { year } })
  return data
}

export async function createHoliday(input: HolidayInput): Promise<Holiday> {
  const { data } = await api.post<Holiday>('/api/v1/public-holidays', input)
  return data
}

export async function updateHoliday(id: number, input: HolidayInput): Promise<Holiday> {
  const { data } = await api.put<Holiday>(`/api/v1/public-holidays/${id}`, input)
  return data
}

export async function deleteHoliday(id: number): Promise<void> {
  await api.delete(`/api/v1/public-holidays/${id}`)
}

// ---- balances
export async function getBalances(employeeId: number, year?: number): Promise<LeaveBalance[]> {
  const { data } = await api.get<LeaveBalance[]>(`/api/v1/leave-balances/${employeeId}`, { params: { year } })
  return data
}

// ---- requests
export interface LeaveListParams {
  scope?: LeaveScope
  status?: LeaveStatus
  /** Only with scope ALL (HR/Admin): one employee's requests. */
  employeeId?: number
  page: number
  size: number
}

export async function listLeaveRequests(params: LeaveListParams): Promise<PageResponse<LeaveRequest>> {
  const { data } = await api.get<PageResponse<LeaveRequest>>('/api/v1/leave-requests', { params })
  return data
}

export async function applyForLeave(input: LeaveApplyInput): Promise<LeaveRequest> {
  const { data } = await api.post<LeaveRequest>('/api/v1/leave-requests', input)
  return data
}

export async function uploadAttachment(file: File): Promise<string> {
  const form = new FormData()
  form.append('file', file)
  const { data } = await api.post<{ attachmentUrl: string }>('/api/v1/leave-requests/attachments', form)
  return data.attachmentUrl
}

export async function approveLeave(id: number, comment: string): Promise<LeaveRequest> {
  const { data } = await api.put<LeaveRequest>(`/api/v1/leave-requests/${id}/approve`, { comment })
  return data
}

export async function rejectLeave(id: number, comment: string): Promise<LeaveRequest> {
  const { data } = await api.put<LeaveRequest>(`/api/v1/leave-requests/${id}/reject`, { comment })
  return data
}

export async function cancelLeave(id: number): Promise<LeaveRequest> {
  const { data } = await api.put<LeaveRequest>(`/api/v1/leave-requests/${id}/cancel`)
  return data
}

/** Downloads through axios so the Authorization header is sent, then opens the file in a new tab. */
export async function openAttachment(id: number): Promise<void> {
  const res = await api.get<Blob>(`/api/v1/leave-requests/${id}/attachment`, { responseType: 'blob' })
  const url = URL.createObjectURL(res.data)
  window.open(url, '_blank', 'noopener')
  setTimeout(() => URL.revokeObjectURL(url), 60_000)
}

export async function getLeaveCalendar(from: string, to: string, scope?: LeaveScope): Promise<LeaveCalendar> {
  const { data } = await api.get<LeaveCalendar>('/api/v1/leave-calendar', { params: { from, to, scope } })
  return data
}
