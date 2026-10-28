import { FolderPlus, Tags } from 'lucide-react'
import { useCallback, useEffect, useState, type FormEvent } from 'react'
import { errorMessage } from '../../api/axiosInstance'
import { createCategory, listCategories, updateCategory } from '../../api/documents'
import DataTable, { type Column } from '../../components/ui/DataTable'
import PageHeader from '../../components/ui/PageHeader'
import SectionCard from '../../components/ui/SectionCard'
import { StatusCodePill } from '../../components/ui/StatusPill'
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

  const columns: Column<DocumentCategory>[] = [
    { key: 'name', header: 'Name', render: (c) => <span className="person-name">{c.name}</span> },
    { key: 'description', header: 'Description', render: (c) => c.description ?? '—' },
    { key: 'status', header: 'Status', render: (c) => <StatusCodePill status={c.active ? 'ACTIVE' : 'INACTIVE'} /> },
    {
      key: 'actions',
      header: '',
      align: 'right',
      className: 'row-actions',
      render: (c) =>
        c.active ? (
          <>
            <button onClick={() => startEdit(c)}>Edit</button>
            <button onClick={() => setActive(c, false)}>Deactivate</button>
          </>
        ) : (
          <button onClick={() => setActive(c, true)}>Restore</button>
        ),
    },
  ]

  return (
    <main className="page">
      <PageHeader title="Document categories" description="The categories documents are filed under." />
      <SectionCard title={editing ? `Edit "${editing.name}"` : 'New category'} icon={FolderPlus} tint="purple">
        <form onSubmit={handleSubmit} className="inline-form">
          <input placeholder="Name (e.g. ID Proof)" value={name} onChange={(e) => setName(e.target.value)} maxLength={100} required />
          <input placeholder="Description (optional)" value={description} onChange={(e) => setDescription(e.target.value)} maxLength={500} size={40} />
          <button type="submit">{editing ? 'Save' : 'Create'}</button>
          {editing && <button type="button" onClick={() => startEdit(null)}>Cancel</button>}
        </form>
        <p className="hint">Deactivating a category hides it from the upload form; documents already filed under it are kept.</p>
      </SectionCard>

      {error && <p className="error">{error}</p>}
      <SectionCard title="Categories" icon={Tags} tint="purple">
        <DataTable
          columns={columns}
          rows={categories}
          rowKey={(c) => c.id}
          rowClassName={(c) => (c.active ? undefined : 'muted')}
          emptyText="No categories yet. Create the first one above."
        />
      </SectionCard>
    </main>
  )
}
