import { Network } from 'lucide-react'
import { useEffect, useMemo, useState } from 'react'
import { Link } from 'react-router-dom'
import { errorMessage } from '../../api/axiosInstance'
import { getOrgChart } from '../../api/organization'
import AvatarInitials from '../../components/ui/AvatarInitials'
import PageHeader from '../../components/ui/PageHeader'
import SectionCard from '../../components/ui/SectionCard'
import { StatusCodePill } from '../../components/ui/StatusPill'
import { useAuth } from '../../hooks/useAuth'
import type { OrgNode } from '../../types/organization'
import { canManageEmployees } from '../../utils/roles'

function idsWithChildren(nodes: OrgNode[]): number[] {
  return nodes.flatMap((n) => (n.children.length ? [n.id, ...idsWithChildren(n.children)] : []))
}

function countAll(nodes: OrgNode[]): number {
  return nodes.reduce((sum, n) => sum + 1 + countAll(n.children), 0)
}

interface NodeProps {
  node: OrgNode
  collapsed: Set<number>
  onToggle: (id: number) => void
  linkable: (id: number) => boolean
}

function OrgTreeNode({ node, collapsed, onToggle, linkable }: NodeProps) {
  const hasChildren = node.children.length > 0
  const isCollapsed = collapsed.has(node.id)
  return (
    <li>
      <div className="org-node">
        {hasChildren ? (
          <button
            className="org-toggle"
            onClick={() => onToggle(node.id)}
            aria-expanded={!isCollapsed}
            aria-label={isCollapsed ? `Expand ${node.fullName}` : `Collapse ${node.fullName}`}
          >
            {isCollapsed ? '▸' : '▾'}
          </button>
        ) : (
          <span className="org-toggle spacer" />
        )}
        <span className="person">
          <AvatarInitials name={node.fullName} size="sm" />
          <span className="person-text">
            <strong className="person-name">{linkable(node.id) ? <Link to={`/employees/${node.id}/profile`}>{node.fullName}</Link> : node.fullName}</strong>
            <span className="org-meta person-meta">
              {node.jobTitle}
              {node.departmentName ? ` · ${node.departmentName}` : ''}
              {hasChildren ? ` · ${node.children.length} direct report${node.children.length === 1 ? '' : 's'}` : ''}
            </span>
          </span>
          {node.employmentStatus !== 'ACTIVE' && <StatusCodePill status={node.employmentStatus} />}
        </span>
      </div>
      {hasChildren && !isCollapsed && (
        <ul>
          {node.children.map((c) => (
            <OrgTreeNode key={c.id} node={c} collapsed={collapsed} onToggle={onToggle} linkable={linkable} />
          ))}
        </ul>
      )}
    </li>
  )
}

export default function OrgChartPage() {
  const { user } = useAuth()
  const [roots, setRoots] = useState<OrgNode[] | null>(null)
  const [collapsed, setCollapsed] = useState<Set<number>>(new Set())
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    getOrgChart()
      .then(setRoots)
      .catch((err) => setError(errorMessage(err, 'Could not load the org chart')))
  }, [])

  const total = useMemo(() => (roots ? countAll(roots) : 0), [roots])

  // Only link to detail pages the backend would actually allow (Admin/HR, or your own record).
  const linkable = (id: number) => canManageEmployees(user?.role) || user?.employeeId === id

  function toggle(id: number) {
    setCollapsed((prev) => {
      const next = new Set(prev)
      if (next.has(id)) next.delete(id)
      else next.add(id)
      return next
    })
  }

  return (
    <main className="page">
      <PageHeader
        title="Org chart"
        description="Who reports to whom across the company."
        actions={roots && (
          <>
            <button onClick={() => setCollapsed(new Set())}>Expand all</button>
            <button onClick={() => setCollapsed(new Set(idsWithChildren(roots)))}>Collapse all</button>
          </>
        )}
      />
      <SectionCard title="Reporting structure" icon={Network} tint="blue">
        {error && <p className="error">{error}</p>}
        {roots && <p className="hint">{total} active employee{total === 1 ? '' : 's'}</p>}
        {roots && roots.length === 0 && <p>No employees yet.</p>}
        <ul className="org-tree">
          {roots?.map((r) => (
            <OrgTreeNode key={r.id} node={r} collapsed={collapsed} onToggle={toggle} linkable={linkable} />
          ))}
        </ul>
      </SectionCard>
    </main>
  )
}
