import { ClipboardCheck } from 'lucide-react'
import { useCallback, useEffect, useState } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import { errorMessage } from '../../api/axiosInstance'
import { approveLeave, listLeaveRequests, openAttachment, rejectLeave } from '../../api/leave'
import Pager from '../../components/Pager'
import AvatarInitials from '../../components/ui/AvatarInitials'
import DataTable, { type Column } from '../../components/ui/DataTable'
import PageHeader from '../../components/ui/PageHeader'
import SectionCard from '../../components/ui/SectionCard'
import { StatusCodePill } from '../../components/ui/StatusPill'
import { useAuth } from '../../hooks/useAuth'
import type { PageResponse } from '../../types/auth'
import { LEAVE_STATUSES, type LeaveRequest, type LeaveScope, type LeaveStatus } from '../../types/leave'
import { formatDateOnly } from '../../utils/dates'

const PAGE_SIZE = 10

interface Props {
  /** TEAM = a manager's direct reports; ALL = the whole organisation (HR/Admin). */
  scope: Extract<LeaveScope, 'TEAM' | 'ALL'>
}

interface Decision {
  request: LeaveRequest
  action: 'approve' | 'reject'
}

export default function LeaveApprovalsPage({ scope }: Props) {
  const { user } = useAuth()
  // arriving from a notification (?request=ID): show all statuses and highlight that request
  const [params] = useSearchParams()
  const highlight = Number(params.get('request')) || null
  const [status, setStatus] = useState<LeaveStatus | ''>(highlight ? '' : 'PENDING')
  const [page, setPage] = useState(0)
  const [data, setData] = useState<PageResponse<LeaveRequest> | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [decision, setDecision] = useState<Decision | null>(null)
  const [comment, setComment] = useState('')
  const [busy, setBusy] = useState(false)
  const [modalError, setModalError] = useState<string | null>(null)

  const load = useCallback(async () => {
    try {
      setData(await listLeaveRequests({ scope, status: status || undefined, page, size: PAGE_SIZE }))
      setError(null)
    } catch (err) {
      setError(errorMessage(err, 'Could not load leave requests'))
    }
  }, [scope, status, page])

  useEffect(() => {
    // eslint-disable-next-line react-hooks/set-state-in-effect
    void load()
  }, [load])

  function open(request: LeaveRequest, action: 'approve' | 'reject') {
    setDecision({ request, action })
    setComment('')
    setModalError(null)
  }

  async function submit() {
    if (!decision) return
    if (decision.action === 'reject' && !comment.trim()) {
      setModalError('Please give a reason for rejecting this request.')
      return
    }
    setBusy(true)
    setModalError(null)
    try {
      if (decision.action === 'approve') await approveLeave(decision.request.id, comment.trim())
      else await rejectLeave(decision.request.id, comment.trim())
      setDecision(null)
      await load()
    } catch (err) {
      setModalError(errorMessage(err, 'Could not save the decision'))
    } finally {
      setBusy(false)
    }
  }

  async function handleAttachment(id: number) {
    try {
      await openAttachment(id)
    } catch (err) {
      setError(errorMessage(err, 'Could not open the attachment'))
    }
  }

  const columns: Column<LeaveRequest>[] = [
    { key: 'employee', header: 'Employee', render: (r) => (
      <span className="person"><AvatarInitials name={r.employeeName} size="sm" />
        <span><span className="person-name">{r.employeeName}</span><br /><span className="person-meta">{r.employeeCode}</span></span></span>
    ) },
    { key: 'type', header: 'Type', render: (r) => r.leaveTypeName },
    { key: 'dates', header: 'Dates', render: (r) => `${formatDateOnly(r.startDate)} – ${formatDateOnly(r.endDate)}` },
    { key: 'days', header: 'Days', render: (r) => r.totalDays },
    { key: 'reason', header: 'Reason', render: (r) => r.reason ?? '—' },
    { key: 'status', header: 'Status', render: (r) => (
      <>
        <StatusCodePill status={r.status} />
        {r.status !== 'PENDING' && r.approverComment && <div className="hint">{r.approverName ? `${r.approverName}: ` : ''}{r.approverComment}</div>}
      </>
    ) },
    { key: 'actions', header: '', align: 'right', render: (r) => {
      // The backend refuses self-approval, so the buttons are not offered for your own requests.
      const own = r.employeeId === user?.employeeId
      return (
        <span className="actions">
          {r.hasAttachment && <button type="button" onClick={() => handleAttachment(r.id)}>Attachment</button>}
          {r.status === 'PENDING' && !own && (
            <>
              <button type="button" onClick={() => open(r, 'approve')}>Approve</button>
              <button type="button" className="danger" onClick={() => open(r, 'reject')}>Reject</button>
            </>
          )}
          {r.status === 'PENDING' && own && <span className="hint">Your own request</span>}
        </span>
      )
    } },
  ]

  return (
    <main className="page">
      <PageHeader
        title={scope === 'TEAM' ? 'Team leave requests' : 'All leave requests'}
        description={scope === 'TEAM' ? 'Review leave requests from your direct reports.' : 'Review leave requests across the organisation.'}
        actions={(
          <>
            {/* the ALL view is only reachable by HR/Admin, who are the ones who manage leave types */}
            {scope === 'ALL' && <Link to="/admin/leave-types" className="button">Manage leave types</Link>}
            <select value={status} onChange={(e) => { setStatus(e.target.value as LeaveStatus | ''); setPage(0) }}>
              <option value="">All statuses</option>
              {LEAVE_STATUSES.map((s) => <option key={s}>{s}</option>)}
            </select>
          </>
        )}
      />
      {error && <p className="error">{error}</p>}

      <SectionCard title="Requests" icon={ClipboardCheck} tint="orange">
        <DataTable columns={columns} rows={data ? data.content : null} rowKey={(r) => r.id}
          rowClassName={(r) => (r.id === highlight ? 'highlight' : undefined)} emptyText="No leave requests found." />
        {data && <Pager data={data} onPage={setPage} noun="requests" />}
      </SectionCard>

      {decision && (
        <div className="modal-backdrop" role="dialog" aria-modal="true">
          <div className="modal">
            <h2>{decision.action === 'approve' ? 'Approve' : 'Reject'} leave</h2>
            <p>
              {decision.request.employeeName} · {decision.request.leaveTypeName} ·{' '}
              {formatDateOnly(decision.request.startDate)} – {formatDateOnly(decision.request.endDate)} (
              {decision.request.totalDays} day{decision.request.totalDays === 1 ? '' : 's'})
            </p>
            <div className="form">
              <label>
                Comment {decision.action === 'approve' ? '(optional)' : '(required)'}
                <textarea value={comment} onChange={(e) => setComment(e.target.value)} rows={3} maxLength={1000} autoFocus />
              </label>
              {modalError && <p className="error">{modalError}</p>}
              <div className="actions">
                <button className={decision.action === 'reject' ? 'danger' : undefined} onClick={submit} disabled={busy}>
                  {busy ? 'Saving…' : decision.action === 'approve' ? 'Approve' : 'Reject'}
                </button>
                <button onClick={() => setDecision(null)} disabled={busy}>Cancel</button>
              </div>
            </div>
          </div>
        </div>
      )}
    </main>
  )
}
