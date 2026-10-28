import { useCallback, useEffect, useState, type FormEvent } from 'react'
import { Plus, Users } from 'lucide-react'
import { Link } from 'react-router-dom'
import { listEmployees } from '../../api/employees'
import { errorMessage } from '../../api/axiosInstance'
import Pager from '../../components/Pager'
import AvatarInitials from '../../components/ui/AvatarInitials'
import DataTable, { type Column } from '../../components/ui/DataTable'
import PageHeader from '../../components/ui/PageHeader'
import SectionCard from '../../components/ui/SectionCard'
import StatusPill, { StatusCodePill } from '../../components/ui/StatusPill'
import { useAuth } from '../../hooks/useAuth'
import { useReferenceData } from '../../hooks/useReferenceData'
import type { PageResponse } from '../../types/auth'
import { ALL_STATUSES, type Employee, type EmployeeFilters, type EmploymentStatus } from '../../types/employee'
import { canManageEmployees } from '../../utils/roles'

const PAGE_SIZE = 10

const columns: Column<Employee>[] = [
  { key: 'code', header: 'Code', render: (e) => e.employeeCode },
  { key: 'name', header: 'Name', render: (e) => (
    <Link to={`/employees/${e.id}`} className="person">
      <AvatarInitials name={e.fullName} size="sm" /><span className="person-name">{e.fullName}</span>
    </Link>
  ) },
  { key: 'title', header: 'Job title', render: (e) => e.jobTitle },
  { key: 'dept', header: 'Department', render: (e) => e.departmentName ?? '—' },
  { key: 'mgr', header: 'Manager', render: (e) => e.managerName ?? '—' },
  { key: 'status', header: 'Status', render: (e) => (
    <>
      <StatusCodePill status={e.employmentStatus} />
      {e.deleted && <> <StatusPill tone="neutral">Offboarded</StatusPill></>}
    </>
  ) },
]

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
      <PageHeader
        title={canManage ? 'Employees' : 'My team'}
        description={canManage ? 'Search, filter and manage everyone in the company.' : 'People on your team.'}
        actions={canManage && <Link to="/employees/new" className="button"><Plus size={16} aria-hidden="true" /> New employee</Link>}
      />

      <SectionCard title="Filters" icon={Users} tint="blue">
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
      </SectionCard>

      <SectionCard title="Employees" icon={Users} tint="blue">
        {error && <p className="error">{error}</p>}
        <DataTable columns={columns} rows={data ? data.content : null} rowKey={(e) => e.id}
          rowClassName={(e) => (e.deleted ? 'muted' : undefined)} emptyText="No employees found." />
        {data && <Pager data={data} onPage={setPage} noun="employees" />}
      </SectionCard>
    </main>
  )
}
