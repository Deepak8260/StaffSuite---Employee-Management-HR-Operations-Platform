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

  return (
    <main className="page">
      <h1>Departments</h1>

      <section>
        <h2>{editing ? `Edit "${editing.name}"` : 'New department'}</h2>
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
      </section>

      {error && <p className="error">{error}</p>}
      {notice && <p className="success">{notice}</p>}

      <table>
        <thead>
          <tr><th>Name</th><th>Description</th><th>Active employees</th><th>Status</th><th></th></tr>
        </thead>
        <tbody>
          {departments.map((d) => (
            <tr key={d.id} className={d.active ? undefined : 'muted'}>
              <td>{d.name}</td>
              <td>{d.description ?? '—'}</td>
              <td>{d.employeeCount}</td>
              <td>{d.active ? 'Active' : 'Deleted'}</td>
              <td className="actions">
                {d.active ? (
                  <>
                    <button onClick={() => startEdit(d)}>Edit</button>
                    <button className="danger" onClick={() => handleDelete(d)}>Delete</button>
                  </>
                ) : (
                  <button onClick={() => handleRestore(d)}>Restore</button>
                )}
              </td>
            </tr>
          ))}
          {departments.length === 0 && <tr><td colSpan={5}>No departments yet. Create the first one above.</td></tr>}
        </tbody>
      </table>
    </main>
  )
}
