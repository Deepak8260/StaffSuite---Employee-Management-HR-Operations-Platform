import { PanelLeftClose, PanelLeftOpen } from 'lucide-react'
import { useState } from 'react'
import { Outlet } from 'react-router-dom'
import Sidebar from './shell/Sidebar'
import Topbar from './shell/Topbar'

const COLLAPSE_KEY = 'staffsuite.sidebar.collapsed'

function readCollapsed(): boolean {
  try {
    return localStorage.getItem(COLLAPSE_KEY) === '1'
  } catch {
    return false
  }
}

/** App shell: fixed sidebar on the left (collapsible), top bar with search / bell / user menu, then the page. */
export default function Layout() {
  const [navOpen, setNavOpen] = useState(false)
  const [collapsed, setCollapsed] = useState(readCollapsed)

  function toggleCollapsed() {
    const next = !collapsed
    setCollapsed(next)
    try {
      localStorage.setItem(COLLAPSE_KEY, next ? '1' : '0')
    } catch {
      /* the choice just won't be remembered */
    }
  }

  return (
    <div className={`app-shell ${navOpen ? 'nav-open' : ''} ${collapsed ? 'collapsed' : ''}`}>
      <aside className="sidebar">
        <div className="brand">
          <span className="brand-name">Staff<b>Suite</b></span>
          <button type="button" className="collapse-btn" data-tip={collapsed ? 'Expand sidebar' : 'Collapse sidebar'}
            aria-label={collapsed ? 'Expand sidebar' : 'Collapse sidebar'} title={collapsed ? 'Expand sidebar' : undefined} onClick={toggleCollapsed}>
            {collapsed ? <PanelLeftOpen size={18} aria-hidden="true" /> : <PanelLeftClose size={18} aria-hidden="true" />}
          </button>
        </div>
        <Sidebar collapsed={collapsed} onToggleCollapsed={toggleCollapsed} onNavigate={() => setNavOpen(false)} />
      </aside>
      {navOpen && <div className="sidebar-scrim" onClick={() => setNavOpen(false)} />}
      <div className="app-main">
        <Topbar onMenu={() => setNavOpen(true)} />
        <div className="app-content">
          <Outlet />
        </div>
      </div>
    </div>
  )
}
