import { useAuth } from '../../hooks/useAuth'

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
    </main>
  )
}
