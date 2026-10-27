import { Link, Outlet, useNavigate } from 'react-router-dom'
import { useAuth } from '../hooks/useAuth'
import { canListEmployees, canManageEmployees } from '../utils/roles'

export default function Layout() {
  const { user, logout } = useAuth()
  const navigate = useNavigate()

  async function handleLogout() {
    await logout()
    navigate('/login', { replace: true })
  }

  return (
    <>
      <header className="topbar">
        <strong>StaffSuite</strong>
        <nav>
          <Link to="/">Home</Link>
          <Link to="/directory">Directory</Link>
          {canListEmployees(user?.role) && (
            <Link to="/employees">{canManageEmployees(user?.role) ? 'Employees' : 'My Team'}</Link>
          )}
          <Link to="/org-chart">Org Chart</Link>
          {canManageEmployees(user?.role) && <Link to="/admin/departments">Departments</Link>}
          {canManageEmployees(user?.role) && <Link to="/admin/designations">Designations</Link>}
          {user?.employeeId != null && <Link to="/my-profile">My Profile</Link>}
          {user?.role === 'SYSTEM_ADMIN' && <Link to="/admin/users">User Management</Link>}
          <Link to="/change-password">Change password</Link>
        </nav>
        <span className="topbar-user">
          {user?.email} ({user?.role})
          <button onClick={handleLogout}>Log out</button>
        </span>
      </header>
      <Outlet />
    </>
  )
}
