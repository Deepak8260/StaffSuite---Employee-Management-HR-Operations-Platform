import { useCallback, useEffect, useState, type FormEvent } from 'react'
import { errorMessage } from '../../api/axiosInstance'
import { createUser, listUsers, updateUserRole, updateUserStatus } from '../../api/users'
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

  return (
    <main className="page">
      <h1>User Management</h1>

      <section>
        <h2>Create user</h2>
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
      </section>

      <section>
        <h2>Users</h2>
        {error && <p className="error">{error}</p>}
        <table>
          <thead>
            <tr>
              <th>Email</th>
              <th>Role</th>
              <th>Status</th>
              <th>Last login</th>
              <th></th>
            </tr>
          </thead>
          <tbody>
            {data?.content.map((u) => {
              const isSelf = u.id === me?.userId
              return (
                <tr key={u.id}>
                  <td>{u.email}</td>
                  <td>
                    <select
                      value={u.role}
                      disabled={isSelf}
                      onChange={(e) => run(() => updateUserRole(u.id, e.target.value as Role))}
                    >
                      {ROLES.map((r) => (
                        <option key={r}>{r}</option>
                      ))}
                    </select>
                  </td>
                  <td>{u.active ? 'Active' : 'Inactive'}</td>
                  <td>{u.lastLoginAt ? new Date(u.lastLoginAt).toLocaleString() : '—'}</td>
                  <td>
                    <button disabled={isSelf} onClick={() => run(() => updateUserStatus(u.id, !u.active))}>
                      {u.active ? 'Deactivate' : 'Activate'}
                    </button>
                  </td>
                </tr>
              )
            })}
          </tbody>
        </table>
        {data && (
          <div className="pager">
            <button disabled={page === 0} onClick={() => setPage(page - 1)}>
              Previous
            </button>
            <span>
              Page {data.page + 1} of {Math.max(data.totalPages, 1)} ({data.totalElements} users)
            </span>
            <button disabled={page + 1 >= data.totalPages} onClick={() => setPage(page + 1)}>
              Next
            </button>
          </div>
        )}
      </section>
    </main>
  )
}
