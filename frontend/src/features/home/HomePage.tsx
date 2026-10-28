import {
  CalendarCheck, CalendarDays, CalendarOff, ClipboardList, FileText, FileWarning, Plane, UserPlus, Users, Hourglass, Upload,
} from 'lucide-react'
import { useEffect, useState } from 'react'
import { errorMessage } from '../../api/axiosInstance'
import { getAttendanceDays, getAttendanceSummary } from '../../api/attendance'
import { getTeam } from '../../api/employees'
import { getBalances, listLeaveRequests } from '../../api/leave'
import { listExpiringDocuments } from '../../api/documents'
import { getPendingApprovals } from '../../api/notifications'
import { getHeadcount, getOnboarding } from '../../api/reports'
import AttendanceBadge from '../../components/AttendanceBadge'
import AvatarInitials from '../../components/ui/AvatarInitials'
import DataTable, { type Column } from '../../components/ui/DataTable'
import QuickActionButton from '../../components/ui/QuickActionButton'
import SectionCard from '../../components/ui/SectionCard'
import StatCard from '../../components/ui/StatCard'
import StatusPill, { StatusCodePill } from '../../components/ui/StatusPill'
import { useAuth } from '../../hooks/useAuth'
import { useDisplayName } from '../../hooks/useDisplayName'
import type { AttendanceDay, SummaryRow } from '../../types/attendance'
import type { EmployeeDocument } from '../../types/documents'
import type { LeaveRequest } from '../../types/leave'
import type { PendingApprovals } from '../../types/notifications'
import { formatDateOnly, toISO } from '../../utils/dates'
import { getConfig } from '../../utils/config'
import { canManageEmployees } from '../../utils/roles'
import { timeOf } from '../../utils/time'
import StoredPdfViewer from '../documents/StoredPdfViewer'

function greeting(hour: number): string {
  if (hour < 12) return 'Good morning'
  if (hour < 17) return 'Good afternoon'
  return 'Good evening'
}

const NON_WORKING: Record<Exclude<AttendanceDay['dayType'], 'WORKING'>, string> = {
  WEEKEND: 'Weekend',
  HOLIDAY: 'Public holiday',
  BEFORE_JOINING: 'Before your joining date',
}

/** Landing page for every role: greeting and banner on top, then the cards that role is allowed to see. */
export default function HomePage() {
  const { user } = useAuth()
  const { firstName } = useDisplayName(user)
  const now = new Date()
  const envLabel = getConfig().APP_ENV_LABEL
  const subtitle = canManageEmployees(user?.role)
    ? 'Here is what is happening across the company today.'
    : user?.role === 'MANAGER'
      ? 'Here is your team at a glance.'
      : 'Here is a summary of your leave and attendance.'

  return (
    <main className="page">
      <div className="hero">
        <div className="greeting">
          <h1>{greeting(now.getHours())}, {firstName}</h1>
          <p className="page-desc">{subtitle}</p>
        </div>
        <div className="hero-right">
          <span className="today-chip">
            <CalendarDays size={16} aria-hidden="true" />
            {now.toLocaleDateString(undefined, { weekday: 'long', day: 'numeric', month: 'long', year: 'numeric' })}
          </span>
          <div className="banner">
            <strong>StaffSuite</strong>
            <p>Employee Management &amp; HR Operations Platform</p>
            {envLabel && <span className="chip">{envLabel}</span>}
          </div>
        </div>
      </div>

      {canManageEmployees(user?.role) && <AdminHome />}
      {user?.role === 'MANAGER' && user.employeeId != null && <ManagerHome employeeId={user.employeeId} />}
      {!canManageEmployees(user?.role) && (user?.employeeId != null ? <MySummary employeeId={user.employeeId} /> : (
        <p className="hint">This login is not linked to an employee record, so there is no personal summary to show.</p>
      ))}
    </main>
  )
}

// ---------------------------------------------------------------------------------------------- shared cards

function QuickActions() {
  const { user } = useAuth()
  const role = user?.role
  const hasProfile = user?.employeeId != null
  const manage = canManageEmployees(role)
  const leaveTo = manage ? '/leave/all' : role === 'MANAGER' ? '/leave/team' : '/leave'
  const uploadTo = hasProfile ? '/documents' : '/admin/documents'
  return (
    <SectionCard title="Quick actions" icon={ClipboardList} tint="blue">
      <div className="quick-grid">
        {manage && <QuickActionButton to="/employees/new" icon={UserPlus} tint="blue" label="Add employee" />}
        {hasProfile && <QuickActionButton to="/attendance" icon={CalendarCheck} tint="green" label="Mark attendance" />}
        {(hasProfile || manage) && <QuickActionButton to={uploadTo} icon={Upload} tint="purple" label="Upload document" />}
        <QuickActionButton to={leaveTo} icon={Plane} tint="orange" label={manage || role === 'MANAGER' ? 'Manage leave' : 'Apply for leave'} />
      </div>
    </SectionCard>
  )
}

