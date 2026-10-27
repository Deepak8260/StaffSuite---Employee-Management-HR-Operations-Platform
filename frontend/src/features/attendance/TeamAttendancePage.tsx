import { useEffect, useState } from 'react'
import { getAttendanceDays, getAttendanceSummary } from '../../api/attendance'
import { errorMessage } from '../../api/axiosInstance'
import AttendanceBadge from '../../components/AttendanceBadge'
import type { AttendanceDay, AttendanceSummary, SummaryRow } from '../../types/attendance'
import { formatDateOnly, fromISO, toISO } from '../../utils/dates'
import { hoursText, timeOf } from '../../utils/time'
import CorrectionsPanel from './CorrectionsPanel'
import SummaryTable from './SummaryTable'

export default function TeamAttendancePage() {
  const today = new Date()
  const [from, setFrom] = useState(toISO(new Date(today.getFullYear(), today.getMonth(), 1)))
  const [to, setTo] = useState(toISO(today))
  const [summary, setSummary] = useState<AttendanceSummary | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [detail, setDetail] = useState<{ row: SummaryRow; days: AttendanceDay[] } | null>(null)

  useEffect(() => {
    if (!from || !to || to < from) return
    let cancelled = false
    getAttendanceSummary(from, to, 'EMPLOYEE')
      .then((s) => {
        if (!cancelled) {
          setSummary(s)
          setError(null)
        }
      })
      .catch((err) => {
        if (!cancelled) setError(errorMessage(err, 'Could not load team attendance'))
      })
    return () => {
      cancelled = true
    }
  }, [from, to])

  async function viewDays(row: SummaryRow) {
    if (row.id === null) return
    try {
      setDetail({ row, days: await getAttendanceDays(row.id, from, to) })
    } catch (err) {
      setError(errorMessage(err, 'Could not load that employee’s attendance'))
    }
  }

  return (
    <main className="page">
      <h1>Team attendance</h1>
      <p className="hint">Your direct reports only. Days with approved leave count as On leave, not Absent.</p>
      <div className="inline-form filters">
        <label className="check">From <input type="date" value={from} max={to} onChange={(e) => setFrom(e.target.value)} /></label>
        <label className="check">To <input type="date" value={to} min={from} onChange={(e) => setTo(e.target.value)} /></label>
      </div>
      {to < from && <p className="error">The end date cannot be before the start date.</p>}
      {error && <p className="error">{error}</p>}
      {summary && (
        <SummaryTable summary={summary} action={(row) => <button type="button" onClick={() => viewDays(row)}>View days</button>} />
      )}

      <CorrectionsPanel scope="TEAM" />

      {detail && (
        <div className="modal-backdrop" role="dialog" aria-modal="true">
          <div className="modal wide-modal">
            <h2>{detail.row.label}</h2>
            <p className="hint">{formatDateOnly(from)} – {formatDateOnly(to)}</p>
            <div className="modal-scroll">
              <table>
                <thead>
                  <tr><th>Date</th><th>Day</th><th>Status</th><th>In</th><th>Out</th><th>Hours</th></tr>
                </thead>
                <tbody>
                  {detail.days.filter((d) => d.status !== null || d.checkInTime).map((d) => (
                    <tr key={d.date}>
                      <td>{formatDateOnly(d.date)}</td>
                      <td>{fromISO(d.date).toLocaleDateString(undefined, { weekday: 'short' })}</td>
                      <td><AttendanceBadge status={d.status} /></td>
                      <td>{timeOf(d.checkInTime)}</td>
                      <td>{timeOf(d.checkOutTime)}</td>
                      <td>{hoursText(d.workingHours)}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
            <div className="actions"><button onClick={() => setDetail(null)}>Close</button></div>
          </div>
        </div>
      )}
    </main>
  )
}
