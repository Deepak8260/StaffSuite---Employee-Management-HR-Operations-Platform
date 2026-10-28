import Flash from '../../components/Flash'
import { FileText, Upload } from 'lucide-react'
import { useCallback, useEffect, useState } from 'react'
import { useSearchParams } from 'react-router-dom'
import { errorMessage } from '../../api/axiosInstance'
import { deleteDocument, downloadDocument, listCategories, listDocuments } from '../../api/documents'
import PageHeader from '../../components/ui/PageHeader'
import SectionCard from '../../components/ui/SectionCard'
import { useReferenceData } from '../../hooks/useReferenceData'
import type { DocumentCategory, EmployeeDocument } from '../../types/documents'
import DocumentList from './DocumentList'
import StoredPdfViewer from './StoredPdfViewer'
import UploadForm from './UploadForm'

/** HR/Admin: pick an employee, then view, upload (on their behalf) and delete their documents. */
export default function EmployeeDocumentsPage() {
  const [params, setParams] = useSearchParams()
  const selectedId = params.get('employee') ? Number(params.get('employee')) : null
  const { people } = useReferenceData()
  const [categories, setCategories] = useState<DocumentCategory[]>([])
  const [documents, setDocuments] = useState<EmployeeDocument[] | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [notice, setNotice] = useState<string | null>(null)
  const [viewing, setViewing] = useState<EmployeeDocument | null>(null)

  const load = useCallback(async () => {
    if (selectedId === null) return
    try {
      setDocuments(await listDocuments(selectedId))
      setError(null)
    } catch (err) {
      setDocuments(null)
      setError(errorMessage(err, 'Could not load documents'))
    }
  }, [selectedId])

  useEffect(() => {
    // eslint-disable-next-line react-hooks/set-state-in-effect
    void load()
  }, [load])
  useEffect(() => {
    listCategories().then(setCategories).catch(() => setCategories([]))
  }, [])

  async function handleDelete(doc: EmployeeDocument) {
    if (!window.confirm(`Delete "${doc.fileName}"? The file will be removed permanently.`)) return
    setError(null)
    setNotice(null)
    try {
      await deleteDocument(doc.id)
      setNotice(`"${doc.fileName}" was deleted.`)
      await load()
    } catch (err) {
      setError(errorMessage(err, 'Could not delete the document'))
    }
  }

  async function handleDownload(doc: EmployeeDocument) {
    try {
      await downloadDocument(doc)
    } catch (err) {
      setError(errorMessage(err, 'Could not download the document'))
    }
  }

  const selectedName = people.find((p) => p.id === selectedId)?.name ?? documents?.[0]?.employeeName

  return (
    <main className="page">
      <PageHeader title="Employee documents" description="View, upload and delete documents for one employee at a time." />
      <div className="inline-form filters lookup">
        <select value={selectedId ?? ''} onChange={(e) => { setDocuments(null); setNotice(null); setParams(e.target.value ? { employee: e.target.value } : {}) }}>
          <option value="">Select an employee…</option>
          {people.map((p) => <option key={p.id} value={p.id}>{p.name}</option>)}
        </select>
      </div>
      {error && <p className="error">{error}</p>}
      <Flash message={notice} onDone={() => setNotice(null)} />

      {selectedId !== null && (
        <>
          <SectionCard title={`Upload for ${selectedName ?? 'this employee'}`} icon={Upload} tint="purple">
            <UploadForm employeeId={selectedId} categories={categories} allowVisibility onUploaded={load} />
          </SectionCard>
          <SectionCard title="Documents" icon={FileText} tint="purple">
            {documents === null && <p className="hint">Loading…</p>}
            {documents && (
              <DocumentList documents={documents} onDownload={handleDownload} onView={setViewing} onDelete={handleDelete}
                emptyText="This employee has no documents yet." />
            )}
          </SectionCard>
          {viewing && <StoredPdfViewer document={viewing} onClose={() => setViewing(null)} />}
        </>
      )}
      {selectedId === null && <p className="hint">Choose an employee to see and manage their documents.</p>}
    </main>
  )
}
