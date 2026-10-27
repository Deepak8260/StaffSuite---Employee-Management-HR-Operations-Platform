import type { PageResponse, Role, UserSummary } from '../types/auth'
import { api } from './axiosInstance'

export async function listUsers(page: number, size: number): Promise<PageResponse<UserSummary>> {
  const { data } = await api.get<PageResponse<UserSummary>>('/api/v1/users', { params: { page, size } })
  return data
}

export async function createUser(input: {
  email: string
  password: string
  role: Role
  employeeId?: number | null
}): Promise<UserSummary> {
  const { data } = await api.post<UserSummary>('/api/v1/users', input)
  return data
}

export async function updateUserRole(id: number, role: Role): Promise<UserSummary> {
  const { data } = await api.put<UserSummary>(`/api/v1/users/${id}/role`, { role })
  return data
}

export async function updateUserStatus(id: number, active: boolean): Promise<UserSummary> {
  const { data } = await api.put<UserSummary>(`/api/v1/users/${id}/status`, { active })
  return data
}
