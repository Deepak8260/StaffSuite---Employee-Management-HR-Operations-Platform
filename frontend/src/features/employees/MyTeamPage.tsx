import { useCallback, useEffect, useState } from 'react'
import { Users } from 'lucide-react'
import { Link } from 'react-router-dom'
import { getTeam } from '../../api/employees'
import { errorMessage } from '../../api/axiosInstance'
import Pager from '../../components/Pager'
import DataTable, { type Column } from '../../components/ui/DataTable'
import PageHeader from '../../components/ui/PageHeader'
import SectionCard from '../../components/ui/SectionCard'
import { StatusCodePill } from '../../components/ui/StatusPill'
import { useAuth } from '../../hooks/useAuth'
import type { PageResponse } from '../../types/auth'
import type { Employee } from '../../types/employee'

const PAGE_SIZE = 10

const columns: Column<Employee>[] = [
  { key: 'code', header: 'Code', render: (e) => e.employeeCode },
  { key: 'name', header: 'Name', render: (e) => (
    <Link to={`/employees/${e.id}`} className="person">
<span className="person-name">{e.fullName}</span>
    </Link>
  ) },
  { key: 'title', header: 'Job title', render: (e) => e.jobTitle },
  { key: 'dept', header: 'Department', render: (e) => e.departmentName ?? '—' },
  { key: 'status', header: 'Status', render: (e) => <StatusCodePill status={e.employmentStatus} /> },
]

export default function MyTeamPage() {
  const { user } = useAuth()
  const employeeId = user?.employeeId ?? null
  const [page, setPage] = useState(0)
  const [data, setData] = useState<PageResponse<Employee> | null>(null)
  const [error, setError] = useState<string | null>(null)

  const load = useCallback(async () => {
    if (employeeId === null) return
    try {
      setData(await getTeam(employeeId, page, PAGE_SIZE))
      setError(null)
    } catch (err) {
      setError(errorMessage(err, 'Could not load your team'))
    }
  }, [employeeId, page])

  useEffect(() => {
    // eslint-disable-next-line react-hooks/set-state-in-effect
    void load()
  }, [load])

  if (employeeId === null) {
    return (
      <main className="page">
        <PageHeader title="My team" description="The people who report to you." />
        <p>No employee record is linked to your login, so you have no direct reports.</p>
      </main>
    )
  }

  return (
    <main className="page">
      <PageHeader title="My team" description="The people who report to you." />
      <SectionCard title="Direct reports" icon={Users} tint="blue">
        {error && <p className="error">{error}</p>}
        <DataTable columns={columns} rows={data ? data.content : null} rowKey={(e) => e.id} emptyText="You have no direct reports." />
        {data && <Pager data={data} onPage={setPage} noun="reports" />}
      </SectionCard>
    </main>
  )
}
