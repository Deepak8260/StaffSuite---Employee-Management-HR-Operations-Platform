import { Bell } from 'lucide-react'
import { useCallback, useEffect, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { errorMessage } from '../../api/axiosInstance'
import { listNotifications, markAllNotificationsRead, markNotificationRead } from '../../api/notifications'
import Pager from '../../components/Pager'
import PageHeader from '../../components/ui/PageHeader'
import SectionCard from '../../components/ui/SectionCard'
import { useAuth } from '../../hooks/useAuth'
import type { PageResponse } from '../../types/auth'
import type { NotificationItem } from '../../types/notifications'
import { formatDateTime } from '../../utils/dates'
import { TYPE_LABELS, announceNotificationsChanged, notificationPath, timeAgo } from '../../utils/notifications'

const PAGE_SIZE = 15

export default function NotificationsPage() {
  const { user } = useAuth()
  const navigate = useNavigate()
  const [unreadOnly, setUnreadOnly] = useState(false)
  const [page, setPage] = useState(0)
  const [data, setData] = useState<PageResponse<NotificationItem> | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)

  const load = useCallback(async () => {
    try {
      setData(await listNotifications(page, PAGE_SIZE, unreadOnly))
      setError(null)
    } catch (err) {
      setError(errorMessage(err, 'Could not load notifications'))
    }
  }, [page, unreadOnly])

  useEffect(() => {
    // eslint-disable-next-line react-hooks/set-state-in-effect
    void load()
  }, [load])

  async function readAll() {
    setBusy(true)
    try {
      await markAllNotificationsRead()
      announceNotificationsChanged()
      await load()
    } catch (err) {
      setError(errorMessage(err, 'Could not mark notifications as read'))
    } finally {
      setBusy(false)
    }
  }

  async function open(n: NotificationItem) {
    try {
      if (!n.read) await markNotificationRead(n.id)
    } catch {
      /* navigate anyway */
    }
    announceNotificationsChanged()
    const path = notificationPath(n, user?.role)
    if (path) navigate(path)
    else await load()
  }

  const unreadHere = data?.content.filter((n) => !n.read).length ?? 0

  return (
    <main className="page">
      <PageHeader title="Notifications" description="Updates about your leave, approvals and documents."
        actions={(
          <>
          <div className="segmented" role="group" aria-label="Filter">
            <button type="button" className={!unreadOnly ? 'on' : ''} onClick={() => { setUnreadOnly(false); setPage(0) }}>All</button>
            <button type="button" className={unreadOnly ? 'on' : ''} onClick={() => { setUnreadOnly(true); setPage(0) }}>Unread</button>
          </div>
          <button type="button" onClick={readAll} disabled={busy || (!unreadOnly && unreadHere === 0 && data?.totalElements === 0)}>
            Mark all as read
          </button>
          </>
        )} />
      {error && <p className="error">{error}</p>}

      <SectionCard title="Inbox" icon={Bell} tint="blue">
      <ul className="notif-list">
        {data?.content.map((n) => (
          <li key={n.id}>
            <button type="button" className={`notif-row ${n.read ? '' : 'unread'}`} onClick={() => void open(n)}>
              <span className="bell-dot" aria-hidden="true" />
              <span className="notif-body">
                <span className="notif-type">{TYPE_LABELS[n.type]}</span>
                <span className="notif-message">{n.message}</span>
              </span>
              <span className="notif-time" title={formatDateTime(n.createdAt)}>{timeAgo(n.createdAt)}</span>
            </button>
          </li>
        ))}
        {data && data.content.length === 0 && (
          <li className="hint">{unreadOnly ? 'No unread notifications.' : 'You have no notifications yet.'}</li>
        )}
      </ul>
      {data && <Pager data={data} onPage={setPage} noun="notifications" />}
      </SectionCard>
    </main>
  )
}
