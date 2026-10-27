import { useCallback, useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { getTeam } from '../../api/employees'
import { errorMessage } from '../../api/axiosInstance'
import Pager from '../../components/Pager'
import { useAuth } from '../../hooks/useAuth'
import type { PageResponse } from '../../types/auth'
import type { Employee } from '../../types/employee'

const PAGE_SIZE = 10

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
        <h1>My team</h1>
        <p>No employee record is linked to your login, so you have no direct reports.</p>
      </main>
    )
  }

  return (
    <main className="page">
      <h1>My team</h1>
      {error && <p className="error">{error}</p>}
      <table>
        <thead>
          <tr><th>Code</th><th>Name</th><th>Job title</th><th>Department</th><th>Status</th></tr>
        </thead>
        <tbody>
          {data?.content.map((e) => (
            <tr key={e.id}>
              <td>{e.employeeCode}</td>
              <td><Link to={`/employees/${e.id}`}>{e.fullName}</Link></td>
              <td>{e.jobTitle}</td>
              <td>{e.departmentName ?? '—'}</td>
              <td>{e.employmentStatus}</td>
            </tr>
          ))}
          {data && data.content.length === 0 && <tr><td colSpan={5}>You have no direct reports.</td></tr>}
        </tbody>
      </table>
      {data && <Pager data={data} onPage={setPage} noun="reports" />}
    </main>
  )
}
