import type { Tint } from '../../components/ui/tints'
import type { OrgNode } from '../../types/organization'

export interface FlatNode {
  node: OrgNode
  depth: number
}

export const NO_DEPARTMENT = 'Others'

const TINTS: Tint[] = ['blue', 'green', 'orange', 'purple', 'red']

export function deptName(node: OrgNode): string {
  return node.departmentName ?? NO_DEPARTMENT
}

/** Every node of the forest with its depth, parents before children. */
export function flatten(nodes: OrgNode[], depth = 0): FlatNode[] {
  return nodes.flatMap((n) => [{ node: n, depth }, ...flatten(n.children, depth + 1)])
}

export interface OrgStats {
  employees: number
  departments: number
  managers: number
  contributors: number
}

export function statsOf(nodes: OrgNode[]): OrgStats {
  const all = flatten(nodes)
  return {
    employees: all.length,
    departments: new Set(all.map((f) => deptName(f.node))).size,
    managers: all.filter((f) => f.node.children.length > 0).length,
    contributors: all.filter((f) => f.node.children.length === 0).length,
  }
}

/** A stable colour for each department; "Others" (no department) is always grey. */
export function tintMap(nodes: OrgNode[]): (name: string) => Tint {
  const names = [...new Set(flatten(nodes).map((f) => deptName(f.node)))].filter((n) => n !== NO_DEPARTMENT).sort((a, b) => a.localeCompare(b))
  const map = new Map(names.map((n, i) => [n, TINTS[i % TINTS.length]]))
  return (name) => map.get(name) ?? 'gray'
}

export function ids(nodes: OrgNode[]): number[] {
  return flatten(nodes).filter((f) => f.node.children.length > 0).map((f) => f.node.id)
}

/** Which nodes to show for a search/department filter: matches plus everyone above them, so each match keeps its context. */
export function visibleIds(nodes: OrgNode[], query: string, department: string): { visible: Set<number> | null; matches: Set<number> } {
  const q = query.trim().toLowerCase()
  if (!q && !department) return { visible: null, matches: new Set() }
  const matches = new Set<number>()
  const visible = new Set<number>()
  const walk = (n: OrgNode): boolean => {
    const text = `${n.fullName} ${n.jobTitle} ${n.departmentName ?? ''} ${n.employeeCode}`.toLowerCase()
    const self = (!q || text.includes(q)) && (!department || deptName(n) === department)
    let below = false
    for (const c of n.children) if (walk(c)) below = true
    if (self) matches.add(n.id)
    if (self || below) visible.add(n.id)
    return self || below
  }
  nodes.forEach(walk)
  return { visible, matches }
}
