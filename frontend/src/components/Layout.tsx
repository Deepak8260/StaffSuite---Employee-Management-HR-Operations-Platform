import { PanelLeftClose, PanelLeftOpen } from 'lucide-react'
import { useRef, useState, type CSSProperties, type PointerEvent as ReactPointerEvent } from 'react'
import { Outlet } from 'react-router-dom'
import logo from '../assets/logo.png'
import Sidebar from './shell/Sidebar'
import Topbar from './shell/Topbar'

const COLLAPSE_KEY = 'staffsuite.sidebar.collapsed'
const WIDTH_KEY = 'staffsuite.sidebar.width'
const MIN_WIDTH = 180
const MAX_WIDTH = 400

/** null = use the stylesheet's default width. */
function readWidth(): number | null {
  try {
    const n = Number(localStorage.getItem(WIDTH_KEY))
    return n >= MIN_WIDTH && n <= MAX_WIDTH ? n : null
  } catch {
    return null
  }
}

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
  const [width, setWidth] = useState<number | null>(readWidth)
  const [resizing, setResizing] = useState(false)
  const [tipY, setTipY] = useState(120)
  const latest = useRef<number | null>(width)

  function remember(next: number | null) {
    try {
      if (next === null) localStorage.removeItem(WIDTH_KEY)
      else localStorage.setItem(WIDTH_KEY, String(Math.round(next)))
    } catch {
      /* the width just won't be remembered */
    }
  }

  function startResize(e: ReactPointerEvent<HTMLDivElement>) {
    e.preventDefault()
    e.currentTarget.setPointerCapture(e.pointerId)
    setResizing(true)
  }

  function resize(e: ReactPointerEvent<HTMLDivElement>) {
    setTipY(e.clientY)
    if (!resizing) return
    const next = Math.min(MAX_WIDTH, Math.max(MIN_WIDTH, e.clientX))
    latest.current = next
    setWidth(next)
  }

  function stopResize() {
    if (!resizing) return
    setResizing(false)
    remember(latest.current)
  }

  function resetWidth() {
    latest.current = null
    setWidth(null)
    remember(null)
  }

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
    <div className={`app-shell ${navOpen ? 'nav-open' : ''} ${collapsed ? 'collapsed' : ''} ${resizing ? 'resizing' : ''}`}
      style={width === null ? undefined : ({ '--sidebar-w': `${width}px` } as CSSProperties)}>
      <aside className="sidebar">
        <div className="brand" aria-label="StaffSuite">
          <img className="brand-logo" src={logo} alt="" />
          <span className="brand-name">Staff<b>Suite</b></span>
        </div>
        <Sidebar collapsed={collapsed} onToggleCollapsed={toggleCollapsed} onNavigate={() => setNavOpen(false)} />
      </aside>
      {/* drag the sidebar's right edge to change its width; double-click puts it back */}
      <div className="sidebar-resizer" role="separator" aria-orientation="vertical" aria-label="Resize sidebar"
        onPointerDown={startResize} onPointerMove={resize} onPointerUp={stopResize} onPointerCancel={stopResize} onDoubleClick={resetWidth}>
        <span className="sidebar-resizer-tip" style={{ top: tipY }}>Drag to resize</span>
      </div>
      {/* a round button on the sidebar's right edge; it lives outside the sidebar so the sidebar's scrolling cannot clip it */}
      <button type="button" className="collapse-btn" data-tip={collapsed ? 'Expand sidebar' : 'Collapse sidebar'}
        aria-label={collapsed ? 'Expand sidebar' : 'Collapse sidebar'} onClick={toggleCollapsed}>
        {collapsed ? <PanelLeftOpen size={15} aria-hidden="true" /> : <PanelLeftClose size={15} aria-hidden="true" />}
      </button>
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
