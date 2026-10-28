import { useState } from 'react'
import { Outlet } from 'react-router-dom'
import Sidebar from './shell/Sidebar'
import Topbar from './shell/Topbar'

/** App shell: fixed sidebar on the left, top bar with search / bell / user menu, then the page. */
export default function Layout() {
  const [navOpen, setNavOpen] = useState(false)

  return (
    <div className={`app-shell ${navOpen ? 'nav-open' : ''}`}>
      <aside className="sidebar">
        <div className="brand">
          <span className="brand-mark" aria-hidden="true">S</span>
          <span className="brand-name">StaffSuite</span>
        </div>
        <Sidebar onNavigate={() => setNavOpen(false)} />
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
