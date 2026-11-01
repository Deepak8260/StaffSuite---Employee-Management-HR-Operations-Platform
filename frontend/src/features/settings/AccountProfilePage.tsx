import { IdCard, Info, Settings } from 'lucide-react'
import { Link } from 'react-router-dom'
import AvatarInitials from '../../components/ui/AvatarInitials'
import PageHeader from '../../components/ui/PageHeader'
import SectionCard from '../../components/ui/SectionCard'
import StatusPill from '../../components/ui/StatusPill'
import { useAuth } from '../../hooks/useAuth'
import { useDisplayName } from '../../hooks/useDisplayName'

const ROLE_LABEL = { SYSTEM_ADMIN: 'System administrator', HR_ADMIN: 'HR administrator', MANAGER: 'Manager', EMPLOYEE: 'Employee' } as const

/**
 * "My profile" for a login that is not linked to an employee record (for example the first administrator).
 * There is no employment data to show for such a login, so this shows the account itself.
 */
export default function AccountProfilePage() {
  const { user } = useAuth()
  const { fullName } = useDisplayName(user)
  if (!user) return null

  return (
    <main className="page">
      <PageHeader title="My profile" description="Your account details." />

      <SectionCard>
        <div className="profile-head">
          <div className="profile-photo"><AvatarInitials name={fullName} size="xl" /></div>
          <div className="profile-head-text">
            <h1>{fullName}</h1>
            <p className="profile-title">{ROLE_LABEL[user.role]}</p>
            <div className="chips">
              <span className="chip">{user.email}</span>
              <StatusPill tone="success">Active</StatusPill>
            </div>
          </div>
        </div>
      </SectionCard>

      <div className="profile-grid">
        <SectionCard title="Account" icon={IdCard} tint="blue">
          <dl className="kv">
            <div><dt>Name</dt><dd>{fullName}</dd></div>
            <div><dt>Login email</dt><dd>{user.email}</dd></div>
            <div><dt>Role</dt><dd>{ROLE_LABEL[user.role]}</dd></div>
          </dl>
        </SectionCard>

        <SectionCard title="About this login" icon={Info} tint="orange">
          <p className="settings-lead">This login is not linked to an employee record.</p>
          <p className="hint">
            So there are no employment details, leave, attendance or documents to show here. HR can link a login to an employee
            record when the person is added in Employees.
          </p>
          <div className="settings-actions">
            <Link to="/settings" className="button secondary"><Settings size={16} aria-hidden="true" /> Open Settings</Link>
          </div>
        </SectionCard>
      </div>
    </main>
  )
}
