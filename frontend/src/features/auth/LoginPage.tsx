import { Eye, EyeOff, Lock, Mail } from 'lucide-react'
import { useState, type FormEvent } from 'react'
import { Link, Navigate, useLocation, useNavigate } from 'react-router-dom'
import { errorMessage } from '../../api/axiosInstance'
import AuthShell from '../../components/AuthShell'
import { useAuth } from '../../hooks/useAuth'

export default function LoginPage() {
  const { user, login } = useAuth()
  const navigate = useNavigate()
  const location = useLocation()
  const from = (location.state as { from?: string } | null)?.from ?? '/'
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [showPassword, setShowPassword] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)

  if (user) return <Navigate to="/" replace />

  async function handleSubmit(e: FormEvent) {
    e.preventDefault()
    setError(null)
    setBusy(true)
    try {
      await login(email, password)
      navigate(from, { replace: true })
    } catch (err) {
      setError(errorMessage(err, 'Login failed'))
    } finally {
      setBusy(false)
    }
  }

  return (
    <AuthShell>
      <h1>Sign in to StaffSuite</h1>
      <p className="auth-sub">Welcome back! Please enter your credentials to continue.</p>
      <form onSubmit={handleSubmit} className="auth-form">
        <label>
          Email
          <span className="auth-field">
            <Mail size={20} strokeWidth={1.7} aria-hidden="true" />
            <input type="email" value={email} placeholder="Enter your email address" autoComplete="username"
              onChange={(e) => setEmail(e.target.value)} required autoFocus />
          </span>
        </label>
        <label>
          Password
          <span className="auth-field">
            <Lock size={20} strokeWidth={1.7} aria-hidden="true" />
            <input type={showPassword ? 'text' : 'password'} value={password} placeholder="Enter your password"
              autoComplete="current-password" onChange={(e) => setPassword(e.target.value)} required />
            <button type="button" className="auth-eye" aria-label={showPassword ? 'Hide password' : 'Show password'}
              onClick={() => setShowPassword((s) => !s)}>
              {showPassword ? <Eye size={20} aria-hidden="true" /> : <EyeOff size={20} aria-hidden="true" />}
            </button>
          </span>
        </label>
        {error && <p className="error">{error}</p>}
        <button type="submit" className="auth-submit" disabled={busy}>
          {busy ? 'Signing in…' : 'Sign in'}
        </button>
      </form>
      <p className="auth-link">
        <Link to="/forgot-password">Forgot password?</Link>
      </p>
    </AuthShell>
  )
}
