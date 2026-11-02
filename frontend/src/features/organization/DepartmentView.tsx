import { BarChart3, Code2, FileText, MoreHorizontal, Settings, Users, type LucideIcon } from 'lucide-react'
import { useState } from 'react'
import { Link } from 'react-router-dom'
import type { Tint } from '../../components/ui/tints'
import type { OrgNode } from '../../types/organization'
import { NO_DEPARTMENT, deptName, flatten } from './orgData'
import OrgAvatar from './OrgAvatar'

const SHOWN = 4

function iconFor(name: string): LucideIcon {
  const n = name.toLowerCase()
  if (n === NO_DEPARTMENT.toLowerCase()) return MoreHorizontal
  if (/(eng|tech|dev|it\b|software)/.test(n)) return Code2
  if (/(sale|market|business)/.test(n)) return BarChart3
  if (/(hr|human|people)/.test(n)) return Users
  if (/(fin|account|payroll)/.test(n)) return FileText
  return Settings
}

interface Member {
  node: OrgNode
  depth: number
}

function Person({ node, tint, tag }: { node: OrgNode; tint: Tint; tag?: boolean }) {
  return (
    <li className="dv-person">
      <OrgAvatar id={node.id} name={node.fullName} hasPhoto={node.hasPhoto} tint={tint} size="sm" />
      <div className="dv-person-text">
        <Link to={`/employees/${node.id}/profile`} className="oc-name">{node.fullName}</Link>
        <span className="oc-title">{node.jobTitle}</span>
      </div>
      {tag && <span className="dv-tag">Manager</span>}
    </li>
  )
}

/** One card per department: its lead first, then the rest of the people in it. */
export default function DepartmentView({ roots, matches, active, tint }: { roots: OrgNode[]; matches: Set<number>; active: boolean; tint: (d: string) => Tint }) {
  const [expanded, setExpanded] = useState<Set<string>>(new Set())

  const groups = new Map<string, Member[]>()
  for (const { node, depth } of flatten(roots)) {
    if (active && !matches.has(node.id)) continue
    const key = deptName(node)
    groups.set(key, [...(groups.get(key) ?? []), { node, depth }])
  }
  const ordered = [...groups.entries()].sort((a, b) => {
    if (a[0] === NO_DEPARTMENT) return 1
    if (b[0] === NO_DEPARTMENT) return -1
    return b[1].length - a[1].length || a[0].localeCompare(b[0])
  })
  if (ordered.length === 0) return <p className="hint oc-empty">No one matches your search.</p>

  return (
    <div className="dv-grid">
      {ordered.map(([name, members]) => {
        // the lead is whoever sits highest in the hierarchy, preferring the one with more reports
        const sorted = [...members].sort((a, b) => a.depth - b.depth || b.node.children.length - a.node.children.length || a.node.fullName.localeCompare(b.node.fullName))
        const [lead, ...rest] = sorted
        rest.sort((a, b) => a.node.fullName.localeCompare(b.node.fullName))
        const open = expanded.has(name)
        const list = open ? rest : rest.slice(0, SHOWN)
        const hidden = rest.length - list.length
        const t = tint(name)
        const Icon = iconFor(name)
        return (
          <section key={name} className={`dv-card tint-${t}`}>
            <header className="dv-head">
              <span className="dv-icon"><Icon size={18} aria-hidden="true" /></span>
              <div>
                <h3>{name}</h3>
                <span className="dv-count">{members.length} employee{members.length === 1 ? '' : 's'}</span>
              </div>
            </header>
            <div className="dv-body">
              <ul className="dv-list lead">
                <Person node={lead.node} tint={t} tag={lead.node.children.length > 0} />
              </ul>
              {list.length > 0 && (
                <ul className="dv-list">
                  {list.map((m) => <Person key={m.node.id} node={m.node} tint={t} />)}
                </ul>
              )}
              {(hidden > 0 || (open && rest.length > SHOWN)) && (
                <div className="dv-more">
                  <span>{hidden > 0 ? `+${hidden} more employee${hidden === 1 ? '' : 's'}` : ''}</span>
                  <button type="button" className="link-button" onClick={() => setExpanded((prev) => {
                    const next = new Set(prev)
                    if (next.has(name)) next.delete(name)
                    else next.add(name)
                    return next
                  })}>{open ? 'Show less' : 'View all →'}</button>
                </div>
              )}
            </div>
          </section>
        )
      })}
    </div>
  )
}
