import { CalendarDays, CheckCircle2, Clock, ClipboardEdit, Hourglass, Sun, TimerReset, UserX } from 'lucide-react'
import Flash from '../../components/Flash'
import { useCallback, useEffect, useMemo, useState } from 'react'
import { useSearchParams } from 'react-router-dom'
import { checkIn, checkOut, getAttendanceDays, listCorrections } from '../../api/attendance'
import { errorMessage } from '../../api/axiosInstance'
import AttendanceCalendar from '../../components/AttendanceCalendar'
import Pager from '../../components/Pager'
import DataTable, { type Column } from '../../components/ui/DataTable'
import PageHeader from '../../components/ui/PageHeader'
import SectionCard from '../../components/ui/SectionCard'
import StatCard from '../../components/ui/StatCard'
import { StatusCodePill } from '../../components/ui/StatusPill'
import { useAuth } from '../../hooks/useAuth'
import type { PageResponse } from '../../types/auth'
import type { AttendanceDay, AttendanceStatus, Correction } from '../../types/attendance'
import { addDays, formatDateOnly, fromISO, toISO } from '../../utils/dates'
import { hoursText, monthRange, timeOf } from '../../utils/time'
import CorrectionModal from './CorrectionModal'

const CORRECTION_WINDOW_DAYS = 30
const PAGE_SIZE = 5

function StatusCell({ status }: { status: AttendanceStatus | null }) {
  return status ? <StatusCodePill status={status} /> : <span className="hint">—</span>
}

