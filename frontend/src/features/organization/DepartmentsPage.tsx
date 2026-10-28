import { Building2, Plus } from 'lucide-react'
import Flash from '../../components/Flash'
import DataTable, { type Column } from '../../components/ui/DataTable'
import PageHeader from '../../components/ui/PageHeader'
import SectionCard from '../../components/ui/SectionCard'
import StatusPill from '../../components/ui/StatusPill'
import { useCallback, useEffect, useState, type FormEvent } from 'react'
import { errorMessage } from '../../api/axiosInstance'
import { createDepartment, deleteDepartment, listDepartments, updateDepartment } from '../../api/organization'
import type { Department } from '../../types/organization'

export default function DepartmentsPage() {
  const [departments, setDepartments] = useState<Department[]>([])
  const [editing, setEditing] = useState<Department | null>(null)
  const [name, setName] = useState('')
  const [description, setDescription] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [notice, setNotice] = useState<string | null>(null)

  const load = useCallback(async () => {
    try {
      setDepartments(await listDepartments(true))
    } catch (err) {
      setError(errorMessage(err, 'Could not load departments'))
    }
  }, [])

  useEffect(() => {
    // eslint-disable-next-line react-hooks/set-state-in-effect
    void load()
  }, [load])

  function startEdit(d: Department | null) {
    setEditing(d)
    setName(d?.name ?? '')
    setDescription(d?.description ?? '')
    setError(null)
    setNotice(null)
  }

  async function handleSubmit(e: FormEvent) {
    e.preventDefault()
    setError(null)
    setNotice(null)
    try {
      if (editing) {
        await updateDepartment(editing.id, { name, description })
      } else {
        await createDepartment({ name, description })
      }
      startEdit(null)
      await load()
    } catch (err) {
      setError(errorMessage(err, 'Could not save department'))
    }
  }

  async function handleDelete(d: Department) {
    if (!window.confirm(`Delete the department "${d.name}"?`)) return
    setError(null)
    setNotice(null)
    try {
      await deleteDepartment(d.id)
      setNotice(`Department "${d.name}" was deleted.`)
      await load()
    } catch (err) {
      // e.g. "Cannot delete department 'X': 3 active employees are still assigned to it..."
      setError(errorMessage(err, 'Could not delete department'))
    }
  }

  async function handleRestore(d: Department) {
    setError(null)
    setNotice(null)
    try {
      await updateDepartment(d.id, { name: d.name, description: d.description ?? '', active: true })
      await load()
    } catch (err) {
      setError(errorMessage(err, 'Could not restore department'))
    }
  }

  const columns: Column<Department>[] = [
    { key: 'name', header: 'Name', render: (d) => <span className="person-name">{d.name}</span> },
    { key: 'desc', header: 'Description', render: (d) => d.description ?? '—' },
    { key: 'count', header: 'Active employees', render: (d) => d.employeeCount },
    { key: 'status', header: 'Status', render: (d) => (
      <StatusPill tone={d.active ? 'success' : 'neutral'}>{d.active ? 'Active' : 'Deleted'}</StatusPill>
    ) },
    { key: 'actions', header: '', align: 'right', render: (d) => (
      <div className="row-actions">
        {d.active ? (
          <>
            <button onClick={() => startEdit(d)}>Edit</button>
            <button className="danger" onClick={() => handleDelete(d)}>Delete</button>
          </>
        ) : (
          <button onClick={() => handleRestore(d)}>Restore</button>
        )}
      </div>
    ) },
  ]

  return (
    <main className="page">
      <PageHeader title="Departments" description="Create and maintain the company's departments." />

      <SectionCard title={editing ? `Edit "${editing.name}"` : 'New department'} icon={Plus} tint="blue">
        <form onSubmit={handleSubmit} className="inline-form">
          <input placeholder="Name" value={name} onChange={(e) => setName(e.target.value)} maxLength={100} required />
          <input
            placeholder="Description (optional)"
            value={description}
            onChange={(e) => setDescription(e.target.value)}
            maxLength={500}
            size={40}
          />
          <button type="submit">{editing ? 'Save' : 'Create'}</button>
          {editing && <button type="button" onClick={() => startEdit(null)}>Cancel</button>}
        </form>
      </SectionCard>

      {error && <p className="error">{error}</p>}
      <Flash message={notice} onDone={() => setNotice(null)} />

      <SectionCard title="All departments" icon={Building2} tint="blue">
        <DataTable columns={columns} rows={departments} rowKey={(d) => d.id}
          rowClassName={(d) => (d.active ? undefined : 'muted')} emptyText="No departments yet. Create the first one above." />
      </SectionCard>
    </main>
  )
}
