import type { PageResponse } from '../types/auth'
import type {
  AttendanceDay,
  AttendanceSummary,
  Correction,
  CorrectionInput,
  CorrectionStatus,
} from '../types/attendance'
import type { LeaveScope } from '../types/leave'
import { api } from './axiosInstance'

export async function checkIn(): Promise<AttendanceDay> {
  const { data } = await api.post<AttendanceDay>('/api/v1/attendance/check-in')
  return data
}

export async function checkOut(): Promise<AttendanceDay> {
  const { data } = await api.post<AttendanceDay>('/api/v1/attendance/check-out')
  return data
}

export async function getAttendanceDays(employeeId: number, from: string, to: string): Promise<AttendanceDay[]> {
  const { data } = await api.get<AttendanceDay[]>(`/api/v1/attendance/${employeeId}`, { params: { from, to } })
  return data
}

export async function getAttendanceSummary(
  from: string,
  to: string,
  groupBy: 'DEPARTMENT' | 'EMPLOYEE',
  departmentId?: number,
): Promise<AttendanceSummary> {
  const { data } = await api.get<AttendanceSummary>('/api/v1/attendance/summary', {
    params: { from, to, groupBy, departmentId },
  })
  return data
}

export interface CorrectionListParams {
  scope?: LeaveScope
  status?: CorrectionStatus
  page: number
  size: number
}

export async function listCorrections(params: CorrectionListParams): Promise<PageResponse<Correction>> {
  const { data } = await api.get<PageResponse<Correction>>('/api/v1/attendance-corrections', { params })
  return data
}

export async function submitCorrection(input: CorrectionInput): Promise<Correction> {
  const { data } = await api.post<Correction>('/api/v1/attendance-corrections', input)
  return data
}

export async function approveCorrection(id: number, comment: string): Promise<Correction> {
  const { data } = await api.put<Correction>(`/api/v1/attendance-corrections/${id}/approve`, { comment })
  return data
}

export async function rejectCorrection(id: number, comment: string): Promise<Correction> {
  const { data } = await api.put<Correction>(`/api/v1/attendance-corrections/${id}/reject`, { comment })
  return data
}
