import type { ReactNode } from 'react'
import { Navigate, useLocation } from 'react-router-dom'
import { useAuth } from '../hooks/useAuth'
import type { Role } from '../types/auth'

interface Props {
  /** When given, only these roles may see the route. */
  roles?: Role[]
  children: ReactNode
}

export default function ProtectedRoute({ roles, children }: Props) {
  const { user } = useAuth()
  const location = useLocation()

  if (!user) {
    return <Navigate to="/login" replace state={{ from: location.pathname }} />
  }
  if (roles && !roles.includes(user.role)) {
    return (
      <main className="page">
        <h1>Access denied</h1>
        <p>Your role ({user.role}) does not have permission to view this page.</p>
      </main>
    )
  }
  return <>{children}</>
}
