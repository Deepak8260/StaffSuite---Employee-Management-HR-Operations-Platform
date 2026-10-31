import EmployeeLink from '../../components/ui/EmployeeLink'
import { ClipboardCheck } from 'lucide-react'
import { useCallback, useEffect, useState } from 'react'
import { useSearchParams } from 'react-router-dom'
import { approveCorrection, listCorrections, rejectCorrection } from '../../api/attendance'
import { errorMessage } from '../../api/axiosInstance'
import Pager from '../../components/Pager'
import DataTable, { type Column } from '../../components/ui/DataTable'
import SectionCard from '../../components/ui/SectionCard'
import { StatusCodePill } from '../../components/ui/StatusPill'
import { useAuth } from '../../hooks/useAuth'
import type { PageResponse } from '../../types/auth'
import { CORRECTION_STATUSES, type Correction, type CorrectionStatus } from '../../types/attendance'
import { formatDateOnly } from '../../utils/dates'
import { timeOf } from '../../utils/time'

const PAGE_SIZE = 8

interface Props {
  /** TEAM = a manager's direct reports; ALL = the whole organisation (HR/Admin). */
  scope: 'TEAM' | 'ALL'
}

interface Decision {
  correction: Correction
  action: 'approve' | 'reject'
}

export default function CorrectionsPanel({ scope }: Props) {
  const { user } = useAuth()
  // arriving from a notification (?correction=ID): show all statuses and highlight that request
  const [params] = useSearchParams()
  const highlight = Number(params.get('correction')) || null
  const [status, setStatus] = useState<CorrectionStatus | ''>(highlight ? '' : 'PENDING')
  const [page, setPage] = useState(0)
  const [data, setData] = useState<PageResponse<Correction> | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [decision, setDecision] = useState<Decision | null>(null)
  const [comment, setComment] = useState('')
  const [modalError, setModalError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)

  const load = useCallback(async () => {
    try {
      setData(await listCorrections({ scope, status: status || undefined, page, size: PAGE_SIZE }))
      setError(null)
    } catch (err) {
      setError(errorMessage(err, 'Could not load correction requests'))
    }
  }, [scope, status, page])

  useEffect(() => {
    // eslint-disable-next-line react-hooks/set-state-in-effect
    void load()
  }, [load])

  function open(correction: Correction, action: 'approve' | 'reject') {
    setDecision({ correction, action })
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
      if (decision.action === 'approve') await approveCorrection(decision.correction.id, comment.trim())
      else await rejectCorrection(decision.correction.id, comment.trim())
      setDecision(null)
      await load()
    } catch (err) {
      setModalError(errorMessage(err, 'Could not save the decision'))
    } finally {
      setBusy(false)
    }
  }

  const columns: Column<Correction>[] = [
    {
      key: 'employee',
      header: 'Employee',
      render: (c) => (
        <div className="person">
          <div className="person-text">
            <EmployeeLink id={c.employeeId} className="person-name">{c.employeeName}</EmployeeLink>
            <span className="person-meta">{c.employeeCode}</span>
          </div>
        </div>
      ),
    },
    { key: 'date', header: 'Date', render: (c) => formatDateOnly(c.attendanceDate) },
    { key: 'recorded', header: 'Recorded', render: (c) => <>{timeOf(c.currentCheckIn)} – {timeOf(c.currentCheckOut)}</> },
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
    {
      key: 'status',
      header: 'Status',
      render: (c) => (
        <>
          <StatusCodePill status={c.status} />
          {c.status !== 'PENDING' && c.reviewComment && (
            <div className="hint">{c.reviewedByName ? `${c.reviewedByName}: ` : ''}{c.reviewComment}</div>
          )}
        </>
      ),
    },
    {
      key: 'actions',
      header: '',
      align: 'right',
      className: 'row-actions',
      render: (c) => {
        // The backend refuses self-decisions, so the buttons are not offered on your own requests.
        const own = c.employeeId === user?.employeeId
        return (
          <>
            {c.status === 'PENDING' && !own && (
              <>
                <button type="button" onClick={() => open(c, 'approve')}>Approve</button>
                <button type="button" className="danger" onClick={() => open(c, 'reject')}>Reject</button>
              </>
            )}
            {c.status === 'PENDING' && own && <span className="hint">Your own request</span>}
          </>
        )
      },
    },
  ]

  return (
    <SectionCard
      title="Correction requests"
      icon={ClipboardCheck}
      tint="orange"
      actions={
        <select value={status} onChange={(e) => { setStatus(e.target.value as CorrectionStatus | ''); setPage(0) }}>
          <option value="">All statuses</option>
          {CORRECTION_STATUSES.map((s) => <option key={s}>{s}</option>)}
        </select>
      }
    >
      {error && <p className="error">{error}</p>}
      <DataTable columns={columns} rows={data ? data.content : null} rowKey={(c) => c.id}
        rowClassName={(c) => (c.id === highlight ? 'highlight' : undefined)} emptyText="No correction requests found." />
      {data && <Pager data={data} onPage={setPage} noun="requests" />}

      {decision && (
        <div className="modal-backdrop" role="dialog" aria-modal="true">
          <div className="modal">
            <h2>{decision.action === 'approve' ? 'Approve' : 'Reject'} correction</h2>
            <p>
              {decision.correction.employeeName} · {formatDateOnly(decision.correction.attendanceDate)} · in{' '}
              {timeOf(decision.correction.requestedCheckIn ?? decision.correction.currentCheckIn)}, out{' '}
              {timeOf(decision.correction.requestedCheckOut ?? decision.correction.currentCheckOut)}
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
    </SectionCard>
  )
}
