import type { ReactNode } from 'react'
import hero from '../assets/login-hero.jpg'

/** Split screen for sign in, forgot password and reset password: the product picture on the left, the form card on the right. */
export default function AuthShell({ children }: { children: ReactNode }) {
  return (
    <div className="auth-shell">
      <aside className="auth-hero">
        <img src={hero} alt="StaffSuite: manage your team, effortlessly. Employee, attendance, leave and document management and organizational insights." />
      </aside>
      <main className="auth-panel">
        <div className="auth-mobile-logo">Staff<b>Suite</b></div>
        <div className="auth-card">{children}</div>
      </main>
    </div>
  )
}
