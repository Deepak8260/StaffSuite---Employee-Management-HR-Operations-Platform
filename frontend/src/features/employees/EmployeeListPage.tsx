import { useCallback, useEffect, useState, type FormEvent } from 'react'
import { Link } from 'react-router-dom'
import { listEmployees } from '../../api/employees'
import { errorMessage } from '../../api/axiosInstance'
import Pager from '../../components/Pager'
import { useAuth } from '../../hooks/useAuth'
import { useReferenceData } from '../../hooks/useReferenceData'
import type { PageResponse } from '../../types/auth'
import { ALL_STATUSES, type Employee, type EmployeeFilters, type EmploymentStatus } from '../../types/employee'
import { canManageEmployees } from '../../utils/roles'

const PAGE_SIZE = 10

export default function EmployeeListPage() {
  const { user } = useAuth()
  const canManage = canManageEmployees(user?.role)
  const { departments, people } = useReferenceData()

  const [departmentId, setDepartmentId] = useState('')
  const [status, setStatus] = useState('')
  const [managerId, setManagerId] = useState('')
  const [search, setSearch] = useState('')
  const [includeOffboarded, setIncludeOffboarded] = useState(false)
  // Filters are applied on submit so typing in the search box does not fire a request per keystroke.
  const [applied, setApplied] = useState<EmployeeFilters>({})
  const [page, setPage] = useState(0)
  const [data, setData] = useState<PageResponse<Employee> | null>(null)
  const [error, setError] = useState<string | null>(null)

  const load = useCallback(async () => {
    try {
      setData(await listEmployees(applied, page, PAGE_SIZE))
      setError(null)
    } catch (err) {
      setError(errorMessage(err, 'Could not load employees'))
    }
  }, [applied, page])

  useEffect(() => {
    // eslint-disable-next-line react-hooks/set-state-in-effect
    void load()
  }, [load])

  function applyFilters(e: FormEvent) {
    e.preventDefault()
    setPage(0)
    setApplied({
      departmentId: departmentId ? Number(departmentId) : undefined,
      status: (status || undefined) as EmploymentStatus | undefined,
      // Managers are limited to their own team by the backend, so the filter is not offered to them.
      managerId: canManage && managerId ? Number(managerId) : undefined,
      q: search.trim() || undefined,
      includeOffboarded: canManage && includeOffboarded ? true : undefined,
    })
  }

  function reset() {
    setDepartmentId('')
    setStatus('')
    setManagerId('')
    setSearch('')
    setIncludeOffboarded(false)
    setPage(0)
    setApplied({})
  }

  return (
    <main className="page">
      <div className="page-head">
        <h1>{canManage ? 'Employees' : 'My team'}</h1>
        {canManage && <Link to="/employees/new" className="button">+ New employee</Link>}
      </div>

      <form onSubmit={applyFilters} className="inline-form filters">
        <input placeholder="Search name or code" value={search} onChange={(e) => setSearch(e.target.value)} />
        <select value={departmentId} onChange={(e) => setDepartmentId(e.target.value)}>
          <option value="">All departments</option>
          {departments.map((d) => (
            <option key={d.id} value={d.id}>{d.name}</option>
          ))}
        </select>
        <select value={status} onChange={(e) => setStatus(e.target.value)}>
          <option value="">All statuses</option>
          {ALL_STATUSES.filter((s) => canManage || s !== 'TERMINATED').map((s) => (
            <option key={s}>{s}</option>
          ))}
        </select>
        {canManage && (
          <select value={managerId} onChange={(e) => setManagerId(e.target.value)}>
            <option value="">Any manager</option>
            {people.map((p) => (
              <option key={p.id} value={p.id}>{p.name}</option>
            ))}
          </select>
        )}
        {canManage && (
          <label className="check">
            <input type="checkbox" checked={includeOffboarded} onChange={(e) => setIncludeOffboarded(e.target.checked)} />
            Include offboarded
          </label>
        )}
        <button type="submit">Apply</button>
        <button type="button" onClick={reset}>Reset</button>
      </form>

      {error && <p className="error">{error}</p>}
      <table>
        <thead>
          <tr>
            <th>Code</th>
            <th>Name</th>
            <th>Job title</th>
            <th>Department</th>
            <th>Manager</th>
            <th>Status</th>
          </tr>
        </thead>
        <tbody>
          {data?.content.map((e) => (
            <tr key={e.id} className={e.deleted ? 'muted' : undefined}>
              <td>{e.employeeCode}</td>
              <td><Link to={`/employees/${e.id}`}>{e.fullName}</Link></td>
              <td>{e.jobTitle}</td>
              <td>{e.departmentName ?? '—'}</td>
              <td>{e.managerName ?? '—'}</td>
              <td>{e.employmentStatus}{e.deleted ? ' (offboarded)' : ''}</td>
            </tr>
          ))}
          {data && data.content.length === 0 && (
            <tr><td colSpan={6}>No employees found.</td></tr>
          )}
        </tbody>
      </table>
      {data && <Pager data={data} onPage={setPage} noun="employees" />}
    </main>
  )
}
