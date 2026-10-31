import { Download, X } from 'lucide-react'
import { useRef, useState } from 'react'
import { bulkUploadEmployees, downloadBulkTemplate } from '../../api/employees'
import { errorMessage } from '../../api/axiosInstance'
import DataTable, { type Column } from '../../components/ui/DataTable'
import InfoTip from '../../components/ui/InfoTip'
import type { BulkUploadResult, BulkUploadRowError } from '../../types/employee'
import BulkUploadGuideCard from './BulkUploadGuideCard'

interface Props {
  onClose: () => void
  /** Called after an upload that created at least one employee, so the list can refresh. */
  onCreated: () => void
}

const errorColumns: Column<BulkUploadRowError>[] = [
  { key: 'row', header: 'Row', render: (e) => e.row },
  { key: 'name', header: 'Name', render: (e) => e.fullName || '—' },
  { key: 'email', header: 'Email', render: (e) => e.email || '—' },
  { key: 'reason', header: 'Reason', render: (e) => e.reason },
]

function csvCell(value: string | number): string {
  return `"${String(value).replace(/"/g, '""')}"`
}

function downloadErrors(errors: BulkUploadRowError[]) {
  const lines = [['row', 'full_name', 'email', 'reason'], ...errors.map((e) => [e.row, e.fullName, e.email, e.reason])]
  const blob = new Blob([lines.map((l) => l.map(csvCell).join(',')).join('\r\n')], { type: 'text/csv' })
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = 'bulk_upload_errors.csv'
  document.body.appendChild(a)
  a.click()
  a.remove()
  URL.revokeObjectURL(url)
}

export default function BulkUploadModal({ onClose, onCreated }: Props) {
  const [file, setFile] = useState<File | null>(null)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [result, setResult] = useState<BulkUploadResult | null>(null)
  const input = useRef<HTMLInputElement>(null)

  async function upload() {
    if (!file) return
    setBusy(true)
    setError(null)
    setResult(null)
    try {
      const r = await bulkUploadEmployees(file)
      setResult(r)
      if (r.created > 0) onCreated()
    } catch (err) {
      setError(errorMessage(err, 'Upload failed'))
    } finally {
      setBusy(false)
    }
  }

  async function template() {
    try {
      await downloadBulkTemplate()
    } catch (err) {
      setError(errorMessage(err, 'Could not download the template'))
    }
  }

  function reset() {
    setFile(null)
    setResult(null)
    setError(null)
    if (input.current) input.current.value = ''
  }

  return (
    <div className="modal-backdrop" role="dialog" aria-modal="true" aria-label="Bulk upload employees">
      <div className="modal bulk-modal">
        <button type="button" className="modal-x" aria-label="Close" onClick={onClose} disabled={busy}>
          <X size={20} aria-hidden="true" />
        </button>
        <h2>Bulk upload employees</h2>
        <p className="hint">Add many employees at once from an Excel file.</p>

        <div className="bulk-row">
          <input ref={input} type="file" accept=".xlsx,.xls" aria-label="Excel file"
            onChange={(e) => { setFile(e.target.files?.[0] ?? null); setResult(null); setError(null) }} />
          <InfoTip label="Required Excel format"><BulkUploadGuideCard /></InfoTip>
        </div>
        <p>
          <button type="button" className="link-button" onClick={template}>
            <Download size={15} aria-hidden="true" /> Download template
          </button>
        </p>

        {error && <p className="error bulk-error">{error}</p>}

        {result && (
          <div className="bulk-result">
            <p>
              <strong>{result.created}</strong> created, <strong>{result.failed}</strong> failed
              {' '}(of {result.totalRows} row{result.totalRows === 1 ? '' : 's'}).
            </p>
            {result.errors.length > 0 && (
              <>
                <DataTable columns={errorColumns} rows={result.errors} rowKey={(e) => e.row} emptyText="" />
                <p className="hint">Fix these rows in your file and upload them again. Rows that were created are not repeated.</p>
                <button type="button" className="secondary" onClick={() => downloadErrors(result.errors)}>Download error list (CSV)</button>
              </>
            )}
          </div>
        )}

        <div className="actions">
          {result
            ? <button type="button" onClick={reset}>Upload another file</button>
            : <button type="button" className="btn-primary" onClick={upload} disabled={!file || busy}>{busy ? 'Uploading…' : 'Upload'}</button>}
        </div>
      </div>
    </div>
  )
}
