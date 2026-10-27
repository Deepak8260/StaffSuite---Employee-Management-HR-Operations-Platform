import { useAuth } from '../../hooks/useAuth'
import { canManageEmployees } from '../../utils/roles'
import ExpiringDocumentsWidget from '../documents/ExpiringDocumentsWidget'

export default function HomePage() {
  const { user } = useAuth()
  return (
    <main className="page">
      <h1>StaffSuite</h1>
      <p>Employee Management &amp; HR Operations Platform</p>
      {user && (
        <p>
          Signed in as <strong>{user.email}</strong> ({user.role})
        </p>
      )}
      {canManageEmployees(user?.role) && <ExpiringDocumentsWidget />}
    </main>
  )
}
