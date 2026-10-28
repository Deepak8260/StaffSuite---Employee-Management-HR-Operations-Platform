import DataTable, { type Column } from '../../components/ui/DataTable'
import StatusPill from '../../components/ui/StatusPill'
import type { EmployeeDocument } from '../../types/documents'
import { formatDateTime } from '../../utils/dates'
import { formatBytes } from '../../utils/format'
import ExpiryPill from './ExpiryPill'

interface Props {
  documents: EmployeeDocument[]
  onDownload: (doc: EmployeeDocument) => void
  /** Opens the PDF in the in-app viewer. */
  onView?: (doc: EmployeeDocument) => void
  /** Only HR/Admin get this; the backend refuses everyone else. */
  onDelete?: (doc: EmployeeDocument) => void
  emptyText?: string
}

/** Documents grouped by category, one table per category. */
export default function DocumentList({ documents, onDownload, onView, onDelete, emptyText = 'No documents yet.' }: Props) {
  if (documents.length === 0) return <p className="hint">{emptyText}</p>

  const groups = new Map<string, EmployeeDocument[]>()
  documents.forEach((d) => groups.set(d.categoryName, [...(groups.get(d.categoryName) ?? []), d]))

  const columns: Column<EmployeeDocument>[] = [
    {
      key: 'file',
      header: 'File',
      render: (d) => (
        <>
          <strong>{d.fileName}</strong>
          {d.visibility === 'HR_ONLY' && <> <StatusPill tone="info">HR only</StatusPill></>}
        </>
      ),
    },
    {
      key: 'uploaded',
      header: 'Uploaded',
      render: (d) => (
        <>
          {formatDateTime(d.uploadedAt)}
          {d.uploadedByName && <div className="person-meta">by {d.uploadedByName}</div>}
        </>
      ),
    },
    { key: 'expiry', header: 'Expiry', render: (d) => <ExpiryPill expiryDate={d.expiryDate} daysRemaining={d.daysRemaining} /> },
    { key: 'size', header: 'Size', render: (d) => formatBytes(d.fileSize) },
    {
      key: 'actions',
      header: '',
      align: 'right',
      className: 'row-actions',
      render: (d) => (
        <>
          {/* only PDFs can be shown in the viewer; any older non-PDF file stays download-only */}
          {onView && d.contentType === 'application/pdf' && (
            <button type="button" onClick={() => onView(d)}>View</button>
          )}
          <button type="button" onClick={() => onDownload(d)}>Download</button>
          {onDelete && <button type="button" className="danger" onClick={() => onDelete(d)}>Delete</button>}
        </>
      ),
    },
  ]

  return (
    <div className="doc-groups">
      {[...groups.entries()].sort((a, b) => a[0].localeCompare(b[0])).map(([category, docs]) => (
        <section key={category} className="doc-group">
          <h3 className="doc-group-title">{category} <span className="doc-group-count">{docs.length}</span></h3>
          <DataTable columns={columns} rows={docs} rowKey={(d) => d.id} />
        </section>
      ))}
    </div>
  )
}
