import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { listExpiringDocuments } from '../../api/documents'
import ExpiryBadge from '../../components/ExpiryBadge'
import type { EmployeeDocument } from '../../types/documents'

const SHOWN = 5

/** Home-page card for HR/Admin: documents that have expired or expire soon. */
export default function ExpiringDocumentsWidget() {
  const [documents, setDocuments] = useState<EmployeeDocument[] | null>(null)

  useEffect(() => {
    listExpiringDocuments(undefined, true).then(setDocuments).catch(() => setDocuments(null))
  }, [])

  if (documents === null) return null
  const expired = documents.filter((d) => d.expired).length

  return (
    <section className="widget">
      <div className="page-head">
        <h2>Documents needing attention</h2>
        <Link to="/admin/documents/expiring">View all</Link>
      </div>
      {documents.length === 0 ? (
        <p className="hint">No documents are expired or expiring soon.</p>
      ) : (
        <>
          <p>
            <strong>{documents.length}</strong> document{documents.length === 1 ? '' : 's'}
            {expired > 0 && <> · <span className="expiry expired">{expired} expired</span></>}
          </p>
          <ul className="widget-list">
            {documents.slice(0, SHOWN).map((d) => (
              <li key={d.id}>
                <span>{d.employeeName} · {d.categoryName}</span>
                <ExpiryBadge expiryDate={d.expiryDate} daysRemaining={d.daysRemaining} />
              </li>
            ))}
          </ul>
          {documents.length > SHOWN && <p className="hint">and {documents.length - SHOWN} more</p>}
        </>
      )}
    </section>
  )
}
