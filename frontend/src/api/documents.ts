import type { DocumentCategory, EmployeeDocument, UploadInput } from '../types/documents'
import { api } from './axiosInstance'

export async function listCategories(includeInactive = false): Promise<DocumentCategory[]> {
  const { data } = await api.get<DocumentCategory[]>('/api/v1/documents/categories', { params: { includeInactive } })
  return data
}

export async function createCategory(input: { name: string; description?: string }): Promise<DocumentCategory> {
  const { data } = await api.post<DocumentCategory>('/api/v1/documents/categories', input)
  return data
}

export async function updateCategory(
  id: number,
  input: { name: string; description?: string; active?: boolean },
): Promise<DocumentCategory> {
  const { data } = await api.put<DocumentCategory>(`/api/v1/documents/categories/${id}`, input)
  return data
}

export async function listDocuments(employeeId: number): Promise<EmployeeDocument[]> {
  const { data } = await api.get<EmployeeDocument[]>(`/api/v1/employees/${employeeId}/documents`)
  return data
}

export async function uploadDocument(employeeId: number, input: UploadInput): Promise<EmployeeDocument> {
  const form = new FormData()
  form.append('file', input.file)
  form.append('categoryId', String(input.categoryId))
  if (input.expiryDate) form.append('expiryDate', input.expiryDate)
  if (input.visibility) form.append('visibility', input.visibility)
  const { data } = await api.post<EmployeeDocument>(`/api/v1/employees/${employeeId}/documents`, form)
  return data
}

export async function deleteDocument(id: number): Promise<void> {
  await api.delete(`/api/v1/documents/${id}`)
}

/** Fetches through axios so the Authorization header is sent, then saves the file under its own name. */
export async function downloadDocument(doc: Pick<EmployeeDocument, 'id' | 'fileName'>): Promise<void> {
  const res = await api.get<Blob>(`/api/v1/documents/${doc.id}/download`, { responseType: 'blob' })
  const url = URL.createObjectURL(res.data)
  const a = document.createElement('a')
  a.href = url
  a.download = doc.fileName
  a.click()
  URL.revokeObjectURL(url)
}

/**
 * Fetches a stored PDF through the access-checked view endpoint (with the user's token) and returns a
 * temporary in-memory URL for the viewer. Nothing is saved; call URL.revokeObjectURL when done.
 */
export async function fetchPdfObjectUrl(id: number): Promise<string> {
  const res = await api.get<Blob>(`/api/v1/documents/${id}/view`, { responseType: 'blob' })
  return URL.createObjectURL(new Blob([res.data], { type: 'application/pdf' }))
}

export async function listExpiringDocuments(days?: number, includeExpired = false): Promise<EmployeeDocument[]> {
  const { data } = await api.get<EmployeeDocument[]>('/api/v1/documents/expiring', { params: { days, includeExpired } })
  return data
}
