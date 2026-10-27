import type { AuthUser, Role } from '../types/auth'

interface JwtPayload {
  userId: number
  email: string
  role: Role
  employeeId?: number | null
  exp: number
}

function decodePayload(token: string): JwtPayload | null {
  try {
    const part = token.split('.')[1]
    const base64 = part.replace(/-/g, '+').replace(/_/g, '/')
    const json = decodeURIComponent(
      atob(base64)
        .split('')
        .map((c) => '%' + c.charCodeAt(0).toString(16).padStart(2, '0'))
        .join(''),
    )
    return JSON.parse(json) as JwtPayload
  } catch {
    return null
  }
}

/** Returns the user encoded in a still-valid token, otherwise null. */
export function userFromToken(token: string): AuthUser | null {
  const payload = decodePayload(token)
  if (!payload || !payload.userId || !payload.role) return null
  if (payload.exp * 1000 <= Date.now()) return null
  return {
    userId: payload.userId,
    email: payload.email,
    role: payload.role,
    employeeId: payload.employeeId ?? null,
  }
}
