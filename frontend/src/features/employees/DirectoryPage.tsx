import { Search } from 'lucide-react'
import { useCallback, useEffect, useState, type FormEvent } from 'react'
import { useSearchParams } from 'react-router-dom'
import { listDirectory } from '../../api/employees'
import { errorMessage } from '../../api/axiosInstance'
import Pager from '../../components/Pager'
import AvatarInitials from '../../components/ui/AvatarInitials'
import DataTable, { type Column } from '../../components/ui/DataTable'
import PageHeader from '../../components/ui/PageHeader'
import SectionCard from '../../components/ui/SectionCard'
import type { PageResponse } from '../../types/auth'
import type { DirectoryEntry } from '../../types/employee'

const PAGE_SIZE = 15

const columns: Column<DirectoryEntry>[] = [
  { key: 'name', header: 'Name', render: (e) => (
    <span className="person"><AvatarInitials name={e.fullName} size="sm" /><span className="person-name">{e.fullName}</span></span>
  ) },
  { key: 'title', header: 'Job title', render: (e) => e.jobTitle },
  { key: 'dept', header: 'Department', render: (e) => e.departmentName ?? '—' },
]

/** Basic company directory (name, title, department) visible to every signed-in user. The top-bar search lands here with ?q=. */
export default function DirectoryPage() {
  const [params] = useSearchParams()
  const fromUrl = params.get('q') ?? ''
  const [search, setSearch] = useState(fromUrl)
  const [applied, setApplied] = useState(fromUrl)
  const [page, setPage] = useState(0)
  const [data, setData] = useState<PageResponse<DirectoryEntry> | null>(null)
  const [error, setError] = useState<string | null>(null)

  // a new search from the top bar replaces whatever was typed here
  useEffect(() => {
    // eslint-disable-next-line react-hooks/set-state-in-effect
    setSearch(fromUrl)
    setApplied(fromUrl)
    setPage(0)
  }, [fromUrl])

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
      <PageHeader title="Directory" description="Find colleagues by name, job title or department." />
      <SectionCard title="People" icon={Search} tint="blue"
        actions={(
          <form onSubmit={submit} className="inline-form">
            <input placeholder="Search by name" value={search} onChange={(e) => setSearch(e.target.value)} />
            <button type="submit">Search</button>
          </form>
        )}>
        {error && <p className="error">{error}</p>}
        <DataTable columns={columns} rows={data ? data.content : null} rowKey={(e) => e.id} emptyText="No one found." />
        {data && <Pager data={data} onPage={setPage} noun="people" />}
      </SectionCard>
    </main>
  )
}
