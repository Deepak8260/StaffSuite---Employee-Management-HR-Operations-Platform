import type { Role } from '../types/auth'
import { formatDateOnly } from './dates'

/** Mirrors the backend: only these roles can create, edit and offboard employees. */
export function canManageEmployees(role: Role | undefined): boolean {
  return role === 'SYSTEM_ADMIN' || role === 'HR_ADMIN'
}

/** Roles that can open the full employee list (managers are limited to their team by the backend). */
export function canListEmployees(role: Role | undefined): boolean {
  return canManageEmployees(role) || role === 'MANAGER'
}

/** Login roles an actor may assign when onboarding (HR_ADMIN cannot grant elevated roles). */
export function grantableRoles(role: Role | undefined): Role[] {
  if (role === 'SYSTEM_ADMIN') return ['EMPLOYEE', 'MANAGER', 'HR_ADMIN', 'SYSTEM_ADMIN']
  if (role === 'HR_ADMIN') return ['EMPLOYEE', 'MANAGER']
  return []
}

export function formatDate(value: string | null | undefined): string {
  return formatDateOnly(value)
}
