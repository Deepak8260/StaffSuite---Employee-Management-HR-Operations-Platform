import ExpiryBadge from '../../components/ExpiryBadge'
import type { EmployeeDocument } from '../../types/documents'
import { formatDateOnly } from '../../utils/dates'
import { formatBytes } from '../../utils/format'

interface Props {
  documents: EmployeeDocument[]
  onDownload: (doc: EmployeeDocument) => void
  /** Only HR/Admin get this; the backend refuses everyone else. */
  onDelete?: (doc: EmployeeDocument) => void
  emptyText?: string
}

/** Documents grouped by category. */
export default function DocumentList({ documents, onDownload, onDelete, emptyText = 'No documents yet.' }: Props) {
  if (documents.length === 0) return <p>{emptyText}</p>

  const groups = new Map<string, EmployeeDocument[]>()
  documents.forEach((d) => groups.set(d.categoryName, [...(groups.get(d.categoryName) ?? []), d]))

  return (
    <>
      {[...groups.entries()].sort((a, b) => a[0].localeCompare(b[0])).map(([category, docs]) => (
        <section key={category} className="doc-group">
          <h3>{category} <span className="month-count">{docs.length}</span></h3>
          <table>
            <thead>
              <tr><th>File</th><th>Uploaded</th><th>Expiry</th><th>Size</th><th></th></tr>
            </thead>
            <tbody>
              {docs.map((d) => (
                <tr key={d.id}>
                  <td>
                    <strong>{d.fileName}</strong>
                    {d.visibility === 'HR_ONLY' && <span className="type-pill hr-only">HR only</span>}
                  </td>
                  <td>
                    {formatDateOnly(d.createdAt)}
                    {d.uploadedByName && <div className="hint">by {d.uploadedByName}</div>}
                  </td>
                  <td><ExpiryBadge expiryDate={d.expiryDate} daysRemaining={d.daysRemaining} /></td>
                  <td>{formatBytes(d.fileSize)}</td>
                  <td className="actions">
                    <button type="button" onClick={() => onDownload(d)}>Download</button>
                    {onDelete && <button type="button" className="danger" onClick={() => onDelete(d)}>Delete</button>}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </section>
      ))}
    </>
  )
}