function dateRange(l: LeaveRequest): string {
  return l.startDate === l.endDate ? formatDateOnly(l.startDate) : `${formatDateOnly(l.startDate)} – ${formatDateOnly(l.endDate)}`
}

function LeaveRequestsCard({ requests, viewAllTo, error }: { requests: LeaveRequest[] | null; viewAllTo: string; error?: string | null }) {
  return (
    <SectionCard title="Upcoming leave requests" icon={Plane} tint="orange" viewAllTo={viewAllTo}>
      {error ? <p className="error">{error}</p>
        : requests === null ? <p className="hint">Loading…</p>
        : requests.length === 0 ? <p className="hint">No leave requests yet.</p>
        : (
          <ul className="leave-list">
            {requests.map((l) => (
              <li key={l.id}>
                <AvatarInitials name={l.employeeName} />
                <span className="grow">
                  <span className="person-name">{l.employeeName}</span>
                  <span className="person-meta">{l.leaveTypeName} · {dateRange(l)}</span>
                </span>
                <StatusCodePill status={l.status} />
              </li>
            ))}
          </ul>
        )}
    </SectionCard>
  )
}

// ---------------------------------------------------------------------------------------------- HR / Admin

interface AdminData {
  total: number | null
  hiredThisMonth: number | null
  today: SummaryRow | null
  documents: EmployeeDocument[] | null
  leave: LeaveRequest[] | null
}

function AdminHome() {
  const [data, setData] = useState<AdminData | null>(null)
  const [viewing, setViewing] = useState<EmployeeDocument | null>(null)

  useEffect(() => {
    const now = new Date()
    const today = toISO(now)
    const monthStart = toISO(new Date(now.getFullYear(), now.getMonth(), 1))
    // each card loads independently: one failing report must not blank the whole page
    Promise.allSettled([
      getHeadcount(),
      getAttendanceSummary(today, today, 'DEPARTMENT'),
      getOnboarding({ from: monthStart, to: today }),
      listExpiringDocuments(undefined, true),
      listLeaveRequests({ scope: 'ALL', page: 0, size: 5 }),
    ]).then(([head, summary, hires, docs, leave]) => setData({
      total: head.status === 'fulfilled' ? head.value.total : null,
      hiredThisMonth: hires.status === 'fulfilled' ? hires.value.totalHires : null,
      today: summary.status === 'fulfilled' ? summary.value.totals : null,
      documents: docs.status === 'fulfilled' ? docs.value : null,
      leave: leave.status === 'fulfilled' ? leave.value.content : null,
    }))
  }, [])

  const t = data?.today ?? null
  const counted = t ? t.present + t.late + t.halfDay + t.absent + t.onLeave : 0
  const presentToday = t ? t.present + t.late + t.halfDay : null
  const expiredDocs = data?.documents?.filter((d) => d.expired).length ?? 0
  const pct = (n: number, of: number) => (of > 0 ? `${Math.round((n / of) * 100)}%` : '0%')

  const columns: Column<EmployeeDocument>[] = [
    { key: 'emp', header: 'Employee', render: (d) => (
      <span className="person"><AvatarInitials name={d.employeeName} size="sm" /><span className="person-name">{d.employeeName}</span></span>
    ) },
    { key: 'doc', header: 'Document', render: (d) => d.fileName },
    { key: 'type', header: 'Type', render: (d) => d.categoryName },
    { key: 'status', header: 'Status', render: (d) => d.expired
      ? <StatusPill tone="danger">Expired</StatusPill>
      : <StatusPill tone="warning">Expiring soon{d.daysRemaining != null ? ` · ${d.daysRemaining}d` : ''}</StatusPill> },
    { key: 'uploaded', header: 'Uploaded on', render: (d) => formatDateOnly(d.uploadedAt) },
    { key: 'actions', header: 'Actions', align: 'right', render: (d) => <button type="button" onClick={() => setViewing(d)}>View</button> },
  ]

  return (
    <>
      <div className="stat-grid">
        <StatCard icon={Users} tint="blue" label="Total employees" value={data?.total ?? '—'}
          trend={data?.hiredThisMonth != null ? `${data.hiredThisMonth} joined this month` : undefined} />
        <StatCard icon={CalendarCheck} tint="green" label="Present today" value={presentToday ?? '—'}
          trend={t ? (counted === 0 ? 'Weekend or holiday: no attendance expected' : `${pct(presentToday ?? 0, t.employeeCount)} attendance rate`) : undefined} />
        <StatCard icon={CalendarOff} tint="orange" label="On leave today" value={t?.onLeave ?? '—'}
          trend={t ? `${pct(t.onLeave, t.employeeCount)} of total employees` : undefined} />
        <StatCard icon={FileWarning} tint="purple" label="Pending documents" value={data?.documents?.length ?? '—'}
          trend={data?.documents ? `${expiredDocs} expired, ${data.documents.length - expiredDocs} expiring soon` : undefined} />
      </div>

      <div className="home-columns">
        <SectionCard title="Documents needing attention" icon={FileText} tint="purple" viewAllTo="/admin/documents/expiring">
          <DataTable columns={columns} rows={data ? (data.documents?.slice(0, 5) ?? []) : null} rowKey={(d) => d.id}
            emptyText="No documents are expired or expiring soon." />
        </SectionCard>
        <div>
          <QuickActions />
        </div>
      </div>
      <LeaveRequestsCard requests={data?.leave ?? null} viewAllTo="/leave/all" error={data && data.leave === null ? 'Could not load leave requests' : null} />
      {viewing && <StoredPdfViewer document={viewing} onClose={() => setViewing(null)} />}
    </>
  )
}

