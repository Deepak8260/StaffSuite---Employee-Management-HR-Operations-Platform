import { useEffect, useState } from 'react'
import { getEmployee } from '../api/employees'
import type { AuthUser } from '../types/auth'

const cache = new Map<number, string>()

/** "mark.manager@x.com" -> "Mark Manager": the fallback for logins that have no employee record. */
function nameFromEmail(email: string): string {
  return email.split('@')[0].split(/[._-]+/).filter(Boolean).map((p) => p.charAt(0).toUpperCase() + p.slice(1)).join(' ') || email
}

/** The signed-in person's real name (from their employee record), with a readable fallback while it loads. */
export function useDisplayName(user: AuthUser | null): { fullName: string; firstName: string } {
  const employeeId = user?.employeeId ?? null
  const [loaded, setLoaded] = useState<{ id: number; name: string } | null>(
    employeeId !== null && cache.has(employeeId) ? { id: employeeId, name: cache.get(employeeId)! } : null,
  )

  useEffect(() => {
    if (employeeId === null || cache.has(employeeId)) return
    let cancelled = false
    getEmployee(employeeId)
      .then((e) => {
        cache.set(employeeId, e.fullName)
        if (!cancelled) setLoaded({ id: employeeId, name: e.fullName })
      })
      .catch(() => { /* keep the email-based name */ })
    return () => { cancelled = true }
  }, [employeeId])

  const fullName = employeeId !== null && loaded?.id === employeeId ? loaded.name : user ? nameFromEmail(user.email) : ''
  return { fullName, firstName: fullName.split(' ')[0] }
}
