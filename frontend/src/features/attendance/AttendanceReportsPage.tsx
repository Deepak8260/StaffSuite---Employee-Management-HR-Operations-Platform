import { BarChart3, Download, Filter, UserSearch } from 'lucide-react'
import { useEffect, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { getAttendanceSummary } from '../../api/attendance'
import { errorMessage } from '../../api/axiosInstance'
import { listDepartments } from '../../api/organization'
import PageHeader from '../../components/ui/PageHeader'
import SectionCard from '../../components/ui/SectionCard'
import { useReferenceData } from '../../hooks/useReferenceData'
import type { AttendanceSummary } from '../../types/attendance'
import type { Department } from '../../types/organization'
import { toISO } from '../../utils/dates'
import CorrectionsPanel from './CorrectionsPanel'
import SummaryTable from './SummaryTable'

function csvCell(value: string | number): string {
  const s = String(value)
  return /[",\n]/.test(s) ? `"${s.replace(/"/g, '""')}"` : s
}

function downloadCsv(summary: AttendanceSummary) {
  const byEmployee = summary.groupBy === 'EMPLOYEE'
  const header = [byEmployee ? 'Employee' : 'Department', ...(byEmployee ? ['Code'] : ['Employees']),
    'Present', 'Late', 'Half day', 'Absent', 'On leave', 'Hours']
  const line = (r: AttendanceSummary['totals']) => [
    r.label, byEmployee ? (r.employeeCode ?? '') : r.employeeCount,
    r.present, r.late, r.halfDay, r.absent, r.onLeave, r.totalHours.toFixed(2),
  ]
  const csv = [header, ...summary.rows.map(line), line(summary.totals)]
    .map((row) => row.map(csvCell).join(',')).join('\n')
  const url = URL.createObjectURL(new Blob([csv], { type: 'text/csv;charset=utf-8' }))
  const a = document.createElement('a')
  a.href = url
  a.download = `attendance-${summary.from}-to-${summary.to}.csv`
  a.click()
  URL.revokeObjectURL(url)
}

export default function AttendanceReportsPage() {
  const navigate = useNavigate()
  const { people } = useReferenceData()
  const today = new Date()
  const [from, setFrom] = useState(toISO(new Date(today.getFullYear(), today.getMonth(), 1)))
  const [to, setTo] = useState(toISO(today))
  const [groupBy, setGroupBy] = useState<'DEPARTMENT' | 'EMPLOYEE'>('DEPARTMENT')
  const [departmentId, setDepartmentId] = useState('')
  const [departments, setDepartments] = useState<Department[]>([])
  const [summary, setSummary] = useState<AttendanceSummary | null>(null)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    listDepartments().then(setDepartments).catch(() => setDepartments([]))
  }, [])

  // open the detail page on the month the report range starts in
  function openEmployee(id: number) {
    navigate(`/attendance/reports/employee/${id}?month=${from.slice(0, 7)}`)
  }

  useEffect(() => {
    if (!from || !to || to < from) return
    let cancelled = false
    getAttendanceSummary(from, to, groupBy, departmentId ? Number(departmentId) : undefined)
      .then((s) => {
        if (!cancelled) {
          setSummary(s)
          setError(null)
        }
      })
      .catch((err) => {
        if (!cancelled) setError(errorMessage(err, 'Could not load the report'))
      })
    return () => {
      cancelled = true
    }
  }, [from, to, groupBy, departmentId])

  return (
    <main className="page">
      <PageHeader
        title="Attendance reports"
        description="Counts are employee-days. Absent means a working day with no check-in and no approved leave."
        actions={summary && summary.rows.length > 0 ? (
          <button onClick={() => downloadCsv(summary)}><Download size={16} aria-hidden="true" /> Export CSV</button>
        ) : undefined}
      />
      <SectionCard title="Employee lookup" icon={UserSearch} tint="blue">
        <div className="inline-form filters lookup">
          <select value="" onChange={(e) => e.target.value && openEmployee(Number(e.target.value))}>
            <option value="">View an employee&apos;s detailed attendance…</option>
            {people.map((p) => <option key={p.id} value={p.id}>{p.name}</option>)}
          </select>
        </div>
      </SectionCard>
      <SectionCard title="Filters" icon={Filter} tint="green">
      <div className="inline-form filters">
        <label className="check">From <input type="date" value={from} max={to} onChange={(e) => setFrom(e.target.value)} /></label>
        <label className="check">To <input type="date" value={to} min={from} onChange={(e) => setTo(e.target.value)} /></label>
        <select value={groupBy} onChange={(e) => setGroupBy(e.target.value as 'DEPARTMENT' | 'EMPLOYEE')}>
          <option value="DEPARTMENT">By department</option>
          <option value="EMPLOYEE">By employee</option>
        </select>
        <select value={departmentId} onChange={(e) => setDepartmentId(e.target.value)}>
          <option value="">All departments</option>
          {departments.map((d) => <option key={d.id} value={d.id}>{d.name}</option>)}
        </select>
      </div>
      </SectionCard>
      {to < from && <p className="error">The end date cannot be before the start date.</p>}
      {error && <p className="error">{error}</p>}
      <SectionCard title="Summary" icon={BarChart3} tint="green">
      {summary ? (
        <SummaryTable
          summary={summary}
          action={
            summary.groupBy === 'EMPLOYEE'
              ? (row) => row.id !== null && (
                  <button type="button" onClick={() => openEmployee(row.id as number)}>View details</button>
                )
              : undefined
          }
        />
      ) : (
        <p className="hint">Loading…</p>
      )}
      </SectionCard>

      <CorrectionsPanel scope="ALL" />
    </main>
  )
}
