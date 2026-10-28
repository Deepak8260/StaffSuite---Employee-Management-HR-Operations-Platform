import { useCallback, useEffect, useState } from 'react'
import { useSearchParams } from 'react-router-dom'
import { approveCorrection, listCorrections, rejectCorrection } from '../../api/attendance'
import { errorMessage } from '../../api/axiosInstance'
import Pager from '../../components/Pager'
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

  return (
    <section>
      <div className="page-head">
        <h2>Correction requests</h2>
        <select value={status} onChange={(e) => { setStatus(e.target.value as CorrectionStatus | ''); setPage(0) }}>
          <option value="">All statuses</option>
          {CORRECTION_STATUSES.map((s) => <option key={s}>{s}</option>)}
        </select>
      </div>
      {error && <p className="error">{error}</p>}
      <table>
        <thead>
          <tr><th>Employee</th><th>Date</th><th>Recorded</th><th>Requested</th><th>Reason</th><th>Status</th><th></th></tr>
        </thead>
        <tbody>
          {data?.content.map((c) => {
            // The backend refuses self-decisions, so the buttons are not offered on your own requests.
            const own = c.employeeId === user?.employeeId
            return (
              <tr key={c.id} className={c.id === highlight ? 'highlight' : undefined}>
                <td>{c.employeeName}<br /><span className="hint">{c.employeeCode}</span></td>
                <td>{formatDateOnly(c.attendanceDate)}</td>
                <td>{timeOf(c.currentCheckIn)} – {timeOf(c.currentCheckOut)}</td>
                <td>
                  {c.requestedCheckIn ? `In ${timeOf(c.requestedCheckIn)}` : ''}
                  {c.requestedCheckIn && c.requestedCheckOut ? ' · ' : ''}
                  {c.requestedCheckOut ? `Out ${timeOf(c.requestedCheckOut)}` : ''}
                </td>
                <td>{c.reason}</td>
                <td>
                  <span className={`badge ${c.status.toLowerCase()}`}>{c.status}</span>
                  {c.status !== 'PENDING' && c.reviewComment && (
                    <div className="hint">{c.reviewedByName ? `${c.reviewedByName}: ` : ''}{c.reviewComment}</div>
                  )}
                </td>
                <td className="actions">
                  {c.status === 'PENDING' && !own && (
                    <>
                      <button type="button" onClick={() => open(c, 'approve')}>Approve</button>
                      <button type="button" className="danger" onClick={() => open(c, 'reject')}>Reject</button>
                    </>
                  )}
                  {c.status === 'PENDING' && own && <span className="hint">Your own request</span>}
                </td>
              </tr>
            )
          })}
          {data && data.content.length === 0 && <tr><td colSpan={7}>No correction requests found.</td></tr>}
        </tbody>
      </table>
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
    </section>
  )
}
