import type { PageResponse } from '../types/auth'
import type {
  DirectoryEntry,
  Employee,
  EmployeeFilters,
  EmployeeInput,
  SelfProfileInput,
} from '../types/employee'
import { api } from './axiosInstance'

const BASE = '/api/v1/employees'

export async function listEmployees(
  filters: EmployeeFilters,
  page: number,
  size: number,
): Promise<PageResponse<Employee>> {
  const { data } = await api.get<PageResponse<Employee>>(BASE, { params: { ...filters, page, size } })
  return data
}

export async function listDirectory(q: string, page: number, size: number): Promise<PageResponse<DirectoryEntry>> {
  const { data } = await api.get<PageResponse<DirectoryEntry>>(`${BASE}/directory`, {
    params: { q: q || undefined, page, size },
  })
  return data
}

export async function getEmployee(id: number): Promise<Employee> {
  const { data } = await api.get<Employee>(`${BASE}/${id}`)
  return data
}

export async function getTeam(id: number, page: number, size: number): Promise<PageResponse<Employee>> {
  const { data } = await api.get<PageResponse<Employee>>(`${BASE}/${id}/team`, { params: { page, size } })
  return data
}

export async function createEmployee(input: EmployeeInput): Promise<Employee> {
  const { data } = await api.post<Employee>(BASE, input)
  return data
}

export async function updateEmployee(id: number, input: EmployeeInput): Promise<Employee> {
  const { data } = await api.put<Employee>(`${BASE}/${id}`, input)
  return data
}

export async function updateOwnProfile(id: number, input: SelfProfileInput): Promise<Employee> {
  const { data } = await api.put<Employee>(`${BASE}/${id}/profile`, input)
  return data
}

export async function offboardEmployee(id: number): Promise<Employee> {
  const { data } = await api.put<Employee>(`${BASE}/${id}/offboard`)
  return data
}
