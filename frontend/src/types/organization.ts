import type { EmploymentStatus } from './employee'

export interface Department {
  id: number
  name: string
  description: string | null
  active: boolean
  employeeCount: number
}

export interface DepartmentInput {
  name: string
  description?: string
  active?: boolean
}

export interface Designation {
  id: number
  title: string
  description: string | null
  departmentId: number | null
  departmentName: string | null
  active: boolean
}

export interface DesignationInput {
  title: string
  description?: string
  departmentId?: number | null
  active?: boolean
}

export interface OrgNode {
  id: number
  employeeCode: string
  fullName: string
  jobTitle: string
  departmentName: string | null
  employmentStatus: EmploymentStatus
  children: OrgNode[]
}

export interface ReassignInput {
  departmentId?: number
  managerId?: number
  removeManager?: boolean
}
