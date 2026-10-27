export type Role = 'SYSTEM_ADMIN' | 'HR_ADMIN' | 'MANAGER' | 'EMPLOYEE'

export const ROLES: Role[] = ['SYSTEM_ADMIN', 'HR_ADMIN', 'MANAGER', 'EMPLOYEE']

export interface AuthUser {
  userId: number
  email: string
  role: Role
  employeeId: number | null
}

export interface UserSummary {
  id: number
  email: string
  role: Role
  active: boolean
  employeeId: number | null
  lastLoginAt: string | null
  createdAt: string
  updatedAt: string
}

export interface LoginResponse {
  token: string
  tokenType: string
  expiresInMs: number
  user: UserSummary
}

export interface PageResponse<T> {
  content: T[]
  page: number
  size: number
  totalElements: number
  totalPages: number
}

export interface ApiError {
  timestamp: string
  status: number
  error: string
  message: string
  path: string
}
