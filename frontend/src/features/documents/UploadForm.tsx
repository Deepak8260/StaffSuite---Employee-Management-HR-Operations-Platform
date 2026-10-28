import Flash from '../../components/Flash'
import { useEffect, useState, type FormEvent } from 'react'
import { errorMessage } from '../../api/axiosInstance'
import { uploadDocument } from '../../api/documents'
import PdfViewerModal from '../../components/PdfViewerModal'
import type { DocumentCategory, DocumentVisibility } from '../../types/documents'
import { formatBytes } from '../../utils/format'

const MAX_BYTES = 5 * 1024 * 1024

/** Checks the file's own first bytes, so a renamed image or text file is caught before it is sent. */
async function looksLikePdf(file: File): Promise<boolean> {
  const head = await file.slice(0, 5).text()
  return head.startsWith('%PDF')
}

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
  // Preview: an in-memory URL for the chosen file. Nothing is sent to the server by previewing.
  const [previewUrl, setPreviewUrl] = useState<string | null>(null)

  // never leave a preview URL behind
  useEffect(() => () => { if (previewUrl) URL.revokeObjectURL(previewUrl) }, [previewUrl])

  function rejectFile(message: string) {
    setError(message)
    setFile(null)
    setFileKey((k) => k + 1)
  }

  async function chooseFile(f: File | null) {
    setError(null)
    setNotice(null)
    if (!f) {
      setFile(null)
      return
    }
    if (!f.name.toLowerCase().endsWith('.pdf')) {
      rejectFile('Only PDF files are allowed.')
      return
    }
    if (f.size > MAX_BYTES) {
      rejectFile('The file is too large (maximum 5 MB).')
      return
    }
    if (!(await looksLikePdf(f))) {
      rejectFile('This file is not a valid PDF.')
      return
    }
    setFile(f)
  }

  function openPreview() {
    if (!file) return
    setPreviewUrl(URL.createObjectURL(file))
  }

  function closePreview() {
    setPreviewUrl(null)   // the effect above revokes the URL
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
      <label>File (PDF only, max 5 MB)
        <input key={fileKey} type="file" accept=".pdf,application/pdf"
          onChange={(e) => void chooseFile(e.target.files?.[0] ?? null)} required />
      </label>
      {file && (
        <div className="file-chosen">
          <span>{file.name} <span className="hint">({formatBytes(file.size)})</span></span>
          <button type="button" onClick={openPreview}>View PDF</button>
        </div>
      )}
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
      <Flash message={notice} onDone={() => setNotice(null)} />
      <button type="submit" disabled={busy || !file || !categoryId}>{busy ? 'Uploading…' : 'Upload'}</button>

      {previewUrl && file && (
        <PdfViewerModal
          title={`Preview: ${file.name}`}
          details="Not uploaded yet. Check the document, close this window, then click Upload."
          url={previewUrl}
          onClose={closePreview}
        />
      )}
    </form>
  )
}
