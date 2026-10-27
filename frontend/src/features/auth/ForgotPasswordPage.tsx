import { useState, type FormEvent } from 'react'
import { Link } from 'react-router-dom'
import { requestPasswordReset } from '../../api/auth'
import { errorMessage } from '../../api/axiosInstance'

export default function ForgotPasswordPage() {
  const [email, setEmail] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [sent, setSent] = useState(false)
  const [busy, setBusy] = useState(false)

  async function handleSubmit(e: FormEvent) {
    e.preventDefault()
    setError(null)
    setBusy(true)
    try {
      await requestPasswordReset(email)
      setSent(true)
    } catch (err) {
      setError(errorMessage(err))
    } finally {
      setBusy(false)
    }
  }

  return (
    <main className="page narrow">
      <h1>Forgot password</h1>
      {sent ? (
        <>
          <p className="success">If the account exists, a reset token has been issued.</p>
          <p>
            <Link to="/reset-password">I have a reset token</Link>
          </p>
        </>
      ) : (
        <form onSubmit={handleSubmit} className="form">
          <label>
            Email
            <input type="email" value={email} onChange={(e) => setEmail(e.target.value)} required autoFocus />
          </label>
          {error && <p className="error">{error}</p>}
          <button type="submit" disabled={busy}>
            Request reset
          </button>
        </form>
      )}
      <p>
        <Link to="/login">Back to sign in</Link>
      </p>
    </main>
  )
}