function shiftMonth(month: string, delta: number): string {
  const [y, m] = month.split('-').map(Number)
  const d = new Date(y, m - 1 + delta, 1)
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}`
}

export default function MyAttendancePage() {
  const { user } = useAuth()
  const employeeId = user?.employeeId ?? null
  const todayISO = toISO(new Date())
  const currentMonth = todayISO.slice(0, 7)
  // arriving from a notification (?correction=ID): that request is highlighted in the list
  const [params] = useSearchParams()
  const highlight = Number(params.get('correction')) || null

  const [today, setToday] = useState<AttendanceDay | null>(null)
  const [month, setMonth] = useState(currentMonth)
  const [view, setView] = useState<'calendar' | 'list'>('calendar')
  const [days, setDays] = useState<AttendanceDay[]>([])
  const [selectedDate, setSelectedDate] = useState<string | null>(null)
  const [requests, setRequests] = useState<PageResponse<Correction> | null>(null)
  const [requestPage, setRequestPage] = useState(0)
  const [correcting, setCorrecting] = useState<AttendanceDay | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [notice, setNotice] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)

  const loadToday = useCallback(async () => {
    if (employeeId === null) return
    try {
      setToday((await getAttendanceDays(employeeId, todayISO, todayISO))[0])
    } catch (err) {
      setError(errorMessage(err, 'Could not load today’s attendance'))
    }
  }, [employeeId, todayISO])

  const loadMonth = useCallback(async () => {
    if (employeeId === null) return
    try {
      const { from, to } = monthRange(month)
      setDays(await getAttendanceDays(employeeId, from, to))
    } catch (err) {
      setError(errorMessage(err, 'Could not load your attendance'))
    }
  }, [employeeId, month])

  const loadRequests = useCallback(async () => {
    if (employeeId === null) return
    try {
      setRequests(await listCorrections({ scope: 'MINE', page: requestPage, size: PAGE_SIZE }))
    } catch (err) {
      setError(errorMessage(err, 'Could not load your correction requests'))
    }
  }, [employeeId, requestPage])

  useEffect(() => {
    // eslint-disable-next-line react-hooks/set-state-in-effect
    void loadToday()
  }, [loadToday])
  useEffect(() => {
    // eslint-disable-next-line react-hooks/set-state-in-effect
    void loadMonth()
  }, [loadMonth])
  useEffect(() => {
    // eslint-disable-next-line react-hooks/set-state-in-effect
    void loadRequests()
  }, [loadRequests])

  async function punch(action: 'in' | 'out') {
    setError(null)
    setNotice(null)
    setBusy(true)
    try {
      const result = action === 'in' ? await checkIn() : await checkOut()
      setNotice(action === 'in' ? `Checked in at ${timeOf(result.checkInTime)}.` : `Checked out at ${timeOf(result.checkOutTime)}.`)
      await Promise.all([loadToday(), loadMonth()])
    } catch (err) {
      setError(errorMessage(err, 'Could not record your attendance'))
    } finally {
      setBusy(false)
    }
  }

  // days before joining, and days that have not happened yet, are not shown in the list
  const shownDays = useMemo(
    () => days.filter((d) => d.date <= todayISO && d.dayType !== 'BEFORE_JOINING').reverse(),
    [days, todayISO],
  )
  const counts = useMemo(() => {
    const c = { present: 0, late: 0, halfDay: 0, absent: 0, onLeave: 0, hours: 0 }
    days.forEach((d) => {
      if (d.date > todayISO) return
      if (d.status === 'PRESENT') c.present++
      else if (d.status === 'LATE') c.late++
      else if (d.status === 'HALF_DAY') c.halfDay++
      else if (d.status === 'ABSENT') c.absent++
      else if (d.status === 'ON_LEAVE') c.onLeave++
      c.hours += d.workingHours ?? 0
    })
    return c
  }, [days, todayISO])

  // The backend decides, but only offer the button where a request could be accepted.
  const windowStart = toISO(addDays(fromISO(todayISO), -CORRECTION_WINDOW_DAYS))
  const canCorrect = (d: AttendanceDay) =>
    d.date <= todayISO && d.date >= windowStart && d.status !== 'ON_LEAVE' && !d.pendingCorrection &&
    (d.dayType === 'WORKING' || d.checkInTime !== null)

  if (employeeId === null) {
    return (
      <main className="page">
        <PageHeader title="My attendance" />
        <p>Your login is not linked to an employee record, so there is no attendance to record.</p>
      </main>
    )
  }

  const checkedIn = !!today?.checkInTime
  const checkedOut = !!today?.checkOutTime
  const onLeaveToday = today?.status === 'ON_LEAVE'
  const [viewYear, viewMonth] = month.split('-').map(Number)
  const selected = days.find((d) => d.date === selectedDate) ?? null

  const dayColumns: Column<AttendanceDay>[] = [
    { key: 'date', header: 'Date', render: (d) => formatDateOnly(d.date) },
    { key: 'day', header: 'Day', render: (d) => fromISO(d.date).toLocaleDateString(undefined, { weekday: 'short' }) },
    {
      key: 'status',
      header: 'Status',
      render: (d) => (
        <>
          {d.status ? <StatusCodePill status={d.status} /> : (
            <span className="hint">{d.dayType === 'HOLIDAY' ? (d.holidayName ?? 'Holiday') : d.dayType === 'WEEKEND' ? 'Weekend' : '—'}</span>
          )}
          {d.status === 'ON_LEAVE' && d.leaveTypeName && <span className="hint"> {d.leaveTypeName}</span>}
        </>
      ),
    },
    { key: 'in', header: 'Check in', render: (d) => timeOf(d.checkInTime) },
    { key: 'out', header: 'Check out', render: (d) => timeOf(d.checkOutTime) },
    { key: 'hours', header: 'Hours', render: (d) => hoursText(d.workingHours) },
    {
      key: 'actions',
      header: '',
      align: 'right',
      className: 'row-actions',
      render: (d) => (
        <>
          {d.pendingCorrection && <span className="hint">Correction pending</span>}
          {canCorrect(d) && <button type="button" onClick={() => setCorrecting(d)}>Request correction</button>}
        </>
      ),
    },
  ]

  const requestColumns: Column<Correction>[] = [
    { key: 'date', header: 'Date', render: (c) => formatDateOnly(c.attendanceDate) },
    {
      key: 'requested',
      header: 'Requested',
      render: (c) => (
        <>
          {c.requestedCheckIn ? `In ${timeOf(c.requestedCheckIn)}` : ''}
          {c.requestedCheckIn && c.requestedCheckOut ? ' · ' : ''}
          {c.requestedCheckOut ? `Out ${timeOf(c.requestedCheckOut)}` : ''}
        </>
      ),
    },
    { key: 'reason', header: 'Reason', render: (c) => c.reason },
    { key: 'status', header: 'Status', render: (c) => <StatusCodePill status={c.status} /> },
    {
      key: 'decision',
      header: 'Decision',
      render: (c) => <>{c.reviewedByName ? `${c.reviewedByName}: ` : ''}{c.reviewComment ?? (c.status === 'PENDING' ? 'Awaiting decision' : '—')}</>,
    },
  ]

  return (
    <main className="page">
      <PageHeader title="My attendance" description="Check in and out, and review your attendance and correction requests." />
      {error && <p className="error">{error}</p>}
      <Flash message={notice} onDone={() => setNotice(null)} />

      <SectionCard title="Today" icon={Clock} tint="green" className="today-section">
      <div className="today-card">
        <div>
          <div className="today-date">{new Date().toLocaleDateString(undefined, { weekday: 'long', day: 'numeric', month: 'long', year: 'numeric' })}</div>
          <div className="today-status">
            {onLeaveToday && <>You are on approved leave today ({today?.leaveTypeName}).</>}
            {!onLeaveToday && !checkedIn && <>You have not checked in yet.</>}
            {checkedIn && !checkedOut && <>Checked in at <strong>{timeOf(today?.checkInTime)}</strong> <StatusCell status={today?.status ?? null} /></>}
            {checkedOut && (
              <>
                {timeOf(today?.checkInTime)} – {timeOf(today?.checkOutTime)} · <strong>{hoursText(today?.workingHours)}</strong>{' '}
                <StatusCell status={today?.status ?? null} />
              </>
            )}
          </div>
        </div>
        <div className="actions">
          <button disabled={busy || checkedIn || onLeaveToday} onClick={() => punch('in')}>Check in</button>
          <button disabled={busy || !checkedIn || checkedOut} onClick={() => punch('out')}>Check out</button>
        </div>
      </div>
      </SectionCard>

      <div className="stat-grid">
        <StatCard icon={CheckCircle2} tint="green" label="Days worked" value={counts.present + counts.late + counts.halfDay} />
        <StatCard icon={Hourglass} tint="orange" label="Late" value={counts.late} />
        <StatCard icon={TimerReset} tint="orange" label="Half days" value={counts.halfDay} />
        <StatCard icon={UserX} tint="red" label="Absent" value={counts.absent} />
        <StatCard icon={Sun} tint="blue" label="On leave" value={counts.onLeave} />
        <StatCard icon={Clock} tint="green" label="Hours" value={counts.hours.toFixed(1)} />
      </div>

      <SectionCard
        title="Attendance history"
        icon={CalendarDays}
        tint="green"
        actions={
          <div className="actions">
            <div className="segmented" role="group" aria-label="View">
              <button type="button" className={view === 'calendar' ? 'on' : ''} onClick={() => setView('calendar')}>Calendar</button>
              <button type="button" className={view === 'list' ? 'on' : ''} onClick={() => setView('list')}>List</button>
            </div>
            <input type="month" value={month} max={currentMonth} onChange={(e) => e.target.value && setMonth(e.target.value)} />
          </div>
        }
      >

        {view === 'calendar' && (
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
                  <p><StatusCell status={selected.status} /> {selected.leaveTypeName && <span className="hint">{selected.leaveTypeName}</span>}</p>
                  {selected.holidayName && <p className="hint">{selected.holidayName}</p>}
                  <dl className="details one-col">
                    <div><dt>Check in</dt><dd>{timeOf(selected.checkInTime)}</dd></div>
                    <div><dt>Check out</dt><dd>{timeOf(selected.checkOutTime)}</dd></div>
                    <div><dt>Working hours</dt><dd>{hoursText(selected.workingHours)}</dd></div>
                  </dl>
                  {selected.pendingCorrection && <p className="hint">A correction request is pending.</p>}
                  {canCorrect(selected) && <button type="button" onClick={() => setCorrecting(selected)}>Request correction</button>}
                </>
              )}
            </aside>
          </div>
        )}

        {view === 'list' && <DataTable columns={dayColumns} rows={shownDays} rowKey={(d) => d.date} rowClassName={(d) => (d.dayType === 'WEEKEND' || d.dayType === 'HOLIDAY') && !d.checkInTime ? 'muted' : undefined} emptyText="Nothing to show for this month." />}
      </SectionCard>

      <SectionCard title="My correction requests" icon={ClipboardEdit} tint="orange">
        <DataTable columns={requestColumns} rows={requests ? requests.content : null} rowKey={(c) => c.id}
          rowClassName={(c) => (c.id === highlight ? 'highlight' : undefined)} emptyText="No correction requests." />
        {requests && <Pager data={requests} onPage={setRequestPage} noun="requests" />}
      </SectionCard>

      {correcting && (
        <CorrectionModal
          day={correcting}
          onClose={() => setCorrecting(null)}
          onDone={() => {
            setCorrecting(null)
            setNotice('Correction request submitted. Your manager will review it.')
            void Promise.all([loadMonth(), loadRequests()])
          }}
        />
      )}
    </main>
  )
}