// ---------------------------------------------------------------------------------------------- manager

interface TeamData {
  size: number
  pending: PendingApprovals
  today: SummaryRow
  leave: LeaveRequest[]
}

function ManagerHome({ employeeId }: { employeeId: number }) {
  const [data, setData] = useState<TeamData | null>(null)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    const today = toISO(new Date())
    Promise.all([
      getTeam(employeeId, 0, 1), getPendingApprovals(), getAttendanceSummary(today, today, 'DEPARTMENT'),
      listLeaveRequests({ scope: 'TEAM', page: 0, size: 5 }),
    ])
      .then(([team, pending, summary, leave]) => setData({ size: team.totalElements, pending, today: summary.totals, leave: leave.content }))
      .catch((err) => setError(errorMessage(err, 'Could not load the team overview')))
  }, [employeeId])

  if (error) return <p className="error">{error}</p>
  if (!data) return <p className="hint">Loading…</p>
  const { size, pending, today, leave } = data
  return (
    <>
      <div className="stat-grid">
        <StatCard icon={Users} tint="blue" label="My team" value={size} trend={`direct report${size === 1 ? '' : 's'}`} />
        <StatCard icon={Hourglass} tint="orange" label="Waiting for approval" value={pending.total}
          trend={`${pending.leaveRequests} leave, ${pending.attendanceCorrections} attendance`} />
        <StatCard icon={CalendarCheck} tint="green" label="Team present today" value={today.present + today.late + today.halfDay}
          trend={`${today.late} late`} />
        <StatCard icon={CalendarOff} tint="purple" label="Team on leave today" value={today.onLeave} />
      </div>
      <div className="home-columns">
        <LeaveRequestsCard requests={leave} viewAllTo="/leave/team" />
        <QuickActions />
      </div>
    </>
  )
}

// ---------------------------------------------------------------------------------------------- my own summary

interface MyData {
  availableDays: number
  today: AttendanceDay | null
  pendingMine: number
  mine: LeaveRequest[]
}

function MySummary({ employeeId }: { employeeId: number }) {
  const { user } = useAuth()
  const [data, setData] = useState<MyData | null>(null)
  const [error, setError] = useState<string | null>(null)
  const manager = user?.role === 'MANAGER'

  useEffect(() => {
    const today = toISO(new Date())
    Promise.all([
      getBalances(employeeId),
      getAttendanceDays(employeeId, today, today),
      listLeaveRequests({ scope: 'MINE', page: 0, size: 5 }),
      listLeaveRequests({ scope: 'MINE', status: 'PENDING', page: 0, size: 1 }),
    ])
      .then(([balances, days, mine, pending]) => setData({
        availableDays: balances.reduce((sum, b) => sum + b.availableDays, 0),
        today: days[0] ?? null, pendingMine: pending.totalElements, mine: mine.content,
      }))
      .catch((err) => setError(errorMessage(err, 'Could not load your summary')))
  }, [employeeId])

  if (error) return <p className="error">{error}</p>
  if (!data) return <p className="hint">Loading…</p>
  const { availableDays, today, pendingMine, mine } = data
  const working = today === null || today.dayType === 'WORKING'
  const attendanceValue = today === null || today.dayType === 'WORKING'
    ? (today?.checkInTime ? `In ${timeOf(today.checkInTime)}` : 'Not in yet')
    : NON_WORKING[today.dayType]
  return (
    <>
      {manager && <h2 className="home-heading">My summary</h2>}
      <div className="stat-grid">
        <StatCard icon={Plane} tint="orange" label="Leave available" value={availableDays} trend="days across all leave types" />
        <StatCard icon={CalendarCheck} tint="green" label="Today’s attendance"
          value={attendanceValue}
          trend={working && today?.checkOutTime ? `Out ${timeOf(today.checkOutTime)}` : undefined} />
        <StatCard icon={Hourglass} tint="blue" label="Pending leave requests" value={pendingMine} />
        {working && today?.status && (
          <div className="stat-card"><AttendanceBadge status={today.status} /></div>
        )}
      </div>
      <div className="home-columns">
        <LeaveRequestsCard requests={mine} viewAllTo="/leave" />
        {!manager && <QuickActions />}
      </div>
    </>
  )
}
