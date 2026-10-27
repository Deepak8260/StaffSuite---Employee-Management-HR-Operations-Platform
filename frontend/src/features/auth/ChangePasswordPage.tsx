import { useState, type FormEvent } from 'react'
import { changePassword } from '../../api/auth'
import { errorMessage } from '../../api/axiosInstance'

export default function ChangePasswordPage() {
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
    <main className="page narrow">
      <h1>Change password</h1>
      <form onSubmit={handleSubmit} className="form">
        <label>
          Current password
          <input type="password" value={current} onChange={(e) => setCurrent(e.target.value)} required />
        </label>
        <label>
          New password (min 8 characters)
          <input type="password" value={next} onChange={(e) => setNext(e.target.value)} minLength={8} required />
        </label>
        <label>
          Confirm new password
          <input type="password" value={confirm} onChange={(e) => setConfirm(e.target.value)} required />
        </label>
        {error && <p className="error">{error}</p>}
        {success && <p className="success">Password changed.</p>}
        <button type="submit" disabled={busy}>
          Change password
        </button>
      </form>
    </main>
  )
}
