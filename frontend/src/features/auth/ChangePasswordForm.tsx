import { useState, type FormEvent } from 'react'
import { changePassword } from '../../api/auth'
import { errorMessage } from '../../api/axiosInstance'
import Flash from '../../components/Flash'

/** Current password, new password, confirmation. Used on the Settings page. */
export default function ChangePasswordForm() {
  const [current, setCurrent] = useState('')
  const [next, setNext] = useState('')
  const [confirm, setConfirm] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [success, setSuccess] = useState(false)
  const [busy, setBusy] = useState(false)

  async function handleSubmit(e: FormEvent) {
    e.preventDefault()
    setError(null)
    setSuccess(false)
    if (next !== confirm) {
      setError('New passwords do not match')
      return
    }
    setBusy(true)
    try {
      await changePassword(current, next)
      setSuccess(true)
      setCurrent('')
      setNext('')
      setConfirm('')
    } catch (err) {
      setError(errorMessage(err, 'Could not change password'))
    } finally {
      setBusy(false)
    }
  }

  return (
    <form onSubmit={handleSubmit} className="form settings-form">
      <label>
        Current password
        <input type="password" value={current} autoComplete="current-password" onChange={(e) => setCurrent(e.target.value)} required />
      </label>
      <label>
        New password (min 8 characters)
        <input type="password" value={next} autoComplete="new-password" onChange={(e) => setNext(e.target.value)} minLength={8} required />
      </label>
      <label>
        Confirm new password
        <input type="password" value={confirm} autoComplete="new-password" onChange={(e) => setConfirm(e.target.value)} required />
      </label>
      {error && <p className="error">{error}</p>}
      <Flash message={success ? 'Password changed.' : null} onDone={() => setSuccess(false)} />
      <div><button type="submit" className="btn-primary" disabled={busy}>{busy ? 'Saving…' : 'Change password'}</button></div>
    </form>
  )
}
