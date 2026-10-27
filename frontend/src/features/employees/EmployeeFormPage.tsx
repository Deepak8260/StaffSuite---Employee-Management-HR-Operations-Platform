import { useEffect, useState, type FormEvent } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { createEmployee, getEmployee, updateEmployee } from '../../api/employees'
import { errorMessage } from '../../api/axiosInstance'
import { useAuth } from '../../hooks/useAuth'
import { useReferenceData } from '../../hooks/useReferenceData'
import type { Role } from '../../types/auth'
import { EDITABLE_STATUSES, type EmployeeInput, type EmploymentStatus } from '../../types/employee'
import { grantableRoles } from '../../utils/roles'

interface FormState {
  fullName: string
  email: string
  phone: string
  address: string
  emergencyContact: string
  dateOfBirth: string
  joiningDate: string
  jobTitle: string
  employmentStatus: EmploymentStatus
  departmentId: string
  designationId: string
  managerId: string
}

const EMPTY: FormState = {
  fullName: '',
  email: '',
  phone: '',
  address: '',
  emergencyContact: '',
  dateOfBirth: '',
  joiningDate: '',
  jobTitle: '',
  employmentStatus: 'ACTIVE',
  departmentId: '',
  designationId: '',
  managerId: '',
}

const today = new Date().toISOString().slice(0, 10)

