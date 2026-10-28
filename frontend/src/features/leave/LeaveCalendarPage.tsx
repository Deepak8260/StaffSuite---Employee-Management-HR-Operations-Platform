import { CalendarDays } from 'lucide-react'
import { useEffect, useMemo, useState } from 'react'
import { errorMessage } from '../../api/axiosInstance'
import { getLeaveCalendar } from '../../api/leave'
import MonthGrid from '../../components/MonthGrid'
import PageHeader from '../../components/ui/PageHeader'
import SectionCard from '../../components/ui/SectionCard'
import { useAuth } from '../../hooks/useAuth'
import type { Holiday, LeaveCalendar } from '../../types/leave'
import { toISO } from '../../utils/dates'

const MAX_NAMES = 3

export default function LeaveCalendarPage() {
  const { user } = useAuth()
  const now = new Date()
  const [view, setView] = useState({ year: now.getFullYear(), month: now.getMonth() })
  const [data, setData] = useState<LeaveCalendar | null>(null)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    const from = toISO(new Date(view.year, view.month, 1))
    const to = toISO(new Date(view.year, view.month + 1, 0))
    let cancelled = false
    getLeaveCalendar(from, to)
      .then((d) => {
        if (!cancelled) {
          setData(d)
          setError(null)
        }
      })
      .catch((err) => {
        if (!cancelled) setError(errorMessage(err, 'Could not load the calendar'))
      })
    return () => {
      cancelled = true
    }
  }, [view])

  const holidays = useMemo(() => {
    const map = new Map<string, Holiday>()
    data?.holidays.forEach((h) => map.set(h.holidayDate, h))
    return map
  }, [data])

  const scopeText =
    user?.role === 'EMPLOYEE' ? 'your own approved leave'
    : user?.role === 'MANAGER' ? 'approved leave for you and your team'
    : 'approved leave across the organisation'

  return (
    <main className="page">
      <PageHeader title="Leave calendar" description={`Showing ${scopeText} and public holidays.`} />
      {error && <p className="error">{error}</p>}
      <SectionCard title="Month view" icon={CalendarDays} tint="orange">
      <MonthGrid
        year={view.year}
        month={view.month}
        holidays={holidays}
        tall
        onPrev={() => setView((v) => (v.month === 0 ? { year: v.year - 1, month: 11 } : { ...v, month: v.month - 1 }))}
        onNext={() => setView((v) => (v.month === 11 ? { year: v.year + 1, month: 0 } : { ...v, month: v.month + 1 }))}
        renderDay={(iso) => {
          const onLeave = data?.leaves.filter((l) => l.startDate <= iso && l.endDate >= iso) ?? []
          if (onLeave.length === 0) return null
          return (
            <ul className="cal-leaves">
              {onLeave.slice(0, MAX_NAMES).map((l) => (
                <li key={l.requestId} title={`${l.employeeName} · ${l.leaveTypeName}`}>{l.employeeName}</li>
              ))}
              {onLeave.length > MAX_NAMES && <li className="more">+{onLeave.length - MAX_NAMES} more</li>}
            </ul>
          )
        }}
      />
      <p className="hint legend">
        <span className="key weekend" /> weekend · <span className="key holiday" /> public holiday ·{' '}
        <span className="key optional" /> optional holiday
      </p>
      </SectionCard>
    </main>
  )
}
