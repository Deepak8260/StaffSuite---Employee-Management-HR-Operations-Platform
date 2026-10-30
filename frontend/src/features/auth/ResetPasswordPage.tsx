import { useState, type FormEvent } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import { confirmPasswordReset } from '../../api/auth'
import { errorMessage } from '../../api/axiosInstance'
import AuthShell from '../../components/AuthShell'

export default function ResetPasswordPage() {
  const [params] = useSearchParams()
  const [token, setToken] = useState(params.get('token') ?? '')
  const [password, setPassword] = useState('')
  const [confirm, setConfirm] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [done, setDone] = useState(false)
  const [busy, setBusy] = useState(false)

  async function handleSubmit(e: FormEvent) {
    e.preventDefault()
    setError(null)
    if (password !== confirm) {
      setError('Passwords do not match')
      return
    }
    setBusy(true)
    try {
      await confirmPasswordReset(token.trim(), password)
      setDone(true)
    } catch (err) {
      setError(errorMessage(err, 'Could not reset password'))
    } finally {
      setBusy(false)
    }
  }

  if (done) {
    return (
      <AuthShell>
        <h1>Password reset</h1>
        <p className="success">Your password has been reset.</p>
        <Link to="/login">Sign in</Link>
      </AuthShell>
    )
  }

  return (
    <AuthShell>
      <h1>Reset password</h1>
      <form onSubmit={handleSubmit} className="auth-form">
        <label>
          Reset token
          <input value={token} onChange={(e) => setToken(e.target.value)} required />
        </label>
        <label>
          New password (min 8 characters)
          <input type="password" value={password} onChange={(e) => setPassword(e.target.value)} minLength={8} required />
        </label>
        <label>
          Confirm new password
          <input type="password" value={confirm} onChange={(e) => setConfirm(e.target.value)} required />
        </label>
        {error && <p className="error">{error}</p>}
        <button type="submit" className="auth-submit" disabled={busy}>
          Reset password
        </button>
      </form>
      <p className="auth-link">
        <Link to="/login">Back to sign in</Link>
      </p>
    </AuthShell>
  )
}
