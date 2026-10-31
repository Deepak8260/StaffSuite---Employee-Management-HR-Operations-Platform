import { useCallback, useEffect, useRef, useState, type FormEvent } from 'react'
import { FileSpreadsheet, Plus, Users } from 'lucide-react'
import { Link, useSearchParams } from 'react-router-dom'
import { listDirectory, listEmployees, offboardEmployee } from '../../api/employees'
import { errorMessage } from '../../api/axiosInstance'
import Pager from '../../components/Pager'
import DataTable, { type Column } from '../../components/ui/DataTable'
import PageHeader from '../../components/ui/PageHeader'
import SectionCard from '../../components/ui/SectionCard'
import StatusPill, { StatusCodePill } from '../../components/ui/StatusPill'
import { useAuth } from '../../hooks/useAuth'
import { useReferenceData } from '../../hooks/useReferenceData'
import type { PageResponse } from '../../types/auth'
import { ALL_STATUSES, type DirectoryEntry, type Employee, type EmployeeFilters, type EmploymentStatus } from '../../types/employee'
import { canManageEmployees } from '../../utils/roles'
import BulkUploadModal from './BulkUploadModal'

const PAGE_SIZE = 10
const MEMORY_KEY = 'staffsuite.employees.list'

interface Remembered { applied: EmployeeFilters; page: number }

/** The list's last filters and page, kept for the session so "Back to Employees" lands where you left. */
function recall(fromUrl: string): Remembered | null {
  if (fromUrl) return null
  try {
    return JSON.parse(sessionStorage.getItem(MEMORY_KEY) ?? 'null') as Remembered | null
  } catch {
    return null
  }
}

/**
 * One Employees page for everyone. Admin and HR get the full management view (GET /employees); other roles get the
 * read-only colleague search, which is the basic-info list every signed-in user is already allowed to see.
 */
