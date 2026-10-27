import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { errorMessage } from '../../api/axiosInstance'
import { downloadDocument, listExpiringDocuments } from '../../api/documents'
import ExpiryBadge from '../../components/ExpiryBadge'
import type { EmployeeDocument } from '../../types/documents'

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

  return (
    <main className="page">
      <h1>Expiring documents</h1>
      <p className="hint">Documents of current employees that expire soon or have already expired, soonest first.</p>
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
        <div className="holiday-summary">
          <div className="stat"><span className="stat-number">{documents.length}</span><span className="stat-label">Documents listed</span></div>
          <div className="stat"><span className="stat-number">{expiredCount}</span><span className="stat-label">Already expired</span></div>
        </div>
      )}
      <table>
        <thead>
          <tr><th>Employee</th><th>Category</th><th>File</th><th>Expiry</th><th></th></tr>
        </thead>
        <tbody>
          {documents?.map((d) => (
            <tr key={d.id}>
              <td>
                {d.employeeName}
                <div className="hint">{d.employeeCode}{d.departmentName ? ` · ${d.departmentName}` : ''}</div>
              </td>
              <td>{d.categoryName}</td>
              <td>{d.fileName}{d.visibility === 'HR_ONLY' && <span className="type-pill hr-only">HR only</span>}</td>
              <td><ExpiryBadge expiryDate={d.expiryDate} daysRemaining={d.daysRemaining} /></td>
              <td className="actions">
                <Link to={`/admin/documents?employee=${d.employeeId}`} className="button">Manage</Link>
                <button type="button" onClick={() => handleDownload(d)}>Download</button>
              </td>
            </tr>
          ))}
          {documents && documents.length === 0 && <tr><td colSpan={5}>Nothing is expiring in this window.</td></tr>}
        </tbody>
      </table>
    </main>
  )
}
