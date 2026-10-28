import { CalendarDays, CheckCircle2, Clock, Hourglass, ListChecks, LogIn, Sun, TimerReset, UserX, Gauge } from 'lucide-react'
import { useEffect, useMemo, useState } from 'react'
import { Link, useNavigate, useParams, useSearchParams } from 'react-router-dom'
import { getAttendanceDays } from '../../api/attendance'
import { errorMessage } from '../../api/axiosInstance'
import { getEmployee } from '../../api/employees'
import AttendanceCalendar from '../../components/AttendanceCalendar'
import DataTable, { type Column } from '../../components/ui/DataTable'
import PageHeader from '../../components/ui/PageHeader'
import SectionCard from '../../components/ui/SectionCard'
import StatCard from '../../components/ui/StatCard'
import { StatusCodePill } from '../../components/ui/StatusPill'
import type { AttendanceDay } from '../../types/attendance'
import type { Employee } from '../../types/employee'
import { formatDateOnly, fromISO, toISO } from '../../utils/dates'
import { hoursText, monthRange, timeOf } from '../../utils/time'

function shiftMonth(month: string, delta: number): string {
  const [y, m] = month.split('-').map(Number)
  const d = new Date(y, m - 1 + delta, 1)
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}`
}

function csvCell(value: string | number): string {
  const s = String(value)
  return /[",\n]/.test(s) ? `"${s.replace(/"/g, '""')}"` : s
}

function minutesOf(dateTime: string): number {
  return Number(dateTime.slice(11, 13)) * 60 + Number(dateTime.slice(14, 16))
}

function clock(minutes: number): string {
  return `${String(Math.floor(minutes / 60)).padStart(2, '0')}:${String(Math.round(minutes % 60)).padStart(2, '0')}`
}

