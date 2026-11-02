import { Info, KeyRound, LogOut, Moon, Palette, Sun, UserCog } from 'lucide-react'
import { useEffect, useState, type ReactNode } from 'react'
import { Link, useNavigate, useSearchParams } from 'react-router-dom'
import { getHealth } from '../../api/health'
import { getEmployee } from '../../api/employees'
import PageHeader from '../../components/ui/PageHeader'
import SectionCard from '../../components/ui/SectionCard'
import StatusPill from '../../components/ui/StatusPill'
import { useAuth } from '../../hooks/useAuth'
import { useDisplayName } from '../../hooks/useDisplayName'
import { useTheme, type Theme } from '../../hooks/useTheme'
import { getConfig } from '../../utils/config'
import ChangePasswordForm from '../auth/ChangePasswordForm'

type TabKey = 'account' | 'password' | 'appearance' | 'about'

const TABS: { key: TabKey; label: string; icon: typeof Info }[] = [
  { key: 'account', label: 'Account settings', icon: UserCog },
  { key: 'password', label: 'Change password', icon: KeyRound },
  { key: 'appearance', label: 'Appearance', icon: Palette },
  { key: 'about', label: 'About', icon: Info },
]

const ROLE_LABEL = { SYSTEM_ADMIN: 'System administrator', HR_ADMIN: 'HR administrator', MANAGER: 'Manager', EMPLOYEE: 'Employee' } as const

function Rows({ rows }: { rows: [string, ReactNode][] }) {
  return (
    <dl className="kv">
      {rows.map(([label, value]) => <div key={label}><dt>{label}</dt><dd>{value}</dd></div>)}
    </dl>
  )
}

function AccountTab() {
  const { user, logout } = useAuth()
  const { fullName } = useDisplayName(user)
  const navigate = useNavigate()
  const [code, setCode] = useState<string | null>(null)
  const employeeId = user?.employeeId ?? null

  useEffect(() => {
    if (employeeId === null) return
    let cancelled = false
    getEmployee(employeeId).then((e) => { if (!cancelled) setCode(e.employeeCode) }).catch(() => { /* optional detail */ })
    return () => { cancelled = true }
  }, [employeeId])

  if (!user) return null
  return (
    <SectionCard title="Account settings" icon={UserCog} tint="blue">
      <Rows rows={[
        ['Name', fullName],
        ['Login email', user.email],
        ['Role', ROLE_LABEL[user.role]],
        ...(code ? [['Employee code', code] as [string, ReactNode]] : []),
      ]} />
      <p className="hint settings-note">
        Your name, job title and other employment details are managed by HR.
        {employeeId !== null && <> You can update your phone, address and emergency contact, and your photo, on <Link to={`/employees/${employeeId}/profile`}>your profile</Link>.</>}
      </p>
      <div className="settings-actions">
        <button type="button" className="secondary" onClick={async () => { await logout(); navigate('/login', { replace: true }) }}>
          <LogOut size={14} aria-hidden="true" /> Log out
        </button>
      </div>
    </SectionCard>
  )
}

function AppearanceTab() {
  const { theme, setTheme } = useTheme()
  const option = (value: Theme, label: string, hint: string, Icon: typeof Sun) => (
    <button type="button" role="radio" aria-checked={theme === value} className={`theme-card ${theme === value ? 'selected' : ''}`} onClick={() => setTheme(value)}>
      <Icon size={20} aria-hidden="true" />
      <strong>{label}</strong>
      <span>{hint}</span>
    </button>
  )
  return (
    <SectionCard title="Appearance" icon={Palette} tint="purple">
      <p className="hint">Choose how StaffSuite looks. The choice is remembered in this browser.</p>
      <div className="theme-cards" role="radiogroup" aria-label="Colour theme">
        {option('light', 'Light', 'Bright and clean', Sun)}
        {option('dark', 'Dark', 'Easier on the eyes at night', Moon)}
      </div>
    </SectionCard>
  )
}

function AboutTab() {
  const [server, setServer] = useState<'checking' | 'online' | 'offline'>('checking')
  const env = getConfig().APP_ENV_LABEL || window.location.hostname

  useEffect(() => {
    let cancelled = false
    getHealth().then(() => { if (!cancelled) setServer('online') }).catch(() => { if (!cancelled) setServer('offline') })
    return () => { cancelled = true }
  }, [])

  return (
    <SectionCard title="About StaffSuite" icon={Info} tint="green">
      <p className="settings-lead">Employee Management &amp; HR Operations Platform.</p>
      <Rows rows={[
        ['Application', 'StaffSuite'],
        ['Version', __APP_VERSION__],
        ['Environment', env],
        ['Server', server === 'checking' ? 'Checking…' : server === 'online' ? <StatusPill tone="success">Online</StatusPill> : <StatusPill tone="danger">Not reachable</StatusPill>],
      ]} />
      <p className="hint settings-note">Employees, attendance, leave, documents and organisational insights in one place.</p>
    </SectionCard>
  )
}

/** One place for account, security, appearance and about. The tab is kept in the address (?tab=) so it can be linked to. */
export default function SettingsPage() {
  const [params, setParams] = useSearchParams()
  const asked = params.get('tab')
  const tab: TabKey = TABS.some((t) => t.key === asked) ? (asked as TabKey) : 'account'

  return (
    <main className="page">
      <PageHeader title="Settings" description="Manage your account, password and how StaffSuite looks." />
      <div className="settings-layout">
        <nav className="settings-tabs" aria-label="Settings sections">
          {TABS.map(({ key, label, icon: Icon }) => (
            <button key={key} type="button" className={`settings-tab ${tab === key ? 'active' : ''}`} aria-current={tab === key ? 'page' : undefined}
              onClick={() => setParams({ tab: key }, { replace: true })}>
              <Icon size={16} aria-hidden="true" /> {label}
            </button>
          ))}
        </nav>
        <div className="settings-content">
          {tab === 'account' && <AccountTab />}
          {tab === 'password' && (
            <SectionCard title="Change password" icon={KeyRound} tint="orange">
              <p className="hint">Use at least 8 characters.</p>
              <ChangePasswordForm />
            </SectionCard>
          )}
          {tab === 'appearance' && <AppearanceTab />}
          {tab === 'about' && <AboutTab />}
        </div>
      </div>
    </main>
  )
}
