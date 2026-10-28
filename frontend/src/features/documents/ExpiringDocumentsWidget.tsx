import { useEffect, useState } from 'react'
import { FileWarning } from 'lucide-react'
import { listExpiringDocuments } from '../../api/documents'
import SectionCard from '../../components/ui/SectionCard'
import StatusPill from '../../components/ui/StatusPill'
import type { EmployeeDocument } from '../../types/documents'
import ExpiryPill from './ExpiryPill'

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
    <SectionCard title="Documents needing attention" icon={FileWarning} tint="purple" viewAllTo="/admin/documents/expiring">
      {documents.length === 0 ? (
        <p className="hint">No documents are expired or expiring soon.</p>
      ) : (
        <>
          <p>
            <strong>{documents.length}</strong> document{documents.length === 1 ? '' : 's'}
            {expired > 0 && <> · <StatusPill tone="danger">{expired} expired</StatusPill></>}
          </p>
          <ul className="widget-list">
            {documents.slice(0, SHOWN).map((d) => (
              <li key={d.id}>
                <span>{d.employeeName} · {d.categoryName}</span>
                <ExpiryPill expiryDate={d.expiryDate} daysRemaining={d.daysRemaining} />
              </li>
            ))}
          </ul>
          {documents.length > SHOWN && <p className="hint">and {documents.length - SHOWN} more</p>}
        </>
      )}
    </SectionCard>
  )
}
