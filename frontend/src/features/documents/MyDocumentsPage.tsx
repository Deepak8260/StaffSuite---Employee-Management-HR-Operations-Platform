import { FileText, Upload } from 'lucide-react'
import { useCallback, useEffect, useState } from 'react'
import { errorMessage } from '../../api/axiosInstance'
import { downloadDocument, listCategories, listDocuments } from '../../api/documents'
import PageHeader from '../../components/ui/PageHeader'
import SectionCard from '../../components/ui/SectionCard'
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
        <PageHeader title="My documents" />
        <p>Your login is not linked to an employee record, so there are no documents to show.</p>
      </main>
    )
  }

  return (
    <main className="page">
      <PageHeader
        title="My documents"
        description="Upload documents HR asks for. Only you, your manager and HR can see them; only HR can remove one."
      />
      {error && <p className="error">{error}</p>}

      <SectionCard title="Upload a document" icon={Upload} tint="purple">
        <UploadForm employeeId={employeeId} categories={categories} onUploaded={load} />
      </SectionCard>

      <SectionCard title="Your documents" icon={FileText} tint="purple">
        <DocumentList documents={documents} onDownload={handleDownload} onView={setViewing} />
      </SectionCard>

      {viewing && <StoredPdfViewer document={viewing} onClose={() => setViewing(null)} />}
    </main>
  )
}
