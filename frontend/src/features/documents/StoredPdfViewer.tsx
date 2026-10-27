import { useEffect, useState } from 'react'
import { errorMessage } from '../../api/axiosInstance'
import { fetchPdfObjectUrl } from '../../api/documents'
import PdfViewerModal from '../../components/PdfViewerModal'
import type { EmployeeDocument } from '../../types/documents'
import { formatDateOnly, formatDateTime } from '../../utils/dates'

interface Props {
  document: EmployeeDocument
  onClose: () => void
}

/** Opens a stored PDF in the app. The file is fetched with the user's token and shown from memory. */
export default function StoredPdfViewer({ document: doc, onClose }: Props) {
  const [url, setUrl] = useState<string | null>(null)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    let cancelled = false
    let created: string | null = null
    fetchPdfObjectUrl(doc.id)
      .then((u) => {
        if (cancelled) {
          URL.revokeObjectURL(u)
          return
        }
        created = u
        setUrl(u)
      })
      .catch(async (err) => {
        if (!cancelled) setError(errorMessage(err, 'Could not open the document'))
      })
    return () => {
      cancelled = true
      if (created) URL.revokeObjectURL(created)
    }
  }, [doc.id])

  return (
    <PdfViewerModal
      title={doc.fileName}
      url={url}
      loading={!url && !error}
      error={error}
      onClose={onClose}
      details={
        <>
          {doc.categoryName} · Uploaded {formatDateTime(doc.uploadedAt)}
          {doc.uploadedByName ? ` by ${doc.uploadedByName}` : ''}
          {doc.expiryDate ? ` · Expires ${formatDateOnly(doc.expiryDate)}` : ''}
        </>
      }
    />
  )
}
