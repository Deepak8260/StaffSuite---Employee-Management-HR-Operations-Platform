import { useCallback, useEffect, useState } from 'react'
import { errorMessage } from '../../api/axiosInstance'
import { downloadDocument, listCategories, listDocuments } from '../../api/documents'
import { useAuth } from '../../hooks/useAuth'
import type { DocumentCategory, EmployeeDocument } from '../../types/documents'
import DocumentList from './DocumentList'
import StoredPdfViewer from './StoredPdfViewer'
import UploadForm from './UploadForm'

export default function MyDocumentsPage() {
  const { user } = useAuth()
  const employeeId = user?.employeeId ?? null
  const [documents, setDocuments] = useState<EmployeeDocument[]>([])
  const [categories, setCategories] = useState<DocumentCategory[]>([])
  const [error, setError] = useState<string | null>(null)
  const [viewing, setViewing] = useState<EmployeeDocument | null>(null)

  const load = useCallback(async () => {
    if (employeeId === null) return
    try {
      setDocuments(await listDocuments(employeeId))
      setError(null)
    } catch (err) {
      setError(errorMessage(err, 'Could not load your documents'))
    }
  }, [employeeId])

  useEffect(() => {
    // eslint-disable-next-line react-hooks/set-state-in-effect
    void load()
  }, [load])
  useEffect(() => {
    listCategories().then(setCategories).catch(() => setCategories([]))
  }, [])

  async function handleDownload(doc: EmployeeDocument) {
    try {
      await downloadDocument(doc)
    } catch (err) {
      setError(errorMessage(err, 'Could not download the document'))
    }
  }

  if (employeeId === null) {
    return (
      <main className="page">
        <h1>My documents</h1>
        <p>Your login is not linked to an employee record, so there are no documents to show.</p>
      </main>
    )
  }

  return (
    <main className="page">
      <h1>My documents</h1>
      <p className="hint">
        Upload documents HR asks for, such as ID proof or certificates. Only you, your manager and HR can see them.
        Some documents HR keeps private are not listed here. Only HR can remove a document.
      </p>
      {error && <p className="error">{error}</p>}

      <section>
        <h2>Upload a document</h2>
        <UploadForm employeeId={employeeId} categories={categories} onUploaded={load} />
      </section>

      <section>
        <h2>Your documents</h2>
        <DocumentList documents={documents} onDownload={handleDownload} onView={setViewing} />
      </section>

      {viewing && <StoredPdfViewer document={viewing} onClose={() => setViewing(null)} />}
    </main>
  )
}
