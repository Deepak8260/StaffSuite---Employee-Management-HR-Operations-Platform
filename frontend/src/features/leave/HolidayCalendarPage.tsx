import { CalendarDays, CalendarHeart, PartyPopper, Sparkles } from 'lucide-react'
import { useEffect, useMemo, useState } from 'react'
import { Link } from 'react-router-dom'
import { errorMessage } from '../../api/axiosInstance'
import { listHolidays } from '../../api/leave'
import DataTable, { type Column } from '../../components/ui/DataTable'
import PageHeader from '../../components/ui/PageHeader'
import SectionCard from '../../components/ui/SectionCard'
import StatCard from '../../components/ui/StatCard'
import StatusPill from '../../components/ui/StatusPill'
import { useAuth } from '../../hooks/useAuth'
import type { Holiday } from '../../types/leave'
import { fromISO, isWeekend, toISO } from '../../utils/dates'
import { canManageEmployees } from '../../utils/roles'

function daysUntil(iso: string, today: Date): number {
  const ms = fromISO(iso).getTime() - new Date(today.getFullYear(), today.getMonth(), today.getDate()).getTime()
  return Math.round(ms / 86_400_000)
}

function relative(days: number): string {
  if (days === 0) return 'today'
  if (days === 1) return 'tomorrow'
  return `in ${days} days`
}

/** Read-only yearly holiday list for everyone, grouped by month. */
export default function HolidayCalendarPage() {
  const { user } = useAuth()
  const today = new Date()
  const todayISO = toISO(today)
  const [year, setYear] = useState(today.getFullYear())
  const [holidays, setHolidays] = useState<Holiday[] | null>(null)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    let cancelled = false
    listHolidays(year)
      .then((list) => {
        if (!cancelled) {
          setHolidays(list)
          setError(null)
        }
      })
      .catch((err) => {
        if (!cancelled) setError(errorMessage(err, 'Could not load the holiday calendar'))
      })
    return () => {
      cancelled = true
    }
  }, [year])

  const months = useMemo(() => {
    const groups = new Map<number, Holiday[]>()
    holidays?.forEach((h) => {
      const m = fromISO(h.holidayDate).getMonth()
      groups.set(m, [...(groups.get(m) ?? []), h])
    })
    return [...groups.entries()].sort((a, b) => a[0] - b[0])
  }, [holidays])

  const next = year === today.getFullYear() ? holidays?.find((h) => h.holidayDate >= todayISO) : undefined
  const publicCount = holidays?.filter((h) => !h.optional).length ?? 0
  const optionalCount = holidays?.filter((h) => h.optional).length ?? 0

  const isPast = (h: Holiday) => (year === today.getFullYear() ? h.holidayDate < todayISO : year < today.getFullYear())
  const columns: Column<Holiday>[] = [
    { key: 'date', header: 'Date', render: (h) => fromISO(h.holidayDate).toLocaleDateString(undefined, { day: '2-digit', month: 'short' }) },
    { key: 'day', header: 'Day', render: (h) => {
      const d = fromISO(h.holidayDate)
      return <>{d.toLocaleDateString(undefined, { weekday: 'long' })}{isWeekend(d) && <span className="tag">Weekend</span>}</>
    } },
    { key: 'name', header: 'Holiday', render: (h) => (
      <>
        <strong>{h.name}</strong>
        {h.description && <div className="hint">{h.description}</div>}
      </>
    ) },
    { key: 'type', header: 'Type', render: (h) => <StatusPill tone={h.optional ? 'info' : 'success'}>{h.optional ? 'Optional' : 'Public holiday'}</StatusPill> },
  ]

  return (
    <main className="page holiday-page">
      <div className="no-print">
        <PageHeader title={`Holiday calendar ${year}`}
          description="Company holidays for the year. Public holidays are not deducted from your leave balance."
          actions={(
            <>
              <button onClick={() => setYear(year - 1)} aria-label="Previous year">‹</button>
              <strong>{year}</strong>
              <button onClick={() => setYear(year + 1)} aria-label="Next year">›</button>
              <button onClick={() => window.print()}>Print</button>
              {canManageEmployees(user?.role) && <Link to="/admin/holidays" className="button">Manage holidays</Link>}
            </>
          )} />
      </div>
      <h1 className="print-only">Holiday calendar {year}</h1>

      {error && <p className="error">{error}</p>}

      {holidays && (
        <>
          <div className="stat-grid">
            <StatCard icon={CalendarDays} tint="orange" label="Public holidays" value={publicCount} />
            <StatCard icon={Sparkles} tint="orange" label="Optional holidays" value={optionalCount} />
            {next && (
              <StatCard icon={CalendarHeart} tint="orange" label="Next holiday" value={next.name}
                trend={`${fromISO(next.holidayDate).toLocaleDateString(undefined, { weekday: 'long', day: 'numeric', month: 'long' })} · ${relative(daysUntil(next.holidayDate, today))}`} />
            )}
          </div>

          {holidays.length === 0 && <p>No holidays have been published for {year} yet.</p>}

          {months.map(([month, list]) => (
            <SectionCard key={month} title={new Date(year, month, 1).toLocaleDateString(undefined, { month: 'long' })}
              icon={PartyPopper} tint="orange" actions={<span className="hint">{list.length} holiday{list.length === 1 ? '' : 's'}</span>}>
              <DataTable columns={columns} rows={list} rowKey={(h) => h.id}
                rowClassName={(h) => [isPast(h) ? 'past' : '', next?.id === h.id ? 'upcoming' : ''].join(' ')} />
            </SectionCard>
          ))}

          <p className="hint note">
            Optional holidays are shown for information; they do not reduce the number of leave days if you apply on that date.
          </p>
        </>
      )}
    </main>
  )
}
