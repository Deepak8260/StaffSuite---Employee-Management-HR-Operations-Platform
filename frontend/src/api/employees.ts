import type { PageResponse } from '../types/auth'
import type {
  BulkUploadResult,
  DirectoryEntry,
  Employee,
  EmployeeFilters,
  EmployeeProfile,
  EmployeeInput,
  SelfProfileInput,
} from '../types/employee'
import axios from 'axios'
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
  let data: Blob
  try {
    ;({ data } = await api.get<Blob>(`${BASE}/bulk-upload/template`, { responseType: 'blob' }))
  } catch (err) {
    // with responseType 'blob' an error body arrives as a Blob too; unpack it so the real reason is shown
    if (axios.isAxiosError(err) && err.response?.data instanceof Blob) {
      try {
        err.response.data = JSON.parse(await err.response.data.text())
      } catch { /* not JSON: keep the generic message */ }
    }
    throw err
  }
  const url = URL.createObjectURL(data)
  const a = document.createElement('a')
  a.href = url
  a.download = 'employee_bulk_upload_template.xlsx'
  document.body.appendChild(a)
  a.click()
  a.remove()
  URL.revokeObjectURL(url)
}

export async function getProfile(id: number): Promise<EmployeeProfile> {
  const { data } = await api.get<EmployeeProfile>(`${BASE}/${id}/profile`)
  return data
}

/** HR/Admin or the employee themself. The backend accepts JPG and PNG up to 5 MB. */
export async function uploadEmployeePhoto(id: number, file: File): Promise<Employee> {
  const form = new FormData()
  form.append('file', file)
  const { data } = await api.post<Employee>(`${BASE}/${id}/photo`, form)
  return data
}

/** The photo needs the login token, so it is fetched and shown from memory (the caller revokes the URL). */
export async function fetchPhotoObjectUrl(id: number): Promise<string> {
  const res = await api.get<Blob>(`${BASE}/${id}/photo`, { responseType: 'blob' })
  return URL.createObjectURL(res.data)
}
