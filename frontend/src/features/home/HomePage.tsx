import { useEffect, useState } from 'react'
import { Link, Navigate } from 'react-router-dom'
import { errorMessage } from '../../api/axiosInstance'
import { getAttendanceDays, getAttendanceSummary } from '../../api/attendance'
import { getTeam } from '../../api/employees'
import { getBalances, listLeaveRequests } from '../../api/leave'
import { getPendingApprovals } from '../../api/notifications'
import AttendanceBadge from '../../components/AttendanceBadge'
import { useAuth } from '../../hooks/useAuth'
import type { AttendanceDay, SummaryRow } from '../../types/attendance'
import type { LeaveBalance } from '../../types/leave'
import type { PendingApprovals } from '../../types/notifications'
import { toISO } from '../../utils/dates'
import { timeOf } from '../../utils/time'

const NON_WORKING: Record<Exclude<AttendanceDay['dayType'], 'WORKING'>, string> = {
  WEEKEND: 'Weekend',
  HOLIDAY: 'Public holiday',
  BEFORE_JOINING: 'Before your joining date',
}

/** Role-based landing page: HR/Admin go to the HR dashboard; everyone else sees their own summary. */
export default function HomePage() {
  const { user } = useAuth()
  if (user?.role === 'SYSTEM_ADMIN' || user?.role === 'HR_ADMIN') {
    return <Navigate to="/admin/dashboard" replace />
  }
  return (
    <main className="page">
      <h1>{user?.role === 'MANAGER' ? 'Team overview' : 'My dashboard'}</h1>
      {user && <p className="hint">Signed in as {user.email} ({user.role})</p>}
      {user?.role === 'MANAGER' && user.employeeId != null && <TeamOverview employeeId={user.employeeId} />}
      {user?.employeeId != null ? <MySummary employeeId={user.employeeId} /> : (
        <p className="hint">This login is not linked to an employee record, so there is no personal summary to show.</p>
      )}
    </main>
  )
}

interface MyData {
  balances: LeaveBalance[]
  today: AttendanceDay | null
  pendingMine: number
}

function MySummary({ employeeId }: { employeeId: number }) {
  const [data, setData] = useState<MyData | null>(null)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    const today = toISO(new Date())
    Promise.all([
      getBalances(employeeId),
      getAttendanceDays(employeeId, today, today),
      listLeaveRequests({ scope: 'MINE', status: 'PENDING', page: 0, size: 1 }),
    ])
      .then(([balances, days, pending]) => setData({ balances, today: days[0] ?? null, pendingMine: pending.totalElements }))
      .catch((err) => setError(errorMessage(err, 'Could not load your summary')))
  }, [employeeId])

  if (error) return <p className="error">{error}</p>
  if (!data) return <p className="hint">Loading…</p>

  const { balances, today, pendingMine } = data
  return (
    <>
      <h2 className="home-heading">My summary</h2>
      <div className="home-grid">
        <section className="home-card">
          <h2>Leave balance</h2>
          {balances.length === 0 ? (
            <p className="hint">No leave types are set up yet.</p>
          ) : (
            <ul>
              {balances.map((b) => (
                <li key={b.leaveTypeId}>
                  <span>{b.leaveTypeName}</span>
                  <strong>{b.availableDays} day{b.availableDays === 1 ? '' : 's'} available</strong>
                </li>
              ))}
            </ul>
          )}
          <Link className="card-link" to="/leave">Apply for or view leave</Link>
        </section>

        <section className="home-card">
          <h2>Today’s attendance</h2>
          {today === null || today.dayType === 'WORKING' ? (
            <p>
              <AttendanceBadge status={today?.status ?? null} />
              {today?.checkInTime ? ` In ${timeOf(today.checkInTime)}` : ' Not checked in yet'}
              {today?.checkOutTime ? ` · Out ${timeOf(today.checkOutTime)}` : ''}
            </p>
          ) : (
            <p>{NON_WORKING[today.dayType]}{today.dayType === 'HOLIDAY' && today.holidayName ? `: ${today.holidayName}` : ''}</p>
          )}
          <Link className="card-link" to="/attendance">Check in / out and calendar</Link>
        </section>

        <section className="home-card">
          <h2>My pending leave requests</h2>
          <p className="big">{pendingMine}</p>
          <Link className="card-link" to="/leave">View my requests</Link>
        </section>
      </div>
    </>
  )
}

interface TeamData {
  size: number
  pending: PendingApprovals
  today: SummaryRow
}

function TeamOverview({ employeeId }: { employeeId: number }) {
  const [data, setData] = useState<TeamData | null>(null)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    const today = toISO(new Date())
    Promise.all([getTeam(employeeId, 0, 1), getPendingApprovals(), getAttendanceSummary(today, today, 'DEPARTMENT')])
      .then(([team, pending, summary]) => setData({ size: team.totalElements, pending, today: summary.totals }))
      .catch((err) => setError(errorMessage(err, 'Could not load the team overview')))
  }, [employeeId])

  if (error) return <p className="error">{error}</p>
  if (!data) return <p className="hint">Loading…</p>

  const { size, pending, today } = data
  return (
    <div className="home-grid">
      <section className="home-card">
        <h2>My team</h2>
        <p className="big">{size}</p>
        <p className="hint">direct report{size === 1 ? '' : 's'}</p>
        <Link className="card-link" to="/employees">View team</Link>
      </section>

      <section className="home-card">
        <h2>Waiting for your approval</h2>
        <p className="big">{pending.total}</p>
        <ul>
          <li><span>Leave requests</span><Link to="/leave/team">{pending.leaveRequests}</Link></li>
          <li><span>Attendance corrections</span><Link to="/attendance/team">{pending.attendanceCorrections}</Link></li>
        </ul>
      </section>

      <section className="home-card">
        <h2>Team today</h2>
        <ul>
          <li><span>Present</span><strong>{today.present}</strong></li>
          <li><span>Late</span><strong>{today.late}</strong></li>
          <li><span>Half day</span><strong>{today.halfDay}</strong></li>
          <li><span>On leave</span><strong>{today.onLeave}</strong></li>
        </ul>
        <Link className="card-link" to="/attendance/team">Team attendance</Link>
      </section>
    </div>
  )
}
