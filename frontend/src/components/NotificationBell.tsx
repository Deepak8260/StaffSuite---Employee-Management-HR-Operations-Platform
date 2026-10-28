import { useCallback, useEffect, useRef, useState } from 'react'
import { Link, useLocation, useNavigate } from 'react-router-dom'
import {
  getPendingApprovals,
  getUnreadCount,
  listNotifications,
  markAllNotificationsRead,
  markNotificationRead,
} from '../api/notifications'
import { useAuth } from '../hooks/useAuth'
import type { NotificationItem, PendingApprovals } from '../types/notifications'
import { NOTIFICATIONS_CHANGED, announceNotificationsChanged, notificationPath, timeAgo } from '../utils/notifications'

const POLL_MS = 30_000
const SHOWN = 8

export default function NotificationBell() {
  const { user } = useAuth()
  const navigate = useNavigate()
  const location = useLocation()
  const ref = useRef<HTMLDivElement>(null)
  const [count, setCount] = useState(0)
  const [open, setOpen] = useState(false)
  const [items, setItems] = useState<NotificationItem[]>([])
  const [pending, setPending] = useState<PendingApprovals | null>(null)
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const refreshCount = useCallback(async () => {
    try {
      setCount(await getUnreadCount())
    } catch {
      /* keep the last known count; the next poll will try again */
    }
  }, [])

  const loadPanel = useCallback(async () => {
    setLoading(true)
    setError(null)
    try {
      const [page, waiting] = await Promise.all([listNotifications(0, SHOWN), getPendingApprovals()])
      setItems(page.content)
      setPending(waiting)
    } catch {
      setError('Could not load notifications.')
    } finally {
      setLoading(false)
    }
  }, [])

  // keep the badge honest: poll, refresh when the tab regains focus, and when another page changed something
  useEffect(() => {
    // eslint-disable-next-line react-hooks/set-state-in-effect
    void refreshCount()
    const timer = window.setInterval(() => void refreshCount(), POLL_MS)
    const onChange = () => {
      void refreshCount()
      if (open) void loadPanel()
    }
    window.addEventListener('focus', onChange)
    window.addEventListener(NOTIFICATIONS_CHANGED, onChange)
    return () => {
      window.clearInterval(timer)
      window.removeEventListener('focus', onChange)
      window.removeEventListener(NOTIFICATIONS_CHANGED, onChange)
    }
  }, [refreshCount, loadPanel, open])

  useEffect(() => {
    // eslint-disable-next-line react-hooks/set-state-in-effect
    setOpen(false)
  }, [location.pathname, location.search])

  useEffect(() => {
    function onDown(e: MouseEvent) {
      if (ref.current && !ref.current.contains(e.target as Node)) setOpen(false)
    }
    function onKey(e: KeyboardEvent) {
      if (e.key === 'Escape') setOpen(false)
    }
    document.addEventListener('mousedown', onDown)
    document.addEventListener('keydown', onKey)
    return () => {
      document.removeEventListener('mousedown', onDown)
      document.removeEventListener('keydown', onKey)
    }
  }, [])

  function toggle() {
    const next = !open
    setOpen(next)
    if (next) {
      void loadPanel()
      void refreshCount()
    }
  }

  async function openItem(n: NotificationItem) {
    try {
      if (!n.read) await markNotificationRead(n.id)
    } catch {
      /* still navigate; the item will simply stay unread */
    }
    announceNotificationsChanged()
    const path = notificationPath(n, user?.role)
    setOpen(false)
    if (path) navigate(path)
  }

  async function readAll() {
    try {
      await markAllNotificationsRead()
      announceNotificationsChanged()
      await loadPanel()
      await refreshCount()
    } catch {
      setError('Could not mark notifications as read.')
    }
  }

  const manager = user?.role === 'MANAGER'
  const leavePath = manager ? '/leave/team' : '/leave/all'
  const correctionPath = manager ? '/attendance/team' : '/attendance/reports'

  return (
    <div className="bell" ref={ref}>
      <button type="button" className="bell-button" onClick={toggle} aria-expanded={open}
        aria-label={count > 0 ? `Notifications, ${count} unread` : 'Notifications'}>
        <svg viewBox="0 0 24 24" width="22" height="22" fill="none" stroke="currentColor" strokeWidth="2"
          strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
          <path d="M18 8a6 6 0 0 0-12 0c0 7-3 9-3 9h18s-3-2-3-9" />
          <path d="M13.7 21a2 2 0 0 1-3.4 0" />
        </svg>
        {count > 0 && <span className="bell-badge">{count > 99 ? '99+' : count}</span>}
      </button>

      {open && (
        <div className="bell-panel" role="dialog" aria-label="Notifications">
          <div className="bell-head">
            <strong>Notifications</strong>
            <button type="button" className="link-button" onClick={readAll} disabled={count === 0}>Mark all as read</button>
          </div>

          {pending && pending.total > 0 && (
            <div className="bell-pending">
              <strong>{pending.total}</strong> waiting for your decision:{' '}
              {pending.leaveRequests > 0 && (
                <Link to={leavePath}>{pending.leaveRequests} leave request{pending.leaveRequests === 1 ? '' : 's'}</Link>
              )}
              {pending.leaveRequests > 0 && pending.attendanceCorrections > 0 && ' · '}
              {pending.attendanceCorrections > 0 && (
                <Link to={correctionPath}>{pending.attendanceCorrections} attendance correction{pending.attendanceCorrections === 1 ? '' : 's'}</Link>
              )}
            </div>
          )}

          <div className="bell-list">
            {loading && items.length === 0 && <p className="hint bell-empty">Loading…</p>}
            {error && <p className="error bell-empty">{error}</p>}
            {!loading && !error && items.length === 0 && <p className="hint bell-empty">You have no notifications yet.</p>}
            {items.map((n) => (
              <button type="button" key={n.id} className={`bell-item ${n.read ? '' : 'unread'}`} onClick={() => void openItem(n)}>
                <span className="bell-dot" aria-hidden="true" />
                <span className="bell-text">
                  <span className="bell-message">{n.message}</span>
                  <span className="hint">{timeAgo(n.createdAt)}</span>
                </span>
              </button>
            ))}
          </div>

          <div className="bell-foot">
            <Link to="/notifications">View all notifications</Link>
          </div>
        </div>
      )}
    </div>
  )
}
