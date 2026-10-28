import { useEffect, useRef, useState, type ReactNode } from 'react'
import { Link, Outlet, useLocation, useNavigate } from 'react-router-dom'
import { useAuth } from '../hooks/useAuth'
import { canListEmployees, canManageEmployees } from '../utils/roles'
import NotificationBell from './NotificationBell'

function NavMenu({ label, children }: { label: string; children: ReactNode }) {
  const [open, setOpen] = useState(false)
  const ref = useRef<HTMLDivElement>(null)
  const location = useLocation()

  // close when the route changes or the user clicks elsewhere
  useEffect(() => {
    // eslint-disable-next-line react-hooks/set-state-in-effect
    setOpen(false)
  }, [location.pathname])
  useEffect(() => {
    function onClick(e: MouseEvent) {
      if (ref.current && !ref.current.contains(e.target as Node)) setOpen(false)
    }
    document.addEventListener('mousedown', onClick)
    return () => document.removeEventListener('mousedown', onClick)
  }, [])

  return (
    <div className="nav-menu" ref={ref}>
      <button type="button" className="nav-menu-button" aria-expanded={open} onClick={() => setOpen(!open)}>
        {label} ▾
      </button>
      {open && <div className="nav-menu-items">{children}</div>}
    </div>
  )
}

export default function Layout() {
  const { user, logout } = useAuth()
  const navigate = useNavigate()
  const role = user?.role
  const canManage = canManageEmployees(role)

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
          <Link to="/org-chart">Org Chart</Link>
          {canListEmployees(role) && <Link to="/employees">{canManage ? 'Employees' : 'My Team'}</Link>}

          <NavMenu label="Attendance">
            {user?.employeeId != null && <Link to="/attendance">My Attendance</Link>}
            {role === 'MANAGER' && <Link to="/attendance/team">Team Attendance</Link>}
            {canManage && <Link to="/attendance/reports">Attendance Reports</Link>}
          </NavMenu>

          <NavMenu label="Leave">
            {user?.employeeId != null && <Link to="/leave">My Leave</Link>}
            <Link to="/holidays">Holiday Calendar</Link>
            <Link to="/leave/calendar">Leave Calendar</Link>
            {role === 'MANAGER' && <Link to="/leave/team">Team Leave</Link>}
            {canManage && <Link to="/leave/all">All Leave</Link>}
          </NavMenu>

          {canManage && (
            <NavMenu label="Admin">
              <Link to="/admin/departments">Departments</Link>
              <Link to="/admin/designations">Designations</Link>
              <Link to="/admin/documents">Employee Documents</Link>
              <Link to="/admin/documents/expiring">Expiring Documents</Link>
              <Link to="/admin/document-categories">Document Categories</Link>
              <Link to="/admin/leave-types">Leave Types</Link>
              <Link to="/admin/holidays">Public Holidays</Link>
              {role === 'SYSTEM_ADMIN' && <Link to="/admin/users">User Management</Link>}
            </NavMenu>
          )}

          {user?.employeeId != null && <Link to="/documents">My Documents</Link>}
          {user?.employeeId != null && <Link to="/my-profile">My Profile</Link>}
          <Link to="/change-password">Change password</Link>
        </nav>
        <span className="topbar-user">
          <NotificationBell />
          {user?.email} ({role})
          <button onClick={handleLogout}>Log out</button>
        </span>
      </header>
      <Outlet />
    </>
  )
}
