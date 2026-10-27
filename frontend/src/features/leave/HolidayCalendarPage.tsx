import { useEffect, useMemo, useState } from 'react'
import { Link } from 'react-router-dom'
import { errorMessage } from '../../api/axiosInstance'
import { listHolidays } from '../../api/leave'
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

  return (
    <main className="page holiday-page">
      <div className="page-head no-print">
        <div>
          <h1>Holiday calendar {year}</h1>
          <p className="hint">Company holidays for the year. Public holidays are not deducted from your leave balance.</p>
        </div>
        <div className="actions">
          <button onClick={() => setYear(year - 1)} aria-label="Previous year">‹</button>
          <strong>{year}</strong>
          <button onClick={() => setYear(year + 1)} aria-label="Next year">›</button>
          <button onClick={() => window.print()}>Print</button>
          {canManageEmployees(user?.role) && <Link to="/admin/holidays" className="button">Manage holidays</Link>}
        </div>
      </div>
      <h1 className="print-only">Holiday calendar {year}</h1>

      {error && <p className="error">{error}</p>}

      {holidays && (
        <>
          <div className="holiday-summary">
            <div className="stat">
              <span className="stat-number">{publicCount}</span>
              <span className="stat-label">Public holidays</span>
            </div>
            <div className="stat">
              <span className="stat-number">{optionalCount}</span>
              <span className="stat-label">Optional holidays</span>
            </div>
            {next && (
              <div className="stat next">
                <span className="stat-label">Next holiday</span>
                <span className="stat-title">{next.name}</span>
                <span className="stat-sub">
                  {fromISO(next.holidayDate).toLocaleDateString(undefined, { weekday: 'long', day: 'numeric', month: 'long' })}
                  {' · '}
                  {relative(daysUntil(next.holidayDate, today))}
                </span>
              </div>
            )}
          </div>

          {holidays.length === 0 && <p>No holidays have been published for {year} yet.</p>}

          {months.length > 0 && (
            <table className="holiday-table">
              <thead>
                <tr><th className="col-date">Date</th><th className="col-day">Day</th><th>Holiday</th><th className="col-type">Type</th></tr>
              </thead>
              {months.map(([month, list]) => (
                <tbody key={month}>
                  <tr className="month-row">
                    <th colSpan={4}>
                      {new Date(year, month, 1).toLocaleDateString(undefined, { month: 'long' })}
                      <span className="month-count">{list.length} holiday{list.length === 1 ? '' : 's'}</span>
                    </th>
                  </tr>
                  {list.map((h) => {
                    const d = fromISO(h.holidayDate)
                    const past = year === today.getFullYear() ? h.holidayDate < todayISO : year < today.getFullYear()
                    return (
                      <tr key={h.id} className={[past ? 'past' : '', next?.id === h.id ? 'upcoming' : ''].join(' ')}>
                        <td className="col-date">{d.toLocaleDateString(undefined, { day: '2-digit', month: 'short' })}</td>
                        <td className="col-day">
                          {d.toLocaleDateString(undefined, { weekday: 'long' })}
                          {isWeekend(d) && <span className="tag">Weekend</span>}
                        </td>
                        <td>
                          <strong>{h.name}</strong>
                          {h.description && <div className="hint">{h.description}</div>}
                        </td>
                        <td className="col-type">
                          <span className={`type-pill ${h.optional ? 'optional' : 'public'}`}>
                            {h.optional ? 'Optional' : 'Public holiday'}
                          </span>
                        </td>
                      </tr>
                    )
                  })}
                </tbody>
              ))}
            </table>
          )}

          <p className="hint note">
            Optional holidays are shown for information; they do not reduce the number of leave days if you apply on that date.
          </p>
        </>
      )}
    </main>
  )
}
