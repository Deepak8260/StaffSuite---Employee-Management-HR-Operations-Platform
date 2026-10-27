import { useCallback, useEffect, useState, type FormEvent } from 'react'
import { errorMessage } from '../../api/axiosInstance'
import { createCategory, listCategories, updateCategory } from '../../api/documents'
import type { DocumentCategory } from '../../types/documents'

export default function DocumentCategoriesPage() {
  const [categories, setCategories] = useState<DocumentCategory[]>([])
  const [editing, setEditing] = useState<DocumentCategory | null>(null)
  const [name, setName] = useState('')
  const [description, setDescription] = useState('')
  const [error, setError] = useState<string | null>(null)

  const load = useCallback(async () => {
    try {
      setCategories(await listCategories(true))
    } catch (err) {
      setError(errorMessage(err, 'Could not load categories'))
    }
  }, [])

  useEffect(() => {
    // eslint-disable-next-line react-hooks/set-state-in-effect
    void load()
  }, [load])

  function startEdit(c: DocumentCategory | null) {
    setEditing(c)
    setName(c?.name ?? '')
    setDescription(c?.description ?? '')
    setError(null)
  }

  async function handleSubmit(e: FormEvent) {
    e.preventDefault()
    setError(null)
    try {
      if (editing) await updateCategory(editing.id, { name, description, active: editing.active })
      else await createCategory({ name, description })
      startEdit(null)
      await load()
    } catch (err) {
      setError(errorMessage(err, 'Could not save the category'))
    }
  }

  async function setActive(c: DocumentCategory, active: boolean) {
    setError(null)
    try {
      await updateCategory(c.id, { name: c.name, description: c.description ?? '', active })
      await load()
    } catch (err) {
      setError(errorMessage(err, 'Could not update the category'))
    }
  }

  return (
    <main className="page">
      <h1>Document categories</h1>
      <section>
        <h2>{editing ? `Edit "${editing.name}"` : 'New category'}</h2>
        <form onSubmit={handleSubmit} className="inline-form">
          <input placeholder="Name (e.g. ID Proof)" value={name} onChange={(e) => setName(e.target.value)} maxLength={100} required />
          <input placeholder="Description (optional)" value={description} onChange={(e) => setDescription(e.target.value)} maxLength={500} size={40} />
          <button type="submit">{editing ? 'Save' : 'Create'}</button>
          {editing && <button type="button" onClick={() => startEdit(null)}>Cancel</button>}
        </form>
        <p className="hint">Deactivating a category hides it from the upload form; documents already filed under it are kept.</p>
      </section>

      {error && <p className="error">{error}</p>}
      <table>
        <thead>
          <tr><th>Name</th><th>Description</th><th>Status</th><th></th></tr>
        </thead>
        <tbody>
          {categories.map((c) => (
            <tr key={c.id} className={c.active ? undefined : 'muted'}>
              <td>{c.name}</td>
              <td>{c.description ?? '—'}</td>
              <td>{c.active ? 'Active' : 'Inactive'}</td>
              <td className="actions">
                {c.active ? (
                  <>
                    <button onClick={() => startEdit(c)}>Edit</button>
                    <button onClick={() => setActive(c, false)}>Deactivate</button>
                  </>
                ) : (
                  <button onClick={() => setActive(c, true)}>Restore</button>
                )}
              </td>
            </tr>
          ))}
          {categories.length === 0 && <tr><td colSpan={4}>No categories yet. Create the first one above.</td></tr>}
        </tbody>
      </table>
    </main>
  )
}
