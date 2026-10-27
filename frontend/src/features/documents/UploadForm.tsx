import { useState, type FormEvent } from 'react'
import { errorMessage } from '../../api/axiosInstance'
import { uploadDocument } from '../../api/documents'
import type { DocumentCategory, DocumentVisibility } from '../../types/documents'

const MAX_BYTES = 5 * 1024 * 1024

interface Props {
  employeeId: number
  categories: DocumentCategory[]
  /** HR/Admin may mark a document HR-only; employees cannot. */
  allowVisibility?: boolean
  onUploaded: () => void
}

export default function UploadForm({ employeeId, categories, allowVisibility, onUploaded }: Props) {
  const [categoryId, setCategoryId] = useState('')
  const [file, setFile] = useState<File | null>(null)
  const [fileKey, setFileKey] = useState(0)
  const [expiryDate, setExpiryDate] = useState('')
  const [visibility, setVisibility] = useState<DocumentVisibility>('SELF_AND_HR')
  const [error, setError] = useState<string | null>(null)
  const [notice, setNotice] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)

  function chooseFile(f: File | null) {
    setError(null)
    if (f && f.size > MAX_BYTES) {
      setError('The file is too large (maximum 5 MB).')
      setFileKey((k) => k + 1)
      return
    }
    setFile(f)
  }

  async function handleSubmit(e: FormEvent) {
    e.preventDefault()
    if (!file) return
    setError(null)
    setNotice(null)
    setBusy(true)
    try {
      await uploadDocument(employeeId, {
        file,
        categoryId: Number(categoryId),
        expiryDate: expiryDate || undefined,
        visibility: allowVisibility ? visibility : undefined,
      })
      setNotice(`"${file.name}" uploaded.`)
      setFile(null)
      setFileKey((k) => k + 1)
      setExpiryDate('')
      onUploaded()
    } catch (err) {
      setError(errorMessage(err, 'Could not upload the document'))
    } finally {
      setBusy(false)
    }
  }

  return (
    <form onSubmit={handleSubmit} className="form upload-form">
      <label>Category
        <select value={categoryId} onChange={(e) => setCategoryId(e.target.value)} required>
          <option value="">Select…</option>
          {categories.map((c) => <option key={c.id} value={c.id}>{c.name}</option>)}
        </select>
      </label>
      <label>File (PDF, PNG or JPG, max 5 MB)
        <input key={fileKey} type="file" accept=".pdf,.png,.jpg,.jpeg,application/pdf,image/png,image/jpeg"
          onChange={(e) => chooseFile(e.target.files?.[0] ?? null)} required />
      </label>
      <label>Expiry date (optional)
        <input type="date" value={expiryDate} onChange={(e) => setExpiryDate(e.target.value)} />
      </label>
      {allowVisibility && (
        <label>Who can see it
          <select value={visibility} onChange={(e) => setVisibility(e.target.value as DocumentVisibility)}>
            <option value="SELF_AND_HR">Employee, their manager and HR</option>
            <option value="HR_ONLY">HR only</option>
          </select>
        </label>
      )}
      {categories.length === 0 && <p className="error">No document categories exist yet. Ask HR to create some.</p>}
      {error && <p className="error">{error}</p>}
      {notice && <p className="success">{notice}</p>}
      <button type="submit" disabled={busy || !file || !categoryId}>{busy ? 'Uploading…' : 'Upload'}</button>
    </form>
  )
}
