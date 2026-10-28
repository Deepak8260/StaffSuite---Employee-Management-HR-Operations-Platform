import { UserPlus, Users } from 'lucide-react'
import { useCallback, useEffect, useState, type FormEvent } from 'react'
import { errorMessage } from '../../api/axiosInstance'
import { createUser, listUsers, updateUserRole, updateUserStatus } from '../../api/users'
import Pager from '../../components/Pager'
import AvatarInitials from '../../components/ui/AvatarInitials'
import DataTable, { type Column } from '../../components/ui/DataTable'
import PageHeader from '../../components/ui/PageHeader'
import SectionCard from '../../components/ui/SectionCard'
import StatusPill from '../../components/ui/StatusPill'
import { useAuth } from '../../hooks/useAuth'
import { ROLES, type PageResponse, type Role, type UserSummary } from '../../types/auth'

const PAGE_SIZE = 10

export default function UserManagementPage() {
  const { user: me } = useAuth()
  const [page, setPage] = useState(0)
  const [data, setData] = useState<PageResponse<UserSummary> | null>(null)
  const [error, setError] = useState<string | null>(null)

  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [role, setRole] = useState<Role>('EMPLOYEE')
  const [createError, setCreateError] = useState<string | null>(null)

  const load = useCallback(async () => {
    try {
      setData(await listUsers(page, PAGE_SIZE))
      setError(null)
    } catch (err) {
      setError(errorMessage(err, 'Could not load users'))
    }
  }, [page])

  useEffect(() => {
    // eslint-disable-next-line react-hooks/set-state-in-effect
    void load()
  }, [load])

  async function handleCreate(e: FormEvent) {
    e.preventDefault()
    setCreateError(null)
    try {
      await createUser({ email, password, role })
      setEmail('')
      setPassword('')
      setRole('EMPLOYEE')
      await load()
    } catch (err) {
      setCreateError(errorMessage(err, 'Could not create user'))
    }
  }

  async function run(action: () => Promise<unknown>) {
    try {
      await action()
      await load()
    } catch (err) {
      setError(errorMessage(err))
    }
  }

  const columns: Column<UserSummary>[] = [
    { key: 'email', header: 'Email', render: (u) => (
      <span className="person"><AvatarInitials name={u.email} size="sm" /><span className="person-name">{u.email}</span></span>
    ) },
    { key: 'role', header: 'Role', render: (u) => (
      <select
        value={u.role}
        disabled={u.id === me?.userId}
        onChange={(e) => run(() => updateUserRole(u.id, e.target.value as Role))}
      >
        {ROLES.map((r) => (
          <option key={r}>{r}</option>
        ))}
      </select>
    ) },
    { key: 'status', header: 'Status', render: (u) => (
      <StatusPill tone={u.active ? 'success' : 'neutral'}>{u.active ? 'Active' : 'Inactive'}</StatusPill>
    ) },
    { key: 'last', header: 'Last login', render: (u) => (u.lastLoginAt ? new Date(u.lastLoginAt).toLocaleString() : '—') },
    { key: 'actions', header: '', align: 'right', render: (u) => (
      <div className="row-actions">
        <button disabled={u.id === me?.userId} onClick={() => run(() => updateUserStatus(u.id, !u.active))}>
          {u.active ? 'Deactivate' : 'Activate'}
        </button>
      </div>
    ) },
  ]

  return (
    <main className="page">
      <PageHeader title="User Management" description="Create logins, assign roles and activate or deactivate accounts." />

      <SectionCard title="Create user" icon={UserPlus} tint="blue">
        <form onSubmit={handleCreate} className="inline-form">
          <input type="email" placeholder="Email" value={email} onChange={(e) => setEmail(e.target.value)} required />
          <input
            type="password"
            placeholder="Initial password (min 8)"
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            minLength={8}
            required
          />
          <select value={role} onChange={(e) => setRole(e.target.value as Role)}>
            {ROLES.map((r) => (
              <option key={r}>{r}</option>
            ))}
          </select>
          <button type="submit">Create</button>
        </form>
        {createError && <p className="error">{createError}</p>}
      </SectionCard>

      <SectionCard title="Users" icon={Users} tint="blue">
        {error && <p className="error">{error}</p>}
        <DataTable columns={columns} rows={data ? data.content : null} rowKey={(u) => u.id} emptyText="No users found." />
        {data && <Pager data={data} onPage={setPage} noun="users" />}
      </SectionCard>
    </main>
  )
}
