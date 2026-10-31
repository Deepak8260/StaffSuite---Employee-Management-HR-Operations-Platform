import { FileWarning, Files } from 'lucide-react'
import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { errorMessage } from '../../api/axiosInstance'
import { downloadDocument, listExpiringDocuments } from '../../api/documents'
import AvatarInitials from '../../components/ui/AvatarInitials'
import DataTable, { type Column } from '../../components/ui/DataTable'
import PageHeader from '../../components/ui/PageHeader'
import SectionCard from '../../components/ui/SectionCard'
import StatCard from '../../components/ui/StatCard'
import StatusPill from '../../components/ui/StatusPill'
import type { EmployeeDocument } from '../../types/documents'
import ExpiryPill from './ExpiryPill'

const WINDOWS = [30, 60, 90, 180, 365]

export default function ExpiringDocumentsPage() {
  const [days, setDays] = useState(30)
  const [includeExpired, setIncludeExpired] = useState(true)
  const [documents, setDocuments] = useState<EmployeeDocument[] | null>(null)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    let cancelled = false
    listExpiringDocuments(days, includeExpired)
      .then((d) => {
        if (!cancelled) {
          setDocuments(d)
          setError(null)
        }
      })
      .catch((err) => {
        if (!cancelled) setError(errorMessage(err, 'Could not load expiring documents'))
      })
    return () => {
      cancelled = true
    }
  }, [days, includeExpired])

  async function handleDownload(doc: EmployeeDocument) {
    try {
      await downloadDocument(doc)
    } catch (err) {
      setError(errorMessage(err, 'Could not download the document'))
    }
  }

  const expiredCount = documents?.filter((d) => d.expired).length ?? 0

  const columns: Column<EmployeeDocument>[] = [
    {
      key: 'employee',
      header: 'Employee',
      render: (d) => (
        <div className="person">
          <AvatarInitials name={d.employeeName} size="sm" />
          <div className="person-text">
            <span className="person-name">{d.employeeName}</span>
            <span className="person-meta">{d.employeeCode}{d.departmentName ? ` · ${d.departmentName}` : ''}</span>
          </div>
        </div>
      ),
    },
    { key: 'category', header: 'Category', render: (d) => d.categoryName },
    {
      key: 'file',
      header: 'File',
      render: (d) => <>{d.fileName}{d.visibility === 'HR_ONLY' && <> <StatusPill tone="info">HR only</StatusPill></>}</>,
    },
    { key: 'expiry', header: 'Expiry', render: (d) => <ExpiryPill expiryDate={d.expiryDate} daysRemaining={d.daysRemaining} /> },
    {
      key: 'actions',
      header: '',
      align: 'right',
      className: 'row-actions',
      render: (d) => (
        <>
          <Link to={`/admin/documents?employee=${d.employeeId}`} className="button secondary">Manage</Link>
          <button type="button" onClick={() => handleDownload(d)}>Download</button>
        </>
      ),
    },
  ]

  return (
    <main className="page">
      <PageHeader title="Expiring documents" description="Documents of current employees that expire soon or have already expired, soonest first." />
      <div className="inline-form filters">
        <label className="check">Expiring within
          <select value={days} onChange={(e) => setDays(Number(e.target.value))}>
            {WINDOWS.map((w) => <option key={w} value={w}>{w} days</option>)}
          </select>
        </label>
        <label className="check">
          <input type="checkbox" checked={includeExpired} onChange={(e) => setIncludeExpired(e.target.checked)} />
          Include already expired
        </label>
      </div>
      {error && <p className="error">{error}</p>}
      {documents && (
        <div className="stat-grid">
          <StatCard icon={Files} tint="purple" label="Documents listed" value={documents.length} />
          <StatCard icon={FileWarning} tint="red" label="Already expired" value={expiredCount} />
        </div>
      )}
      <SectionCard title="Documents" icon={Files} tint="purple">
        <DataTable columns={columns} rows={documents} rowKey={(d) => d.id} emptyText="Nothing is expiring in this window." />
      </SectionCard>
    </main>
  )
}
