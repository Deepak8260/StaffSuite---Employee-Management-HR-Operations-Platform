import { useEffect, useMemo, useState } from 'react'
import { listDirectory } from '../../api/employees'
import { errorMessage } from '../../api/axiosInstance'
import { getOrgChart, listDepartments, reassignEmployee } from '../../api/organization'
import type { Employee } from '../../types/employee'
import type { Department, OrgNode, ReassignInput } from '../../types/organization'

interface Person {
  id: number
  label: string
}

function findNode(nodes: OrgNode[], id: number): OrgNode | null {
  for (const n of nodes) {
    if (n.id === id) return n
    const hit = findNode(n.children, id)
    if (hit) return hit
  }
  return null
}

function collectIds(node: OrgNode, into: Set<number>) {
  into.add(node.id)
  node.children.forEach((c) => collectIds(c, into))
}

interface Props {
  employee: Employee
  onClose: () => void
  onDone: (updated: Employee) => void
}

/** Change an employee's department and/or manager, blocking choices that would create a loop. */
export default function ReassignModal({ employee, onClose, onDone }: Props) {
  const [departments, setDepartments] = useState<Department[]>([])
  const [people, setPeople] = useState<Person[]>([])
  const [descendants, setDescendants] = useState<Set<number>>(new Set())
  const [departmentId, setDepartmentId] = useState(employee.departmentId ? String(employee.departmentId) : '')
  const [managerId, setManagerId] = useState(employee.managerId ? String(employee.managerId) : '')
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)

  useEffect(() => {
    Promise.all([listDepartments(), listDirectory('', 0, 200), getOrgChart()])
      .then(([d, p, chart]) => {
        setDepartments(d)
        setPeople(p.content.filter((x) => x.id !== employee.id).map((x) => ({ id: x.id, label: `${x.fullName} (${x.jobTitle})` })))
        // Everyone below this employee: making any of them the manager would form a loop.
        const subtree = new Set<number>()
        const node = findNode(chart, employee.id)
        if (node) collectIds(node, subtree)
        setDescendants(subtree)
      })
      .catch((err) => setError(errorMessage(err, 'Could not load options')))
  }, [employee.id])

  const departmentChanged = departmentId !== '' && Number(departmentId) !== employee.departmentId
  const managerChanged = (managerId === '' ? null : Number(managerId)) !== employee.managerId
  const circular = managerId !== '' && descendants.has(Number(managerId))
  const selectedManager = people.find((p) => p.id === Number(managerId))

  const request = useMemo<ReassignInput>(() => {
    const r: ReassignInput = {}
    if (departmentChanged) r.departmentId = Number(departmentId)
    if (managerChanged) {
      if (managerId === '') r.removeManager = true
      else r.managerId = Number(managerId)
    }
    return r
  }, [departmentChanged, managerChanged, departmentId, managerId])

  const nothingToChange = !departmentChanged && !managerChanged

  async function handleSubmit() {
    if (circular) return
    setError(null)
    setBusy(true)
    try {
      onDone(await reassignEmployee(employee.id, request))
    } catch (err) {
      setError(errorMessage(err, 'Could not reassign employee'))
    } finally {
      setBusy(false)
    }
  }

  return (
    <div className="modal-backdrop" role="dialog" aria-modal="true">
      <div className="modal">
        <h2>Reassign {employee.fullName}</h2>
        <div className="form">
          <label>Department
            <select value={departmentId} onChange={(e) => setDepartmentId(e.target.value)}>
              {departments.map((d) => <option key={d.id} value={d.id}>{d.name}</option>)}
            </select>
          </label>
          <label>Manager
            <select value={managerId} onChange={(e) => setManagerId(e.target.value)}>
              <option value="">No manager</option>
              {people.map((p) => (
                <option key={p.id} value={p.id}>
                  {p.label}{descendants.has(p.id) ? ' — reports to ' + employee.fullName : ''}
                </option>
              ))}
            </select>
          </label>
          {circular && (
            <p className="error">
              Circular hierarchy: {selectedManager?.label ?? 'this person'} already reports (directly or indirectly) to{' '}
              {employee.fullName}, so they cannot become their manager. Choose someone else.
            </p>
          )}
          {error && <p className="error">{error}</p>}
          <div className="actions">
            <button onClick={handleSubmit} disabled={busy || circular || nothingToChange}>
              {busy ? 'Saving…' : 'Reassign'}
            </button>
            <button onClick={onClose} disabled={busy}>Cancel</button>
          </div>
        </div>
      </div>
    </div>
  )
}
