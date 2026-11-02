import { Users } from 'lucide-react'
import { Link } from 'react-router-dom'
import type { Tint } from '../../components/ui/tints'
import type { OrgNode } from '../../types/organization'
import { deptName } from './orgData'
import OrgAvatar from './OrgAvatar'

interface Shared {
  collapsed: Set<number>
  onToggle: (id: number) => void
  /** null = no search or filter is active, so every node is shown. */
  visible: Set<number> | null
  matches: Set<number>
  tint: (department: string) => Tint
}

function Card({ node, compact, shared, open }: { node: OrgNode; compact: boolean; shared: Shared; open: boolean }) {
  const kids = node.children.length
  const dept = deptName(node)
  const t = shared.tint(dept)
  return (
    <div className={`oc-card ${compact ? 'compact' : ''} ${kids > 0 ? 'has-count' : ''} ${shared.matches.has(node.id) ? 'match' : ''}`}>
      <OrgAvatar id={node.id} name={node.fullName} hasPhoto={node.hasPhoto} tint={t} size={compact ? 'sm' : 'md'} />
      <div className="oc-card-text">
        <Link to={`/employees/${node.id}/profile`} className="oc-name">{node.fullName}</Link>
        <span className="oc-title">{node.jobTitle}</span>
        <span className={`oc-dept tint-${t}`}>{dept}</span>
      </div>
      {kids > 0 && (
        <button type="button" className="oc-count" aria-expanded={open} aria-label={`${open ? 'Hide' : 'Show'} ${kids} direct report${kids === 1 ? '' : 's'} of ${node.fullName}`}
          title={`${kids} direct report${kids === 1 ? '' : 's'}`} onClick={() => shared.onToggle(node.id)}>
          <Users size={13} aria-hidden="true" /> {kids}
        </button>
      )}
    </div>
  )
}

/** One person and, below them, their reports: the top two levels spread out as a tree, deeper levels stack in a list. */
function Branch({ node, depth, shared }: { node: OrgNode; depth: number; shared: Shared }) {
  const searching = shared.visible !== null
  const open = searching || !shared.collapsed.has(node.id)
  const kids = node.children.filter((c) => !shared.visible || shared.visible.has(c.id))
  return (
    <li className="oc-item">
      <Card node={node} compact={depth >= 2} shared={shared} open={open} />
      {open && kids.length > 0 && (
        <ul className={depth === 0 ? 'oc-row' : 'oc-stack'}>
          {kids.map((c) => <Branch key={c.id} node={c} depth={depth + 1} shared={shared} />)}
        </ul>
      )}
    </li>
  )
}

export default function HierarchyView({ roots, ...shared }: { roots: OrgNode[] } & Shared) {
  const shown = roots.filter((r) => !shared.visible || shared.visible.has(r.id))
  if (shown.length === 0) return <p className="hint oc-empty">No one matches your search.</p>
  return (
    <div className="oc-scroll">
      <ul className={`oc-forest ${shown.length === 1 ? 'single' : ''}`}>
        {shown.map((r) => <Branch key={r.id} node={r} depth={0} shared={shared} />)}
      </ul>
    </div>
  )
}
