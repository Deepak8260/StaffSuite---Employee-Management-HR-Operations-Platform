import { Building2, Maximize2, Minimize2, Search, SlidersHorizontal, User, UserCog, Users } from 'lucide-react'
import { useEffect, useMemo, useState } from 'react'
import { errorMessage } from '../../api/axiosInstance'
import { getOrgChart } from '../../api/organization'
import PageHeader from '../../components/ui/PageHeader'
import StatCard from '../../components/ui/StatCard'
import type { OrgNode } from '../../types/organization'
import DepartmentView from './DepartmentView'
import HierarchyView from './HierarchyView'
import { clearPhotoCache } from './OrgAvatar'
import { flatten, deptName, ids, statsOf, tintMap, visibleIds } from './orgData'

type View = 'hierarchy' | 'department'

/** Who reports to whom (hierarchy) and who is in which department, for the people the signed-in user may see. */
export default function OrgChartPage() {
  const [roots, setRoots] = useState<OrgNode[] | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [view, setView] = useState<View>('hierarchy')
  const [collapsed, setCollapsed] = useState<Set<number>>(new Set())
  const [query, setQuery] = useState('')
  const [department, setDepartment] = useState('')
  const [filterOpen, setFilterOpen] = useState(false)

  useEffect(() => {
    clearPhotoCache()
    getOrgChart().then(setRoots).catch((err) => setError(errorMessage(err, 'Could not load the org chart')))
    return clearPhotoCache
  }, [])

  const stats = useMemo(() => (roots ? statsOf(roots) : null), [roots])
  const tint = useMemo(() => tintMap(roots ?? []), [roots])
  const departments = useMemo(() => [...new Set(flatten(roots ?? []).map((f) => deptName(f.node)))].sort((a, b) => a.localeCompare(b)), [roots])
  const { visible, matches } = useMemo(() => visibleIds(roots ?? [], query, department), [roots, query, department])
  const active = visible !== null

  function toggle(id: number) {
    setCollapsed((prev) => {
      const next = new Set(prev)
      if (next.has(id)) next.delete(id)
      else next.add(id)
      return next
    })
  }

  return (
    <main className="page org-page">
      <PageHeader
        title="Organization Chart"
        description="Visualize reporting structure and who reports to whom across the company."
        actions={roots && view === 'hierarchy' && (
          <>
            <button type="button" className="secondary" onClick={() => setCollapsed(new Set())}><Maximize2 size={14} aria-hidden="true" /> Expand all</button>
            <button type="button" className="secondary" onClick={() => setCollapsed(new Set(ids(roots)))}><Minimize2 size={14} aria-hidden="true" /> Collapse all</button>
          </>
        )}
      />

      {error && <p className="error">{error}</p>}
      {!roots && !error && <p className="hint">Loading…</p>}

      {roots && stats && (
        <>
          <div className="org-stats">
            <StatCard icon={Users} tint="purple" label="Total Employees" value={stats.employees} />
            <StatCard icon={Building2} tint="green" label="Departments" value={stats.departments} />
            <StatCard icon={UserCog} tint="blue" label="Managers" value={stats.managers} />
            <StatCard icon={User} tint="orange" label="Individual Contributors" value={stats.contributors} />
          </div>

          {roots.length === 0 ? <p>No employees yet.</p> : (
            <section className="org-panel">
              <div className="org-toolbar">
                <div className="org-tabs" role="tablist" aria-label="Chart view">
                  <button type="button" role="tab" aria-selected={view === 'hierarchy'} className={view === 'hierarchy' ? 'active' : ''} onClick={() => setView('hierarchy')}>Hierarchy View</button>
                  <button type="button" role="tab" aria-selected={view === 'department'} className={view === 'department' ? 'active' : ''} onClick={() => setView('department')}>Department View</button>
                </div>
                <div className="org-search-wrap">
                  <label className="org-search">
                    <Search size={15} aria-hidden="true" />
                    <input type="search" placeholder="Search by name, department or title..." value={query} onChange={(e) => setQuery(e.target.value)} aria-label="Search the chart" />
                  </label>
                  <button type="button" className={`org-filter ${department ? 'on' : ''}`} aria-label="Filter by department" aria-expanded={filterOpen} title="Filter by department" onClick={() => setFilterOpen((o) => !o)}>
                    <SlidersHorizontal size={15} aria-hidden="true" />
                  </button>
                </div>
              </div>
              {filterOpen && (
                <div className="org-filter-row">
                  <label>Department
                    <select value={department} onChange={(e) => setDepartment(e.target.value)}>
                      <option value="">All departments</option>
                      {departments.map((d) => <option key={d} value={d}>{d}</option>)}
                    </select>
                  </label>
                  {(department || query) && <button type="button" className="link-button" onClick={() => { setDepartment(''); setQuery('') }}>Clear</button>}
                </div>
              )}

              {view === 'hierarchy'
                ? <HierarchyView roots={roots} collapsed={collapsed} onToggle={toggle} visible={visible} matches={matches} tint={tint} />
                : <DepartmentView roots={roots} matches={matches} active={active} tint={tint} />}
            </section>
          )}

          {roots.length > 0 && view === 'hierarchy' && (
            <section className="org-legend" aria-label="Department colours">
              <strong>Departments</strong>
              {departments.map((d) => <span key={d} className={`oc-dept tint-${tint(d)}`}><i aria-hidden="true" />{d}</span>)}
            </section>
          )}
        </>
      )}
    </main>
  )
}