/** HR/Admin: one employee's day-by-day attendance (log-in / log-out times, hours, status) for a month. */
export default function EmployeeAttendanceDetailPage() {
  const { id } = useParams()
  const employeeId = Number(id)
  const navigate = useNavigate()
  const [params, setParams] = useSearchParams()
  const currentMonth = toISO(new Date()).slice(0, 7)
  const month = /^\d{4}-\d{2}$/.test(params.get('month') ?? '') ? (params.get('month') as string) : currentMonth

  const [employee, setEmployee] = useState<Employee | null>(null)
  const [days, setDays] = useState<AttendanceDay[]>([])
  const [selectedDate, setSelectedDate] = useState<string | null>(null)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    getEmployee(employeeId).then(setEmployee).catch((err) => setError(errorMessage(err, 'Could not load the employee')))
  }, [employeeId])

  useEffect(() => {
    let cancelled = false
    const { from, to } = monthRange(month)
    getAttendanceDays(employeeId, from, to)
      .then((d) => {
        if (!cancelled) {
          setDays(d)
          setError(null)
        }
      })
      .catch((err) => {
        if (!cancelled) setError(errorMessage(err, 'Could not load attendance'))
      })
    return () => {
      cancelled = true
    }
  }, [employeeId, month])

  function setMonth(next: string) {
    setSelectedDate(null)
    setParams({ month: next }, { replace: true })
  }

  const todayISO = toISO(new Date())
  const rows = useMemo(
    () => days.filter((d) => d.date <= todayISO && d.dayType !== 'BEFORE_JOINING'),
    [days, todayISO],
  )
  const stats = useMemo(() => {
    let present = 0, late = 0, halfDay = 0, absent = 0, onLeave = 0, hours = 0, inMinutes = 0, inCount = 0
    rows.forEach((d) => {
      if (d.status === 'PRESENT') present++
      else if (d.status === 'LATE') late++
      else if (d.status === 'HALF_DAY') halfDay++
      else if (d.status === 'ABSENT') absent++
      else if (d.status === 'ON_LEAVE') onLeave++
      hours += d.workingHours ?? 0
      if (d.checkInTime) {
        inMinutes += minutesOf(d.checkInTime)
        inCount++
      }
    })
    const worked = present + late + halfDay
    return { present, late, halfDay, absent, onLeave, hours, worked, avgIn: inCount ? clock(inMinutes / inCount) : '—',
      avgHours: worked ? (hours / worked).toFixed(2) : '—' }
  }, [rows])

  function exportCsv() {
    const header = ['Date', 'Day', 'Status', 'Check in', 'Check out', 'Working hours', 'Note']
    const lines = rows.map((d) => [
      d.date,
      fromISO(d.date).toLocaleDateString(undefined, { weekday: 'long' }),
      d.status ?? (d.dayType === 'HOLIDAY' ? 'Holiday' : d.dayType === 'WEEKEND' ? 'Weekend' : ''),
      d.checkInTime ? timeOf(d.checkInTime) : '',
      d.checkOutTime ? timeOf(d.checkOutTime) : '',
      d.workingHours ?? '',
      d.leaveTypeName ?? d.holidayName ?? (d.pendingCorrection ? 'Correction pending' : ''),
    ])
    const csv = [header, ...lines].map((r) => r.map(csvCell).join(',')).join('\n')
    const url = URL.createObjectURL(new Blob([csv], { type: 'text/csv;charset=utf-8' }))
    const a = document.createElement('a')
    a.href = url
    a.download = `attendance-${employee?.employeeCode ?? employeeId}-${month}.csv`
    a.click()
    URL.revokeObjectURL(url)
  }

  const [viewYear, viewMonth] = month.split('-').map(Number)
  const selected = days.find((d) => d.date === selectedDate) ?? null

  const logColumns: Column<AttendanceDay>[] = [
    { key: 'date', header: 'Date', render: (d) => formatDateOnly(d.date) },
    { key: 'day', header: 'Day', render: (d) => fromISO(d.date).toLocaleDateString(undefined, { weekday: 'short' }) },
    {
      key: 'status',
      header: 'Status',
      render: (d) => d.status ? <StatusCodePill status={d.status} /> : (
        <span className="hint">{d.dayType === 'HOLIDAY' ? 'Holiday' : d.dayType === 'WEEKEND' ? 'Weekend' : '—'}</span>
      ),
    },
    { key: 'in', header: 'Check in', render: (d) => timeOf(d.checkInTime) },
    { key: 'out', header: 'Check out', render: (d) => timeOf(d.checkOutTime) },
    { key: 'hours', header: 'Hours', render: (d) => hoursText(d.workingHours) },
    { key: 'note', header: 'Note', render: (d) => <span className="hint">{d.leaveTypeName ?? d.holidayName ?? ''}{d.pendingCorrection ? 'Correction pending' : ''}</span> },
  ]

  return (
    <main className="page">
      <p className="hint no-print"><Link to="/attendance/reports">← Attendance reports</Link></p>
      <PageHeader
        title={employee ? employee.fullName : 'Employee attendance'}
        description={employee ? `${employee.employeeCode} · ${employee.jobTitle}${employee.departmentName ? ` · ${employee.departmentName}` : ''}` : 'Day-by-day attendance for one employee.'}
        actions={
          <div className="actions no-print">
            <input type="month" value={month} max={currentMonth} onChange={(e) => e.target.value && setMonth(e.target.value)} />
            <button onClick={exportCsv} disabled={rows.length === 0}>Export CSV</button>
            <button onClick={() => window.print()}>Print</button>
            <button onClick={() => navigate(-1)}>Back</button>
          </div>
        }
      />
      {error && <p className="error">{error}</p>}

      <div className="stat-grid">
        <StatCard icon={CheckCircle2} tint="green" label="Days worked" value={stats.worked} />
        <StatCard icon={Hourglass} tint="orange" label="Late" value={stats.late} />
        <StatCard icon={TimerReset} tint="orange" label="Half days" value={stats.halfDay} />
        <StatCard icon={UserX} tint="red" label="Absent" value={stats.absent} />
        <StatCard icon={Sun} tint="blue" label="On leave" value={stats.onLeave} />
        <StatCard icon={Clock} tint="green" label="Total hours" value={stats.hours.toFixed(1)} />
        <StatCard icon={LogIn} tint="blue" label="Avg check-in" value={stats.avgIn} />
        <StatCard icon={Gauge} tint="purple" label="Avg hours / day" value={stats.avgHours} />
      </div>

      <SectionCard title="Calendar" icon={CalendarDays} tint="green" className="no-print">
      <div className="att-layout">
        <AttendanceCalendar
          year={viewYear}
          month={viewMonth - 1}
          days={days}
          selected={selectedDate}
          onSelect={(d) => setSelectedDate(d.date === selectedDate ? null : d.date)}
          onPrev={() => setMonth(shiftMonth(month, -1))}
          onNext={() => setMonth(shiftMonth(month, 1))}
          nextDisabled={month >= currentMonth}
        />
        <aside className="day-detail">
          {!selected && <p className="hint">Select a day on the calendar to see its details.</p>}
          {selected && (
            <>
              <h3>{fromISO(selected.date).toLocaleDateString(undefined, { weekday: 'long', day: 'numeric', month: 'long' })}</h3>
              <p>{selected.status ? <StatusCodePill status={selected.status} /> : <span className="hint">—</span>} {selected.leaveTypeName && <span className="hint">{selected.leaveTypeName}</span>}</p>
              {selected.holidayName && <p className="hint">{selected.holidayName}</p>}
              <dl className="details one-col">
                <div><dt>Check in</dt><dd>{timeOf(selected.checkInTime)}</dd></div>
                <div><dt>Check out</dt><dd>{timeOf(selected.checkOutTime)}</dd></div>
                <div><dt>Working hours</dt><dd>{hoursText(selected.workingHours)}</dd></div>
              </dl>
              {selected.pendingCorrection && <p className="hint">A correction request is pending.</p>}
            </>
          )}
        </aside>
      </div>
      </SectionCard>

      <SectionCard title="Daily log" icon={ListChecks} tint="green">
      <DataTable columns={logColumns} rows={[...rows].reverse()} rowKey={(d) => d.date}
        rowClassName={(d) => (d.dayType === 'WEEKEND' || d.dayType === 'HOLIDAY') && !d.checkInTime ? 'muted' : undefined}
        emptyText="No attendance to show for this month." />
      </SectionCard>
    </main>
  )
}
