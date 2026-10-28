import { CalendarDays, CalendarPlus, History } from 'lucide-react'
import Flash from '../../components/Flash'
import { useCallback, useEffect, useMemo, useState, type FormEvent } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import { errorMessage } from '../../api/axiosInstance'
import {
  applyForLeave,
  cancelLeave,
  getBalances,
  listLeaveRequests,
  listLeaveTypes,
  openAttachment,
  uploadAttachment,
} from '../../api/leave'
import MonthGrid from '../../components/MonthGrid'
import Pager from '../../components/Pager'
import DataTable, { type Column } from '../../components/ui/DataTable'
import PageHeader from '../../components/ui/PageHeader'
import SectionCard from '../../components/ui/SectionCard'
import StatCard from '../../components/ui/StatCard'
import { StatusCodePill } from '../../components/ui/StatusPill'
import { useAuth } from '../../hooks/useAuth'
import { useHolidays } from '../../hooks/useHolidays'
import type { PageResponse } from '../../types/auth'
import {
  LEAVE_STATUSES,
  type LeaveBalance,
  type LeaveRequest,
  type LeaveStatus,
  type LeaveType,
} from '../../types/leave'
import { countWorkingDays, formatDateOnly, toISO } from '../../utils/dates'
import { canManageEmployees } from '../../utils/roles'

const PAGE_SIZE = 8
const MAX_FILE_BYTES = 5 * 1024 * 1024