/** Shared by "New employee" (no :id) and "Edit employee" (/employees/:id/edit). */
export default function EmployeeFormPage() {
  const { id } = useParams()
  const editingId = id ? Number(id) : null
  const { user } = useAuth()
  const navigate = useNavigate()
  const { departments, designations, people } = useReferenceData()
  const roles = grantableRoles(user?.role)

  const [form, setForm] = useState<FormState>(EMPTY)
  const [createAccount, setCreateAccount] = useState(false)
  const [accountPassword, setAccountPassword] = useState('')
  const [accountRole, setAccountRole] = useState<Role>('EMPLOYEE')
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)
  const [loading, setLoading] = useState(editingId !== null)

  useEffect(() => {
    if (editingId === null) return
    getEmployee(editingId)
      .then((e) => {
        setForm({
          fullName: e.fullName,
          email: e.email,
          phone: e.phone ?? '',
          address: e.address ?? '',
          emergencyContact: e.emergencyContact ?? '',
          dateOfBirth: e.dateOfBirth ?? '',
          joiningDate: e.joiningDate,
          jobTitle: e.jobTitle,
          employmentStatus: e.employmentStatus,
          departmentId: e.departmentId ? String(e.departmentId) : '',
          designationId: e.designationId ? String(e.designationId) : '',
          managerId: e.managerId ? String(e.managerId) : '',
        })
      })
      .catch((err) => setError(errorMessage(err, 'Could not load employee')))
      .finally(() => setLoading(false))
  }, [editingId])

  function set<K extends keyof FormState>(key: K, value: FormState[K]) {
    setForm((f) => ({ ...f, [key]: value }))
  }

  const designationOptions = designations.filter(
    (d) => !form.departmentId || d.departmentId === null || d.departmentId === Number(form.departmentId),
  )
  const managerOptions = people.filter((p) => p.id !== editingId)

  async function handleSubmit(e: FormEvent) {
    e.preventDefault()
    setError(null)
    const input: EmployeeInput = {
      fullName: form.fullName.trim(),
      email: form.email.trim(),
      phone: form.phone.trim(),
      address: form.address.trim(),
      emergencyContact: form.emergencyContact.trim(),
      dateOfBirth: form.dateOfBirth || undefined,
      joiningDate: form.joiningDate,
      jobTitle: form.jobTitle.trim(),
      employmentStatus: form.employmentStatus,
      departmentId: Number(form.departmentId),
      designationId: form.designationId ? Number(form.designationId) : null,
      managerId: form.managerId ? Number(form.managerId) : null,
    }
    if (editingId === null && createAccount) {
      input.userAccount = { password: accountPassword, role: accountRole }
    }
    setBusy(true)
    try {
      const saved = editingId === null ? await createEmployee(input) : await updateEmployee(editingId, input)
      navigate(`/employees/${saved.id}`, { replace: true })
    } catch (err) {
      setError(errorMessage(err, 'Could not save employee'))
    } finally {
      setBusy(false)
    }
  }

  if (loading) return <main className="page"><p>Loading…</p></main>

  return (
    <main className="page">
      <h1>{editingId === null ? 'New employee' : 'Edit employee'}</h1>
      {departments.length === 0 && (
        <p className="error">
          No departments exist yet. <Link to="/admin/departments">Create a department</Link> first.
        </p>
      )}
      <form onSubmit={handleSubmit} className="form grid">
        <label>Full name
          <input value={form.fullName} onChange={(e) => set('fullName', e.target.value)} maxLength={160} required />
        </label>
        <label>Email
          <input type="email" value={form.email} onChange={(e) => set('email', e.target.value)} required />
        </label>
        <label>Phone
          <input value={form.phone} onChange={(e) => set('phone', e.target.value)} maxLength={25} />
        </label>
        <label>Date of birth
          <input type="date" value={form.dateOfBirth} max={today} onChange={(e) => set('dateOfBirth', e.target.value)} />
        </label>
        <label>Joining date
          <input type="date" value={form.joiningDate} max={today} onChange={(e) => set('joiningDate', e.target.value)} required />
        </label>
        <label>Job title
          <input value={form.jobTitle} onChange={(e) => set('jobTitle', e.target.value)} maxLength={100} required />
        </label>
        <label>Employment status
          <select value={form.employmentStatus} onChange={(e) => set('employmentStatus', e.target.value as EmploymentStatus)}>
            {EDITABLE_STATUSES.map((s) => <option key={s}>{s}</option>)}
          </select>
        </label>
        <label>Department
          <select value={form.departmentId} onChange={(e) => set('departmentId', e.target.value)} required>
            <option value="">Select…</option>
            {departments.map((d) => <option key={d.id} value={d.id}>{d.name}</option>)}
          </select>
        </label>
        <label>Designation
          <select value={form.designationId} onChange={(e) => set('designationId', e.target.value)}>
            <option value="">None</option>
            {designationOptions.map((d) => <option key={d.id} value={d.id}>{d.name}</option>)}
          </select>
        </label>
        <label>Manager
          <select value={form.managerId} onChange={(e) => set('managerId', e.target.value)}>
            <option value="">None</option>
            {managerOptions.map((p) => <option key={p.id} value={p.id}>{p.name}</option>)}
          </select>
        </label>
        <label className="wide">Address
          <input value={form.address} onChange={(e) => set('address', e.target.value)} maxLength={500} />
        </label>
        <label className="wide">Emergency contact
          <input value={form.emergencyContact} onChange={(e) => set('emergencyContact', e.target.value)} maxLength={150} />
        </label>

        {editingId === null && (
          <fieldset className="wide">
            <legend>Login account (optional)</legend>
            <label className="check">
              <input type="checkbox" checked={createAccount} onChange={(e) => setCreateAccount(e.target.checked)} />
              Create a login for this employee (uses the email above)
            </label>
            {createAccount && (
              <div className="inline-form">
                <input
                  type="password"
                  placeholder="Initial password (min 8)"
                  value={accountPassword}
                  onChange={(e) => setAccountPassword(e.target.value)}
                  minLength={8}
                  required
                />
                <select value={accountRole} onChange={(e) => setAccountRole(e.target.value as Role)}>
                  {roles.map((r) => <option key={r}>{r}</option>)}
                </select>
              </div>
            )}
          </fieldset>
        )}

        {error && <p className="error wide">{error}</p>}
        <div className="wide actions">
          <button type="submit" disabled={busy}>{busy ? 'Saving…' : 'Save'}</button>
          <Link to={editingId === null ? '/employees' : `/employees/${editingId}`}>Cancel</Link>
        </div>
      </form>
    </main>
  )
}
