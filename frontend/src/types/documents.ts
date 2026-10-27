export type DocumentVisibility = 'SELF_AND_HR' | 'HR_ONLY'

export interface DocumentCategory {
  id: number
  name: string
  description: string | null
  active: boolean
}

export interface EmployeeDocument {
  id: number
  employeeId: number
  employeeCode: string
  employeeName: string
  departmentName: string | null
  categoryId: number
  categoryName: string
  fileName: string
  contentType: string | null
  fileSize: number | null
  expiryDate: string | null
  daysRemaining: number | null
  expired: boolean
  visibility: DocumentVisibility
  uploadedByName: string | null
  createdAt: string
}

export interface UploadInput {
  file: File
  categoryId: number
  expiryDate?: string
  visibility?: DocumentVisibility
}