export default function MyLeavePage() {
  const { user } = useAuth()
  const employeeId = user?.employeeId ?? null
  const today = new Date()
  // arriving from a notification (?request=ID): that request is highlighted in the history
  const [params] = useSearchParams()
  const highlight = Number(params.get('request')) || null

  const [types, setTypes] = useState<LeaveType[]>([])
  const [typesLoaded, setTypesLoaded] = useState(false)
  const [typesError, setTypesError] = useState<string | null>(null)
  const [balances, setBalances] = useState<LeaveBalance[]>([])
  const [history, setHistory] = useState<PageResponse<LeaveRequest> | null>(null)
  const [statusFilter, setStatusFilter] = useState<LeaveStatus | ''>('')
  const [page, setPage] = useState(0)

  const [view, setView] = useState({ year: today.getFullYear(), month: today.getMonth() })
  const [typeId, setTypeId] = useState('')
  const [start, setStart] = useState('')
  const [end, setEnd] = useState('')
  const [reason, setReason] = useState('')
  const [file, setFile] = useState<File | null>(null)
  const [fileKey, setFileKey] = useState(0)

  const [error, setError] = useState<string | null>(null)
  const [notice, setNotice] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)

  const holidays = useHolidays([view.year, ...(start ? [Number(start.slice(0, 4))] : [])])
  const year = today.getFullYear()

  const refreshBalances = useCallback(async () => {
    if (employeeId === null) return
    try {
      setBalances(await getBalances(employeeId, year))
    } catch (err) {
      setError(errorMessage(err, 'Could not load balances'))
    }
  }, [employeeId, year])

  const refreshHistory = useCallback(async () => {
    if (employeeId === null) return
    try {
      setHistory(await listLeaveRequests({ scope: 'MINE', status: statusFilter || undefined, page, size: PAGE_SIZE }))
    } catch (err) {
      setError(errorMessage(err, 'Could not load your leave history'))
    }
  }, [employeeId, statusFilter, page])

  const loadTypes = useCallback(() => {
    setTypesError(null)
    listLeaveTypes()
      .then((t) => {
        setTypes(t)
        setTypesLoaded(true)
      })
      .catch((err) => {
        setTypesError(errorMessage(err, 'Could not load the leave types'))
        setTypesLoaded(true)
      })
  }, [])
  useEffect(() => {
    // eslint-disable-next-line react-hooks/set-state-in-effect
    loadTypes()
  }, [loadTypes])
  useEffect(() => {
    // eslint-disable-next-line react-hooks/set-state-in-effect
    void refreshBalances()
  }, [refreshBalances])
  useEffect(() => {
    // eslint-disable-next-line react-hooks/set-state-in-effect
    void refreshHistory()
  }, [refreshHistory])

  const mandatoryHolidays = useMemo(
    () => new Set([...holidays.values()].filter((h) => !h.optional).map((h) => h.holidayDate)),
    [holidays],
  )

  const noTypes = typesLoaded && !typesError && types.length === 0
  const canManage = canManageEmployees(user?.role)
  const crossesYears = !!start && !!end && start.slice(0, 4) !== end.slice(0, 4)
  const rangeInvalid = !!start && !!end && end < start
  const workingDays = start && end && !rangeInvalid && !crossesYears ? countWorkingDays(start, end, mandatoryHolidays) : null
  const selectedBalance = balances.find((b) => b.leaveTypeId === Number(typeId))
  const overBalance = workingDays !== null && !!selectedBalance && workingDays > selectedBalance.availableDays

  function pickDay(iso: string) {
    if (!start || (end && end !== start)) {
      setStart(iso)
      setEnd(iso)
    } else if (iso >= start) {
      setEnd(iso)
    } else {
      setStart(iso)
      setEnd(iso)
    }
  }

  function changeFile(f: File | null) {
    setError(null)
    if (f && f.size > MAX_FILE_BYTES) {
      setError('The attachment is too large (maximum 5 MB).')
      setFileKey((k) => k + 1)
      return
    }
    setFile(f)
  }

  async function handleSubmit(e: FormEvent) {
    e.preventDefault()
    setError(null)
    setNotice(null)
    setBusy(true)
    try {
      const attachmentUrl = file ? await uploadAttachment(file) : undefined
      await applyForLeave({
        leaveTypeId: Number(typeId),
        startDate: start,
        endDate: end,
        reason: reason.trim() || undefined,
        attachmentUrl,
      })
      setNotice('Leave request submitted. It is now pending approval.')
      setStart('')
      setEnd('')
      setReason('')
      setFile(null)
      setFileKey((k) => k + 1)
      setPage(0)
      await Promise.all([refreshBalances(), refreshHistory()])
    } catch (err) {
      setError(errorMessage(err, 'Could not submit the leave request'))
    } finally {
      setBusy(false)
    }
  }

  async function handleCancel(r: LeaveRequest) {
    const message = r.status === 'APPROVED'
      ? 'This leave is already approved. Cancel it and return the days to your balance?'
      : 'Cancel this leave request?'
    if (!window.confirm(message)) return
    setError(null)
    setNotice(null)
    try {
      await cancelLeave(r.id)
      await Promise.all([refreshBalances(), refreshHistory()])
    } catch (err) {
      setError(errorMessage(err, 'Could not cancel the request'))
    }
  }

  async function handleAttachment(id: number) {
    try {
      await openAttachment(id)
    } catch (err) {
      setError(errorMessage(err, 'Could not open the attachment'))
    }
  }

  if (employeeId === null) {
    return (
      <main className="page">
        <PageHeader title="My leave" description="Balances, requests and leave history." />
        <SectionCard title="My leave" icon={CalendarDays} tint="orange">
          <p>Your login is not linked to an employee record, so there is no leave to manage.</p>
        </SectionCard>
      </main>
    )
  }

  const todayISO = toISO(today)
  const canCancel = (r: LeaveRequest) => r.status === 'PENDING' || (r.status === 'APPROVED' && r.startDate > todayISO)

  const columns: Column<LeaveRequest>[] = [
    { key: 'type', header: 'Type', render: (r) => r.leaveTypeName },
    { key: 'dates', header: 'Dates', render: (r) => `${formatDateOnly(r.startDate)} – ${formatDateOnly(r.endDate)}` },
    { key: 'days', header: 'Days', render: (r) => r.totalDays },
    { key: 'status', header: 'Status', render: (r) => <StatusCodePill status={r.status} /> },
    { key: 'decision', header: 'Decision', render: (r) => (
      <>{r.approverName ? `${r.approverName}: ` : ''}{r.approverComment ?? (r.status === 'PENDING' ? 'Awaiting decision' : '—')}</>
    ) },
    { key: 'actions', header: '', align: 'right', render: (r) => (
      <span className="actions">
        {r.hasAttachment && <button type="button" onClick={() => handleAttachment(r.id)}>Attachment</button>}
        {canCancel(r) && <button type="button" className="danger" onClick={() => handleCancel(r)}>Cancel</button>}
      </span>
    ) },
  ]

  return (
    <main className="page">
      <PageHeader title="My leave" description="Check your balances, apply for leave and follow your requests." />
      {error && <p className="error">{error}</p>}
      <Flash message={notice} onDone={() => setNotice(null)} />

      <SectionCard title={`Balances for ${year}`} icon={CalendarDays} tint="orange">
        {balances.length === 0 ? (
          <p className="hint">{noTypes ? 'No leave types have been set up yet.' : 'No balances to show yet.'}</p>
        ) : (
          <div className="stat-grid">
            {balances.map((b) => (
              <StatCard key={b.leaveTypeId} icon={CalendarDays} tint="orange" label={`${b.leaveTypeName} available`} value={b.availableDays}
                trend={`Allocated ${b.allocatedDays} · Carried forward ${b.carriedForwardDays} · Used ${b.usedDays} · Pending ${b.pendingDays}`} />
            ))}
          </div>
        )}
      </SectionCard>

      <SectionCard title="Apply for leave" icon={CalendarPlus} tint="orange">
        <div className="leave-form-layout">
          <form onSubmit={handleSubmit} className="form">
            {typesError && (
              <div className="notice-box error-box">
                <p>{typesError}</p>
                <button type="button" onClick={loadTypes}>Try again</button>
              </div>
            )}
            {typesLoaded && !typesError && noTypes && (
              <div className="notice-box">
                <strong>No leave types are available yet.</strong>
                {canManage ? (
                  <p>
                    You can set them up now under <Link to="/admin/leave-types">Admin → Leave Types</Link>{' '}
                    (for example Annual and Sick leave). They appear here straight away.
                  </p>
                ) : (
                  <p>Leave can&apos;t be requested until HR sets up the leave types. Please ask HR to add them.</p>
                )}
              </div>
            )}
            <label>Leave type
              <select value={typeId} onChange={(e) => setTypeId(e.target.value)} required disabled={noTypes}>
                <option value="">{!typesLoaded ? 'Loading…' : noTypes ? 'No leave types available' : 'Select…'}</option>
                {types.map((t) => <option key={t.id} value={t.id}>{t.name}</option>)}
              </select>
            </label>
            <label>From
              <input type="date" value={start} onChange={(e) => { setStart(e.target.value); if (!end || end < e.target.value) setEnd(e.target.value) }} required />
            </label>
            <label>To
              <input type="date" value={end} min={start || undefined} onChange={(e) => setEnd(e.target.value)} required />
            </label>
            {workingDays !== null && (
              <p className={workingDays === 0 || overBalance ? 'error' : 'hint'}>
                {workingDays === 0
                  ? 'These dates are only weekends or holidays, so no leave is needed.'
                  : `${workingDays} working day${workingDays === 1 ? '' : 's'} (weekends and public holidays are not counted).`}
                {overBalance && selectedBalance && ` You only have ${selectedBalance.availableDays} available.`}
              </p>
            )}
            {rangeInvalid && <p className="error">The end date cannot be before the start date.</p>}
            {crossesYears && <p className="error">Leave cannot span two calendar years. Submit one request per year.</p>}
            <label>Reason (optional)
              <textarea value={reason} onChange={(e) => setReason(e.target.value)} maxLength={1000} rows={3} />
            </label>
            <label>Attachment (optional, PDF/PNG/JPG, max 5 MB)
              <input key={fileKey} type="file" accept=".pdf,.png,.jpg,.jpeg,application/pdf,image/png,image/jpeg"
                onChange={(e) => changeFile(e.target.files?.[0] ?? null)} />
            </label>
            <button type="submit" disabled={busy || !typeId || !start || !end || rangeInvalid || crossesYears || workingDays === 0 || overBalance}>
              {busy ? 'Submitting…' : 'Submit request'}
            </button>
          </form>

          <div>
            <MonthGrid
              year={view.year}
              month={view.month}
              holidays={holidays}
              onPick={pickDay}
              selection={start && end && !rangeInvalid ? { start, end } : null}
              onPrev={() => setView((v) => (v.month === 0 ? { year: v.year - 1, month: 11 } : { ...v, month: v.month - 1 }))}
              onNext={() => setView((v) => (v.month === 11 ? { year: v.year + 1, month: 0 } : { ...v, month: v.month + 1 }))}
            />
            <p className="hint legend">
              <span className="legend-item"><span className="key weekend" /> weekend</span>
              <span className="legend-item"><span className="key holiday" /> public holiday</span>
              <span className="legend-item"><span className="key optional" /> optional holiday</span>
              <span className="legend-item"><span className="key range" /> selected</span>
            </p>
            <p className="hint">Click a day to start, then click another day to set the end.</p>
          </div>
        </div>
      </SectionCard>

      <SectionCard title="My requests" icon={History} tint="orange"
        actions={(
          <select value={statusFilter} onChange={(e) => { setStatusFilter(e.target.value as LeaveStatus | ''); setPage(0) }}>
            <option value="">All statuses</option>
            {LEAVE_STATUSES.map((s) => <option key={s}>{s}</option>)}
          </select>
        )}>
        <DataTable columns={columns} rows={history ? history.content : null} rowKey={(r) => r.id}
          rowClassName={(r) => (r.id === highlight ? 'highlight' : undefined)} emptyText="No leave requests yet." />
        {history && <Pager data={history} onPage={setPage} noun="requests" />}
      </SectionCard>
    </main>
  )
}
