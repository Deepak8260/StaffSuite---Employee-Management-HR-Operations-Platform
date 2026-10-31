import { Briefcase } from 'lucide-react'
import { useEffect, useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { getEmployee, offboardEmployee } from '../../api/employees'
import { errorMessage } from '../../api/axiosInstance'
import SectionCard from '../../components/ui/SectionCard'
import { StatusCodePill } from '../../components/ui/StatusPill'
import { useAuth } from '../../hooks/useAuth'
import type { Employee } from '../../types/employee'
import { canManageEmployees, formatDate } from '../../utils/roles'
import ReassignModal from './ReassignModal'

export function EmployeeFields({ employee }: { employee: Employee }) {
  const rows: [string, string][] = [
    ['Employee code', employee.employeeCode],
    ['Email', employee.email],
    ['Phone', employee.phone ?? '—'],
    ['Address', employee.address ?? '—'],
    ['Emergency contact', employee.emergencyContact ?? '—'],
    ['Date of birth', formatDate(employee.dateOfBirth)],
    ['Joining date', formatDate(employee.joiningDate)],
    ['Job title', employee.jobTitle],
    ['Designation', employee.designationName ?? '—'],
    ['Department', employee.departmentName ?? '—'],
    ['Manager', employee.managerName ?? '—'],
    ['Status', employee.employmentStatus + (employee.deleted ? ' (offboarded)' : '')],
  ]
  return (
    <dl className="details">
      {rows.map(([label, value]) => (
        <div key={label}>
          <dt>{label}</dt>
          <dd>{value}</dd>
        </div>
      ))}
    </dl>
  )
}

export default function EmployeeDetailPage() {
  const { id } = useParams()
  const employeeId = Number(id)
  const { user } = useAuth()
  const navigate = useNavigate()
  const [employee, setEmployee] = useState<Employee | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [confirming, setConfirming] = useState(false)
  const [reassigning, setReassigning] = useState(false)
  const [busy, setBusy] = useState(false)

  useEffect(() => {
    getEmployee(employeeId)
      .then(setEmployee)
      .catch((err) => setError(errorMessage(err, 'Could not load employee')))
  }, [employeeId])

  const canManage = canManageEmployees(user?.role)
  const isSelf = user?.employeeId === employeeId

  async function handleOffboard() {
    setBusy(true)
    try {
      setEmployee(await offboardEmployee(employeeId))
      setConfirming(false)
    } catch (err) {
      setError(errorMessage(err, 'Could not offboard employee'))
      setConfirming(false)
    } finally {
      setBusy(false)
    }
  }

  if (error && !employee) {
    return (
      <main className="page">
        <p className="error">{error}</p>
        <button onClick={() => navigate(-1)}>Back</button>
      </main>
    )
  }
  if (!employee) return <main className="page"><p>Loading…</p></main>

  return (
    <main className="page">
      <div className="page-header profile-header">
        <div className="person profile-id">
          <div className="person-text">
            <h1>{employee.fullName}</h1>
            <span className="person-meta">{employee.jobTitle}</span>
            <span className="profile-status">
              <StatusCodePill status={employee.employmentStatus} />
              {employee.deleted && <span className="person-meta"> Offboarded</span>}
            </span>
          </div>
        </div>
        <div className="page-header-actions">
          {isSelf && !employee.deleted && <Link to={`/employees/${employee.id}/profile`} className="button">My profile</Link>}
          {canManage && !employee.deleted && (
            <Link to={`/employees/${employee.id}/edit`} className="button">Edit</Link>
          )}
          {canManage && (
            <Link to={`/admin/documents?employee=${employee.id}`} className="button secondary">Documents</Link>
          )}
          {canManage && !employee.deleted && (
            <button onClick={() => setReassigning(true)}>Reassign</button>
          )}
          {canManage && !employee.deleted && !isSelf && (
            <button className="danger" onClick={() => setConfirming(true)}>Offboard</button>
          )}
        </div>
      </div>
      {error && <p className="error">{error}</p>}
      <SectionCard title="Employee details" icon={Briefcase} tint="blue">
        <EmployeeFields employee={employee} />
      </SectionCard>

      {reassigning && (
        <ReassignModal
          employee={employee}
          onClose={() => setReassigning(false)}
          onDone={(updated) => {
            setEmployee(updated)
            setReassigning(false)
          }}
        />
      )}

      {confirming && (
        <div className="modal-backdrop" role="dialog" aria-modal="true">
          <div className="modal">
            <h2>Offboard {employee.fullName}?</h2>
            <p>
              They will be marked TERMINATED, removed from active lists and their login will be disabled.
              Their records are kept. This cannot be undone from the UI.
            </p>
            <div className="actions">
              <button className="danger" onClick={handleOffboard} disabled={busy}>
                {busy ? 'Offboarding…' : 'Yes, offboard'}
              </button>
              <button onClick={() => setConfirming(false)} disabled={busy}>Cancel</button>
            </div>
          </div>
        </div>
      )}
    </main>
  )
}
