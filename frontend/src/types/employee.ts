import type { EmployeeDocument } from './documents'
import type { LeaveBalance } from './leave'
import type { Role } from './auth'

export type EmploymentStatus = 'ACTIVE' | 'ON_LEAVE' | 'PROBATION' | 'TERMINATED'

/** Statuses an employee can be created/edited with. TERMINATED only happens via offboarding. */
export const EDITABLE_STATUSES: EmploymentStatus[] = ['ACTIVE', 'ON_LEAVE', 'PROBATION']
export const ALL_STATUSES: EmploymentStatus[] = ['ACTIVE', 'ON_LEAVE', 'PROBATION', 'TERMINATED']

export interface Employee {
  id: number
  employeeCode: string
  fullName: string
  email: string
  phone: string | null
  address: string | null
  emergencyContact: string | null
  dateOfBirth: string | null
  joiningDate: string
  jobTitle: string
  employmentStatus: EmploymentStatus
  departmentId: number | null
  departmentName: string | null
  designationId: number | null
  designationName: string | null
  managerId: number | null
  managerName: string | null
  deleted: boolean
  profilePhotoUrl: string | null
  createdAt: string
  updatedAt: string
}

export interface DirectoryEntry {
  id: number
  fullName: string
  jobTitle: string
  departmentName: string | null
}

export interface ReferenceItem {
  id: number
  name: string
  departmentId: number | null
}

export interface EmployeeInput {
  fullName: string
  email: string
  phone?: string
  address?: string
  emergencyContact?: string
  dateOfBirth?: string
  joiningDate: string
  jobTitle: string
  employmentStatus: EmploymentStatus
  departmentId: number
  designationId?: number | null
  managerId?: number | null
  userAccount?: { password: string; role: Role }
}

/** Fields that are left out are not changed. */
export interface SelfProfileInput {
  phone?: string
  address?: string
  emergencyContact?: string
}

export interface EmployeeFilters {
  departmentId?: number
  status?: EmploymentStatus
  managerId?: number
  q?: string
  includeOffboarded?: boolean
}

export interface BulkUploadRowError {
  row: number
  email: string
  fullName: string
  reason: string
}

export interface BulkUploadResult {
  totalRows: number
  created: number
  failed: number
  errors: BulkUploadRowError[]
}

export interface ProfileLeaveSummary {
  balances: LeaveBalance[]
  pendingRequests: number
}

export interface ProfileAttendanceSummary {
  month: string
  present: number
  absent: number
  late: number
  todayStatus: string | null
}

export interface ProfileCompletionItem {
  key: 'photo' | 'phone' | 'address' | 'emergencyContact' | 'documents'
  label: string
  done: boolean
}

/** What the employee can still fill in themself; percent is 0-100. */
export interface ProfileCompletion {
  percent: number
  complete: boolean
  items: ProfileCompletionItem[]
}

/** FULL for HR/Admin, the employee and their direct manager; DIRECTORY (name, title, department, photo only) for anyone else. */
export interface EmployeeProfile {
  id: number
  accessLevel: 'FULL' | 'DIRECTORY'
  fullName: string
  jobTitle: string
  departmentName: string | null
  profilePhotoUrl: string | null
  employeeCode: string | null
  email: string | null
  phone: string | null
  address: string | null
  emergencyContact: string | null
  dateOfBirth: string | null
  joiningDate: string | null
  employmentStatus: EmploymentStatus | null
  designationName: string | null
  managerId: number | null
  managerName: string | null
  leave: ProfileLeaveSummary | null
  attendance: ProfileAttendanceSummary | null
  documents: EmployeeDocument[] | null
  directReports: DirectoryEntry[] | null
  completion: ProfileCompletion | null
}
