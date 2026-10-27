import { useCallback, useEffect, useState, type FormEvent } from 'react'
import { listDirectory } from '../../api/employees'
import { errorMessage } from '../../api/axiosInstance'
import Pager from '../../components/Pager'
import type { PageResponse } from '../../types/auth'
import type { DirectoryEntry } from '../../types/employee'

const PAGE_SIZE = 15

/** Basic company directory (name, title, department) visible to every signed-in user. */
export default function DirectoryPage() {
  const [search, setSearch] = useState('')
  const [applied, setApplied] = useState('')
  const [page, setPage] = useState(0)
  const [data, setData] = useState<PageResponse<DirectoryEntry> | null>(null)
  const [error, setError] = useState<string | null>(null)

  const load = useCallback(async () => {
    try {
      setData(await listDirectory(applied, page, PAGE_SIZE))
      setError(null)
    } catch (err) {
      setError(errorMessage(err, 'Could not load the directory'))
    }
  }, [applied, page])

  useEffect(() => {
    // eslint-disable-next-line react-hooks/set-state-in-effect
    void load()
  }, [load])

  function submit(e: FormEvent) {
    e.preventDefault()
    setPage(0)
    setApplied(search.trim())
  }

  return (
    <main className="page">
      <h1>Directory</h1>
      <form onSubmit={submit} className="inline-form">
        <input placeholder="Search by name" value={search} onChange={(e) => setSearch(e.target.value)} />
        <button type="submit">Search</button>
      </form>
      {error && <p className="error">{error}</p>}
      <table>
        <thead>
          <tr><th>Name</th><th>Job title</th><th>Department</th></tr>
        </thead>
        <tbody>
          {data?.content.map((e) => (
            <tr key={e.id}>
              <td>{e.fullName}</td>
              <td>{e.jobTitle}</td>
              <td>{e.departmentName ?? '—'}</td>
            </tr>
          ))}
          {data && data.content.length === 0 && <tr><td colSpan={3}>No one found.</td></tr>}
        </tbody>
      </table>
      {data && <Pager data={data} onPage={setPage} noun="people" />}
    </main>
  )
}
