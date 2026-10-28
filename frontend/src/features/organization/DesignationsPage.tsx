import { BadgeCheck, Plus } from 'lucide-react'
import { useCallback, useEffect, useState, type FormEvent } from 'react'
import { errorMessage } from '../../api/axiosInstance'
import { createDesignation, listDepartments, listDesignations, updateDesignation } from '../../api/organization'
import DataTable, { type Column } from '../../components/ui/DataTable'
import PageHeader from '../../components/ui/PageHeader'
import SectionCard from '../../components/ui/SectionCard'
import StatusPill from '../../components/ui/StatusPill'
import type { Department, Designation } from '../../types/organization'

export default function DesignationsPage() {
  const [designations, setDesignations] = useState<Designation[]>([])
  const [departments, setDepartments] = useState<Department[]>([])
  const [editing, setEditing] = useState<Designation | null>(null)
  const [title, setTitle] = useState('')
  const [description, setDescription] = useState('')
  const [departmentId, setDepartmentId] = useState('')
  const [error, setError] = useState<string | null>(null)

  const load = useCallback(async () => {
    try {
      const [g, d] = await Promise.all([listDesignations(true), listDepartments()])
      setDesignations(g)
      setDepartments(d)
    } catch (err) {
      setError(errorMessage(err, 'Could not load designations'))
    }
  }, [])

  useEffect(() => {
    // eslint-disable-next-line react-hooks/set-state-in-effect
    void load()
  }, [load])

  function startEdit(d: Designation | null) {
    setEditing(d)
    setTitle(d?.title ?? '')
    setDescription(d?.description ?? '')
    setDepartmentId(d?.departmentId ? String(d.departmentId) : '')
    setError(null)
  }

  async function handleSubmit(e: FormEvent) {
    e.preventDefault()
    setError(null)
    const input = { title, description, departmentId: departmentId ? Number(departmentId) : null }
    try {
      if (editing) {
        await updateDesignation(editing.id, input)
      } else {
        await createDesignation(input)
      }
      startEdit(null)
      await load()
    } catch (err) {
      setError(errorMessage(err, 'Could not save designation'))
    }
  }

  async function setActive(d: Designation, active: boolean) {
    setError(null)
    try {
      await updateDesignation(d.id, {
        title: d.title,
        description: d.description ?? '',
        departmentId: d.departmentId,
        active,
      })
      await load()
    } catch (err) {
      setError(errorMessage(err, 'Could not update designation'))
    }
  }

  const columns: Column<Designation>[] = [
    { key: 'title', header: 'Title', render: (d) => <span className="person-name">{d.title}</span> },
    { key: 'dept', header: 'Department', render: (d) => d.departmentName ?? '—' },
    { key: 'desc', header: 'Description', render: (d) => d.description ?? '—' },
    { key: 'status', header: 'Status', render: (d) => (
      <StatusPill tone={d.active ? 'success' : 'neutral'}>{d.active ? 'Active' : 'Inactive'}</StatusPill>
    ) },
    { key: 'actions', header: '', align: 'right', render: (d) => (
      <div className="row-actions">
        {d.active ? (
          <>
            <button onClick={() => startEdit(d)}>Edit</button>
            <button onClick={() => setActive(d, false)}>Deactivate</button>
          </>
        ) : (
          <button onClick={() => setActive(d, true)}>Restore</button>
        )}
      </div>
    ) },
  ]

  return (
    <main className="page">
      <PageHeader title="Designations" description="Job designations and the departments they belong to." />

      <SectionCard title={editing ? `Edit "${editing.title}"` : 'New designation'} icon={Plus} tint="blue">
        <form onSubmit={handleSubmit} className="inline-form">
          <input placeholder="Title" value={title} onChange={(e) => setTitle(e.target.value)} maxLength={100} required />
          <select value={departmentId} onChange={(e) => setDepartmentId(e.target.value)}>
            <option value="">No department</option>
            {departments.map((d) => <option key={d.id} value={d.id}>{d.name}</option>)}
          </select>
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

      <SectionCard title="All designations" icon={BadgeCheck} tint="blue">
        <DataTable columns={columns} rows={designations} rowKey={(d) => d.id}
          rowClassName={(d) => (d.active ? undefined : 'muted')} emptyText="No designations yet. Create the first one above." />
      </SectionCard>
    </main>
  )
}