export default function EmployeeListPage() {
  const { user } = useAuth()
  const canManage = canManageEmployees(user?.role)
  const { departments, people } = useReferenceData()
  const [params] = useSearchParams()
  const fromUrl = params.get('q') ?? ''

  const remembered = useRef(recall(fromUrl)).current
  const [departmentId, setDepartmentId] = useState(remembered?.applied.departmentId ? String(remembered.applied.departmentId) : '')
  const [status, setStatus] = useState<string>(remembered?.applied.status ?? '')
  const [managerId, setManagerId] = useState(remembered?.applied.managerId ? String(remembered.applied.managerId) : '')
  const [search, setSearch] = useState(fromUrl || (remembered?.applied.q ?? ''))
  const [includeOffboarded, setIncludeOffboarded] = useState(Boolean(remembered?.applied.includeOffboarded))
  // Filters are applied on submit so typing in the search box does not fire a request per keystroke.
  const [applied, setApplied] = useState<EmployeeFilters>(fromUrl ? { q: fromUrl } : (remembered?.applied ?? {}))
  const [page, setPage] = useState(remembered?.page ?? 0)
  const [data, setData] = useState<PageResponse<Employee | DirectoryEntry> | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [bulkOpen, setBulkOpen] = useState(false)
  const [offboarding, setOffboarding] = useState<Employee | null>(null)
  const [busy, setBusy] = useState(false)

  // a new search from the top bar replaces whatever was typed here (but not the state restored on first load)
  const first = useRef(true)
  useEffect(() => {
    if (first.current) {
      first.current = false
      return
    }
    // eslint-disable-next-line react-hooks/set-state-in-effect
    setSearch(fromUrl)
    setApplied(fromUrl ? { q: fromUrl } : {})
    setPage(0)
  }, [fromUrl])

  useEffect(() => {
    try {
      sessionStorage.setItem(MEMORY_KEY, JSON.stringify({ applied, page } satisfies Remembered))
    } catch {
      /* not remembered */
    }
  }, [applied, page])

  const load = useCallback(async () => {
    try {
      setData(canManage
        ? await listEmployees(applied, page, PAGE_SIZE)
        : await listDirectory(applied.q ?? '', page, PAGE_SIZE))
      setError(null)
    } catch (err) {
      setError(errorMessage(err, 'Could not load employees'))
    }
  }, [applied, page, canManage])

  useEffect(() => {
    // eslint-disable-next-line react-hooks/set-state-in-effect
    void load()
  }, [load])

  function applyFilters(e: FormEvent) {
    e.preventDefault()
    setPage(0)
    setApplied(canManage ? {
      departmentId: departmentId ? Number(departmentId) : undefined,
      status: (status || undefined) as EmploymentStatus | undefined,
      managerId: managerId ? Number(managerId) : undefined,
      q: search.trim() || undefined,
      includeOffboarded: includeOffboarded ? true : undefined,
    } : { q: search.trim() || undefined })
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

  async function confirmOffboard() {
    if (!offboarding) return
    setBusy(true)
    try {
      await offboardEmployee(offboarding.id)
      setOffboarding(null)
      await load()
    } catch (err) {
      setError(errorMessage(err, 'Could not offboard employee'))
      setOffboarding(null)
    } finally {
      setBusy(false)
    }
  }

  const manageColumns: Column<Employee>[] = [
    { key: 'code', header: 'Code', render: (e) => e.employeeCode },
    { key: 'name', header: 'Name', render: (e) => (
      <Link to={`/employees/${e.id}/profile`} className="person">
<span className="person-name">{e.fullName}</span>
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
    { key: 'actions', header: 'Actions', render: (e) => (e.deleted ? null : (
      <span className="row-actions">
        <Link to={`/employees/${e.id}/edit`} className="button secondary">Edit</Link>
        {e.id !== user?.employeeId && <button type="button" className="danger" onClick={() => setOffboarding(e)}>Offboard</button>}
      </span>
    )) },
  ]

  const simpleColumns: Column<DirectoryEntry>[] = [
    { key: 'name', header: 'Name', render: (e) => (
      <Link to={`/employees/${e.id}/profile`} className="person"><span className="person-name">{e.fullName}</span></Link>
    ) },
    { key: 'title', header: 'Job title', render: (e) => e.jobTitle },
    { key: 'dept', header: 'Department', render: (e) => e.departmentName ?? '—' },
  ]

  return (
    <main className="page">
      <PageHeader
        title="Employees"
        description={canManage ? 'Search, filter and manage everyone in the company.' : 'Find colleagues by name, job title or department.'}
        actions={canManage && (
          <>
            <button type="button" className="secondary" onClick={() => setBulkOpen(true)}>
              <FileSpreadsheet size={16} aria-hidden="true" /> Bulk Upload
            </button>
            <Link to="/employees/new" className="button"><Plus size={16} aria-hidden="true" /> New employee</Link>
          </>
        )}
      />

      {canManage ? (
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
              {ALL_STATUSES.map((s) => (
                <option key={s}>{s}</option>
              ))}
            </select>
            <select value={managerId} onChange={(e) => setManagerId(e.target.value)}>
              <option value="">Any manager</option>
              {people.map((p) => (
                <option key={p.id} value={p.id}>{p.name}</option>
              ))}
            </select>
            <label className="check">
              <input type="checkbox" checked={includeOffboarded} onChange={(e) => setIncludeOffboarded(e.target.checked)} />
              Include offboarded
            </label>
            <button type="submit">Apply</button>
            <button type="button" onClick={reset}>Reset</button>
          </form>
        </SectionCard>
      ) : null}

      <SectionCard title={canManage ? 'Employees' : 'People'} icon={Users} tint="blue"
        actions={canManage ? undefined : (
          <form onSubmit={applyFilters} className="inline-form">
            <input placeholder="Search by name" value={search} onChange={(e) => setSearch(e.target.value)} />
            <button type="submit">Search</button>
          </form>
        )}>
        {error && <p className="error">{error}</p>}
        {canManage ? (
          <DataTable columns={manageColumns} rows={data ? (data.content as Employee[]) : null} rowKey={(e) => e.id}
            rowClassName={(e) => (e.deleted ? 'muted' : undefined)} emptyText="No employees found." />
        ) : (
          <DataTable columns={simpleColumns} rows={data ? (data.content as DirectoryEntry[]) : null} rowKey={(e) => e.id}
            emptyText="No one found." />
        )}
        {data && <Pager data={data} onPage={setPage} noun="employees" />}
      </SectionCard>

      {bulkOpen && <BulkUploadModal onClose={() => setBulkOpen(false)} onCreated={() => { setPage(0); void load() }} />}

      {offboarding && (
        <div className="modal-backdrop" role="dialog" aria-modal="true">
          <div className="modal">
            <h2>Offboard {offboarding.fullName}?</h2>
            <p>
              They will be marked TERMINATED, removed from active lists and their login will be disabled.
              Their records are kept. This cannot be undone from the UI.
            </p>
            <div className="actions">
              <button className="danger" onClick={confirmOffboard} disabled={busy}>{busy ? 'Offboarding…' : 'Yes, offboard'}</button>
              <button onClick={() => setOffboarding(null)} disabled={busy}>Cancel</button>
            </div>
          </div>
        </div>
      )}
    </main>
  )
}
