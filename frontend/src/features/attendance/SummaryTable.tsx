import EmployeeLink from '../../components/ui/EmployeeLink'
import type { ReactNode } from 'react'
import type { AttendanceSummary, SummaryRow } from '../../types/attendance'
import AvatarInitials from '../../components/ui/AvatarInitials'
import DataTable, { type Column } from '../../components/ui/DataTable'

interface Props {
  summary: AttendanceSummary
  /** Renders a trailing action cell for a row (e.g. "View days"). */
  action?: (row: SummaryRow) => ReactNode
}

interface Line {
  row: SummaryRow
  total: boolean
}

export default function SummaryTable({ summary, action }: Props) {
  const byEmployee = summary.groupBy === 'EMPLOYEE'
  const lines: Line[] = summary.rows.map((row) => ({ row, total: false }))
  if (summary.rows.length > 0) lines.push({ row: summary.totals, total: true })

  const columns: Column<Line>[] = [
    {
      key: 'label',
      header: byEmployee ? 'Employee' : 'Department',
      render: ({ row, total }) =>
        total ? <strong>Total</strong> : byEmployee ? (
          <div className="person">
            <AvatarInitials name={row.label} size="sm" />
            <div className="person-text">
              <EmployeeLink id={row.id} className="person-name">{row.label}</EmployeeLink>
              {row.employeeCode && <span className="person-meta">{row.employeeCode}</span>}
            </div>
          </div>
        ) : (
          <>
            <span className="person-name">{row.label}</span>
            {row.employeeCode && <div className="person-meta">{row.employeeCode}</div>}
          </>
        ),
    },
    ...(byEmployee ? [] : [{ key: 'employees', header: 'Employees', render: ({ row }: Line) => row.employeeCount }]),
    { key: 'present', header: 'Present', render: ({ row }) => row.present },
    { key: 'late', header: 'Late', render: ({ row }) => row.late },
    { key: 'half', header: 'Half day', render: ({ row }) => row.halfDay },
    { key: 'absent', header: 'Absent', render: ({ row }) => <span className={row.absent > 0 ? 'num-bad' : undefined}>{row.absent}</span> },
    { key: 'leave', header: 'On leave', render: ({ row }) => row.onLeave },
    { key: 'hours', header: 'Hours', render: ({ row }) => row.totalHours.toFixed(2) },
    ...(action
      ? [{ key: 'actions', header: '', align: 'right' as const, className: 'row-actions', render: ({ row, total }: Line) => (total ? null : action(row)) }]
      : []),
  ]

  return (
    <DataTable
      columns={columns}
      rows={lines}
      rowKey={({ row, total }) => (total ? 'total' : (row.id ?? row.label))}
      rowClassName={({ total }) => (total ? 'total-row' : undefined)}
      emptyText="No employees in scope."
    />
  )
}
