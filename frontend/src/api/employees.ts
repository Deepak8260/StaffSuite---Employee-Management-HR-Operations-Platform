import type { PageResponse } from '../types/auth'
import type {
  BulkUploadResult,
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

export async function bulkUploadEmployees(file: File): Promise<BulkUploadResult> {
  const form = new FormData()
  form.append('file', file)
  const { data } = await api.post<BulkUploadResult>(`${BASE}/bulk-upload`, form)
  return data
}

/** Fetches the blank template (needs the login token, so it cannot be a plain link) and saves it. */
export async function downloadBulkTemplate(): Promise<void> {
  const { data } = await api.get<Blob>(`${BASE}/bulk-upload/template`, { responseType: 'blob' })
  const url = URL.createObjectURL(data)
  const a = document.createElement('a')
  a.href = url
  a.download = 'employee_bulk_upload_template.xlsx'
  document.body.appendChild(a)
  a.click()
  a.remove()
  URL.revokeObjectURL(url)
}
