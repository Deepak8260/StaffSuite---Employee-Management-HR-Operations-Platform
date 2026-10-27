import { useCallback, useEffect, useState, type FormEvent } from 'react'
import { errorMessage } from '../../api/axiosInstance'
import { createDesignation, listDepartments, listDesignations, updateDesignation } from '../../api/organization'
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

  return (
    <main className="page">
      <h1>Designations</h1>

      <section>
        <h2>{editing ? `Edit "${editing.title}"` : 'New designation'}</h2>
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
      </section>

      {error && <p className="error">{error}</p>}

      <table>
        <thead>
          <tr><th>Title</th><th>Department</th><th>Description</th><th>Status</th><th></th></tr>
        </thead>
        <tbody>
          {designations.map((d) => (
            <tr key={d.id} className={d.active ? undefined : 'muted'}>
              <td>{d.title}</td>
              <td>{d.departmentName ?? '—'}</td>
              <td>{d.description ?? '—'}</td>
              <td>{d.active ? 'Active' : 'Inactive'}</td>
              <td className="actions">
                {d.active ? (
                  <>
                    <button onClick={() => startEdit(d)}>Edit</button>
                    <button onClick={() => setActive(d, false)}>Deactivate</button>
                  </>
                ) : (
                  <button onClick={() => setActive(d, true)}>Restore</button>
                )}
              </td>
            </tr>
          ))}
          {designations.length === 0 && <tr><td colSpan={5}>No designations yet. Create the first one above.</td></tr>}
        </tbody>
      </table>
    </main>
  )
}
