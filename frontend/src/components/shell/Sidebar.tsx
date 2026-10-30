import {
  CalendarDays, ChevronDown, ChevronRight, File, FileText, House, Lock, type LucideIcon, Network, PanelLeftClose, PanelLeftOpen,
  ShieldCheck, Users,
} from 'lucide-react'
import { useEffect, useState } from 'react'
import { NavLink, useLocation } from 'react-router-dom'
import { useAuth } from '../../hooks/useAuth'
import { canManageEmployees } from '../../utils/roles'

interface Item {
  to: string
  label: string
}

type IconTone = 'blue' | 'green' | 'orange' | 'slate'

interface GroupDef {
  label: string
  icon: LucideIcon
  tone: IconTone
  items: Item[]
}

function SideLink({ to, label, icon: Icon, tone, end, fill }: {
  to: string; label: string; icon: LucideIcon; tone: IconTone; end?: boolean; fill?: boolean
}) {
  return (
    <NavLink to={to} end={end} title={label} className={({ isActive }) => `side-link ${isActive ? 'active' : ''}`}>
      <Icon size={20} strokeWidth={1.9} className={`ico ico-${tone}`} fill={fill ? 'currentColor' : 'none'} aria-hidden="true" />
      <span className="side-label">{label}</span>
    </NavLink>
  )
}

function SideGroup({ group, onExpandSidebar, collapsed }: { group: GroupDef; onExpandSidebar: () => void; collapsed: boolean }) {
  const { pathname } = useLocation()
  const containsActive = group.items.some((i) => pathname === i.to || pathname.startsWith(`${i.to}/`))
  const [open, setOpen] = useState(containsActive)
  const Icon = group.icon
  // navigating into a page of this group opens it; after that the user can fold it freely
  useEffect(() => {
    if (containsActive) setOpen(true)
  }, [pathname, containsActive])
  const expanded = open
  return (
    <div className="side-group">
      <button type="button" title={group.label} className={`side-link side-group-button ${containsActive ? 'has-active' : ''}`}
        aria-expanded={expanded} onClick={() => (collapsed ? onExpandSidebar() : setOpen(!expanded))}>
        <Icon size={20} strokeWidth={1.9} className={`ico ico-${group.tone}`} aria-hidden="true" />
        <span className="side-label">{group.label}</span>
        {expanded ? <ChevronDown size={16} className="chevron" aria-hidden="true" /> : <ChevronRight size={16} className="chevron" aria-hidden="true" />}
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

interface Props {
  collapsed: boolean
  onToggleCollapsed: () => void
  onNavigate?: () => void
}

/** Left navigation. Which entries appear depends only on the role (and whether the login has an employee record). */
export default function Sidebar({ collapsed, onToggleCollapsed, onNavigate }: Props) {
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
    ...(hasProfile ? [{ to: '/admin/documents', label: 'Employee Documents' }] : []),
    { to: '/admin/documents/expiring', label: 'Expiring Documents' },
    { to: '/admin/document-categories', label: 'Document Categories' },
    ...(role === 'SYSTEM_ADMIN' ? [{ to: '/admin/users', label: 'User Management' }] : []),
  ] : []

  const documentsTo = hasProfile ? '/documents' : canManage ? '/admin/documents' : null

  return (
    <nav className="sidebar-nav" aria-label="Main" onClick={(e) => { if ((e.target as HTMLElement).closest('a')) onNavigate?.() }}>
      <div className="side-tools">
        <button type="button" className="collapse-btn" data-tip={collapsed ? 'Expand sidebar' : 'Collapse sidebar'}
          aria-label={collapsed ? 'Expand sidebar' : 'Collapse sidebar'} onClick={onToggleCollapsed}>
          {collapsed ? <PanelLeftOpen size={18} aria-hidden="true" /> : <PanelLeftClose size={18} aria-hidden="true" />}
        </button>
      </div>
      <SideLink to="/" label="Home" icon={House} tone="blue" end fill />
      <SideLink to="/org-chart" label="Org Chart" icon={Network} tone="green" />
      <SideLink to="/employees" label="Employees" icon={Users} tone="green" />
      {attendance.length > 0 && <SideGroup group={{ label: 'Attendance', icon: CalendarDays, tone: 'orange', items: attendance }} onExpandSidebar={onToggleCollapsed} collapsed={collapsed} />}
      <SideGroup group={{ label: 'Leave', icon: FileText, tone: 'green', items: leave }} onExpandSidebar={onToggleCollapsed} collapsed={collapsed} />
      {documentsTo && <SideLink to={documentsTo} label="Documents" icon={File} tone="slate" end />}
      <div className="side-rule" />
      {admin.length > 0 && <SideGroup group={{ label: 'Admin', icon: ShieldCheck, tone: 'slate', items: admin }} onExpandSidebar={onToggleCollapsed} collapsed={collapsed} />}
      <SideLink to="/change-password" label="Change Password" icon={Lock} tone="slate" />
    </nav>
  )
}
