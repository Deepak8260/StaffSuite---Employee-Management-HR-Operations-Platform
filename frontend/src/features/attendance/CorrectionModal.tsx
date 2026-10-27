import { useState, type FormEvent } from 'react'
import { errorMessage } from '../../api/axiosInstance'
import { submitCorrection } from '../../api/attendance'
import type { AttendanceDay } from '../../types/attendance'
import { formatDateOnly } from '../../utils/dates'
import { timeOf } from '../../utils/time'

interface Props {
  day: AttendanceDay
  onClose: () => void
  onDone: () => void
}

/** Request new check-in/check-out times for one day. Only the times you change are sent. */
export default function CorrectionModal({ day, onClose, onDone }: Props) {
  const currentIn = day.checkInTime ? timeOf(day.checkInTime) : ''
  const currentOut = day.checkOutTime ? timeOf(day.checkOutTime) : ''
  const [checkIn, setCheckIn] = useState(currentIn)
  const [checkOut, setCheckOut] = useState(currentOut)
  const [reason, setReason] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)

  async function handleSubmit(e: FormEvent) {
    e.preventDefault()
    setError(null)
    const inChanged = checkIn !== '' && checkIn !== currentIn
    const outChanged = checkOut !== '' && checkOut !== currentOut
    if (!inChanged && !outChanged) {
      setError('Change at least one of the times.')
      return
    }
    if (!checkIn && !currentIn) {
      setError('Please enter a check-in time.')
      return
    }
    setBusy(true)
    try {
      await submitCorrection({
        attendanceRecordId: day.recordId ?? undefined,
        attendanceDate: day.recordId ? undefined : day.date,
        requestedCheckIn: inChanged ? `${day.date}T${checkIn}:00` : undefined,
        requestedCheckOut: outChanged ? `${day.date}T${checkOut}:00` : undefined,
        reason: reason.trim(),
      })
      onDone()
    } catch (err) {
      setError(errorMessage(err, 'Could not submit the correction request'))
    } finally {
      setBusy(false)
    }
  }

  return (
    <div className="modal-backdrop" role="dialog" aria-modal="true">
      <div className="modal">
        <h2>Request a correction</h2>
        <p className="hint">
          {formatDateOnly(day.date)} · currently {day.checkInTime ? `in ${currentIn}` : 'no check-in'}
          {day.checkOutTime ? `, out ${currentOut}` : day.checkInTime ? ', no check-out' : ''}
        </p>
        <form onSubmit={handleSubmit} className="form">
          <label>Check-in time
            <input type="time" value={checkIn} onChange={(e) => setCheckIn(e.target.value)} />
          </label>
          <label>Check-out time
            <input type="time" value={checkOut} onChange={(e) => setCheckOut(e.target.value)} />
          </label>
          <label>Reason
            <textarea value={reason} onChange={(e) => setReason(e.target.value)} rows={3} maxLength={1000} required />
          </label>
          {error && <p className="error">{error}</p>}
          <div className="actions">
            <button type="submit" disabled={busy || !reason.trim()}>{busy ? 'Submitting…' : 'Submit request'}</button>
            <button type="button" onClick={onClose} disabled={busy}>Cancel</button>
          </div>
        </form>
      </div>
    </div>
  )
}
