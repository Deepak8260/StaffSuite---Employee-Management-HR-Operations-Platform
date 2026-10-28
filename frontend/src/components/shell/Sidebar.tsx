import {
  CalendarCheck, ChevronDown, FileText, Home, KeyRound, type LucideIcon, Network, Plane, ShieldCheck, Users, BookUser,
} from 'lucide-react'
import { useState } from 'react'
import { NavLink, useLocation } from 'react-router-dom'
import { useAuth } from '../../hooks/useAuth'
import { canListEmployees, canManageEmployees } from '../../utils/roles'

interface Item {
  to: string
  label: string
}

interface Group {
  label: string
  icon: LucideIcon
  items: Item[]
}

function SideLink({ to, label, icon: Icon, end }: { to: string; label: string; icon: LucideIcon; end?: boolean }) {
  return (
    <NavLink to={to} end={end} className={({ isActive }) => `side-link ${isActive ? 'active' : ''}`}>
      <Icon size={18} aria-hidden="true" />
      <span>{label}</span>
    </NavLink>
  )
}

function SideGroup({ group }: { group: Group }) {
  const { pathname } = useLocation()
  const containsActive = group.items.some((i) => pathname === i.to || pathname.startsWith(`${i.to}/`))
  const [open, setOpen] = useState(containsActive)
  const Icon = group.icon
  // a group holding the current page is always shown open, so you can see where you are
  const expanded = open || containsActive
  return (
    <div className="side-group">
      <button type="button" className={`side-link side-group-button ${containsActive ? 'has-active' : ''}`}
        aria-expanded={expanded} onClick={() => setOpen(!expanded)}>
        <Icon size={18} aria-hidden="true" />
        <span>{group.label}</span>
        <ChevronDown size={16} className={`chevron ${expanded ? 'open' : ''}`} aria-hidden="true" />
      </button>
      {expanded && (
        <div className="side-sub">
          {group.items.map((i) => (
            <NavLink key={i.to} to={i.to} end className={({ isActive }) => `side-sublink ${isActive ? 'active' : ''}`}>{i.label}</NavLink>
          ))}
        </div>
      )}
    </div>
  )
}

/** Left navigation. Which entries appear depends only on the role (and whether the login has an employee record). */
export default function Sidebar({ onNavigate }: { onNavigate?: () => void }) {
  const { user } = useAuth()
  const role = user?.role
  const canManage = canManageEmployees(role)
  const hasProfile = user?.employeeId != null

  const attendance: Item[] = [
    ...(hasProfile ? [{ to: '/attendance', label: 'My Attendance' }] : []),
    ...(role === 'MANAGER' ? [{ to: '/attendance/team', label: 'Team Attendance' }] : []),
    ...(canManage ? [{ to: '/attendance/reports', label: 'Attendance Reports' }] : []),
  ]
  const leave: Item[] = [
    ...(hasProfile ? [{ to: '/leave', label: 'My Leave' }] : []),
    { to: '/holidays', label: 'Holiday Calendar' },
    { to: '/leave/calendar', label: 'Leave Calendar' },
    ...(role === 'MANAGER' ? [{ to: '/leave/team', label: 'Team Leave' }] : []),
    ...(canManage ? [{ to: '/leave/all', label: 'All Leave' }, { to: '/admin/leave-types', label: 'Leave Types' }, { to: '/admin/holidays', label: 'Public Holidays' }] : []),
  ]
  const admin: Item[] = canManage ? [
    { to: '/admin/dashboard', label: 'HR Analytics' },
    { to: '/admin/departments', label: 'Departments' },
    { to: '/admin/designations', label: 'Designations' },
    { to: '/admin/documents', label: 'Employee Documents' },
    { to: '/admin/documents/expiring', label: 'Expiring Documents' },
    { to: '/admin/document-categories', label: 'Document Categories' },
    ...(role === 'SYSTEM_ADMIN' ? [{ to: '/admin/users', label: 'User Management' }] : []),
  ] : []

  const documentsTo = hasProfile ? '/documents' : canManage ? '/admin/documents' : null

  return (
    <nav className="sidebar-nav" aria-label="Main" onClick={(e) => { if ((e.target as HTMLElement).closest('a')) onNavigate?.() }}>
      <SideLink to="/" label="Home" icon={Home} end />
      <SideLink to="/directory" label="Directory" icon={BookUser} />
      <SideLink to="/org-chart" label="Org Chart" icon={Network} />
      {canListEmployees(role) && <SideLink to="/employees" label={canManage ? 'Employees' : 'My Team'} icon={Users} />}
      {attendance.length > 0 && <SideGroup group={{ label: 'Attendance', icon: CalendarCheck, items: attendance }} />}
      <SideGroup group={{ label: 'Leave', icon: Plane, items: leave }} />
      {documentsTo && <SideLink to={documentsTo} label="Documents" icon={FileText} end />}
      {admin.length > 0 && <SideGroup group={{ label: 'Admin', icon: ShieldCheck, items: admin }} />}
      <div className="side-spacer" />
      <SideLink to="/change-password" label="Change Password" icon={KeyRound} />
    </nav>
  )
}
