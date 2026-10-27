import type { ReactNode } from 'react'
import type { AttendanceSummary, SummaryRow } from '../../types/attendance'

interface Props {
  summary: AttendanceSummary
  /** Renders a trailing action cell for a row (e.g. "View days"). */
  action?: (row: SummaryRow) => ReactNode
}

export default function SummaryTable({ summary, action }: Props) {
  const byEmployee = summary.groupBy === 'EMPLOYEE'
  return (
    <table className="summary-table">
      <thead>
        <tr>
          <th>{byEmployee ? 'Employee' : 'Department'}</th>
          {!byEmployee && <th>Employees</th>}
          <th>Present</th><th>Late</th><th>Half day</th><th>Absent</th><th>On leave</th><th>Hours</th>
          {action && <th></th>}
        </tr>
      </thead>
      <tbody>
        {summary.rows.map((r) => (
          <tr key={r.id ?? r.label}>
            <td>{r.label}{r.employeeCode && <><br /><span className="hint">{r.employeeCode}</span></>}</td>
            {!byEmployee && <td>{r.employeeCount}</td>}
            <td>{r.present}</td><td>{r.late}</td><td>{r.halfDay}</td>
            <td className={r.absent > 0 ? 'bad' : undefined}>{r.absent}</td>
            <td>{r.onLeave}</td><td>{r.totalHours.toFixed(2)}</td>
            {action && <td className="actions">{action(r)}</td>}
          </tr>
        ))}
        {summary.rows.length === 0 && <tr><td colSpan={9}>No employees in scope.</td></tr>}
      </tbody>
      {summary.rows.length > 0 && (
        <tfoot>
          <tr>
            <th>Total</th>
            {!byEmployee && <th>{summary.totals.employeeCount}</th>}
            <th>{summary.totals.present}</th><th>{summary.totals.late}</th><th>{summary.totals.halfDay}</th>
            <th>{summary.totals.absent}</th><th>{summary.totals.onLeave}</th><th>{summary.totals.totalHours.toFixed(2)}</th>
            {action && <th></th>}
          </tr>
        </tfoot>
      )}
    </table>
  )
}
