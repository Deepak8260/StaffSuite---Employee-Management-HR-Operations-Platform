import { ChevronDown, KeyRound, LogOut, Menu, Moon, Search, Sun, UserRound } from 'lucide-react'
import { useEffect, useRef, useState, type FormEvent } from 'react'
import { Link, useLocation, useNavigate } from 'react-router-dom'
import { useAuth } from '../../hooks/useAuth'
import { useDisplayName } from '../../hooks/useDisplayName'
import { useTheme } from '../../hooks/useTheme'
import type { Role } from '../../types/auth'
import NotificationBell from '../NotificationBell'
import StatusPill from '../ui/StatusPill'

const ROLE_LABEL: Record<Role, string> = {
  SYSTEM_ADMIN: 'Admin',
  HR_ADMIN: 'HR',
  MANAGER: 'Manager',
  EMPLOYEE: 'Employee',
}

export default function Topbar({ onMenu }: { onMenu: () => void }) {
  const { user, logout } = useAuth()
  const { fullName } = useDisplayName(user)
  const { theme, toggle } = useTheme()
  const navigate = useNavigate()
  const location = useLocation()
  const menuRef = useRef<HTMLDivElement>(null)
  const [menuOpen, setMenuOpen] = useState(false)
  const [query, setQuery] = useState('')

  useEffect(() => {
    // eslint-disable-next-line react-hooks/set-state-in-effect
    setMenuOpen(false)
  }, [location.pathname])

  useEffect(() => {
    function onDown(e: MouseEvent) {
      if (menuRef.current && !menuRef.current.contains(e.target as Node)) setMenuOpen(false)
    }
    document.addEventListener('mousedown', onDown)
    return () => document.removeEventListener('mousedown', onDown)
  }, [])

  function search(e: FormEvent) {
    e.preventDefault()
    const q = query.trim()
    navigate(q ? `/employees?q=${encodeURIComponent(q)}` : '/employees')
  }

  async function handleLogout() {
    await logout()
    navigate('/login', { replace: true })
  }

  if (!user) return null
  return (
    <header className="topbar">
      <button type="button" className="icon-button menu-toggle" aria-label="Open navigation" onClick={onMenu}>
        <Menu size={20} aria-hidden="true" />
      </button>

      {user.role === 'EMPLOYEE' ? <span className="topbar-spacer" /> : (
        <form className="global-search" role="search" onSubmit={search}>
          <Search size={18} aria-hidden="true" />
          <input type="search" value={query} onChange={(e) => setQuery(e.target.value)}
            placeholder="Search employees, documents, leave requests..." aria-label="Search" />
        </form>
      )}

      <div className="topbar-right">
        <div className="topbar-tools">
          <button type="button" className="bell-button theme-toggle" onClick={toggle}
            aria-label={theme === 'dark' ? 'Switch to light mode' : 'Switch to dark mode'}
            title={theme === 'dark' ? 'Switch to light mode' : 'Switch to dark mode'}>
            {theme === 'dark' ? <Sun size={20} aria-hidden="true" /> : <Moon size={20} aria-hidden="true" />}
          </button>
          <NotificationBell />
        </div>
        <div className="user-menu" ref={menuRef}>
          <button type="button" className="user-button" aria-expanded={menuOpen} onClick={() => setMenuOpen(!menuOpen)}>
            <span className="user-avatar" aria-hidden="true">{fullName.charAt(0).toUpperCase()}</span>
            <span className="user-text">
              <span className="user-name">{fullName}</span>
              <span className="user-role">{user.role}</span>
            </span>
            <ChevronDown size={16} aria-hidden="true" />
          </button>
          {menuOpen && (
            <div className="user-dropdown" role="menu">
              <div className="user-dropdown-head">
                <strong>{fullName}</strong>
                <span className="hint">{user.email}</span>
                <StatusPill tone="info">{ROLE_LABEL[user.role]} / {user.role}</StatusPill>
              </div>
              {user.employeeId != null && <Link to={`/employees/${user.employeeId}/profile`} role="menuitem"><UserRound size={16} aria-hidden="true" /> My profile</Link>}
              <Link to="/change-password" role="menuitem"><KeyRound size={16} aria-hidden="true" /> Change password</Link>
              <button type="button" role="menuitem" onClick={handleLogout}><LogOut size={16} aria-hidden="true" /> Log out</button>
            </div>
          )}
        </div>
      </div>
    </header>
  )
}
