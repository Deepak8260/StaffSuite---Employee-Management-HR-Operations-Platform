import { useEffect, type ReactNode } from 'react'

interface Props {
  title: string
  /** Extra information shown under the title (category, upload time, ...). */
  details?: ReactNode
  /** In-memory URL of the PDF, or null while it is still loading. */
  url: string | null
  loading?: boolean
  error?: string | null
  onClose: () => void
}

/** Full-screen PDF viewer used both for previewing a file before upload and for viewing stored documents. */
export default function PdfViewerModal({ title, details, url, loading, error, onClose }: Props) {
  useEffect(() => {
    function onKey(e: KeyboardEvent) {
      if (e.key === 'Escape') onClose()
    }
    document.addEventListener('keydown', onKey)
    return () => document.removeEventListener('keydown', onKey)
  }, [onClose])

  return (
    <div className="modal-backdrop" role="dialog" aria-modal="true" aria-label={title} onMouseDown={(e) => e.target === e.currentTarget && onClose()}>
      <div className="modal pdf-modal">
        <div className="pdf-head">
          <div>
            <h2>{title}</h2>
            {details && <div className="hint">{details}</div>}
          </div>
          <button type="button" onClick={onClose}>Close</button>
        </div>
        <div className="pdf-body">
          {loading && <p className="pdf-message">Loading document…</p>}
          {error && <p className="pdf-message error">{error}</p>}
          {url && <iframe className="pdf-frame" src={url} title={title} />}
        </div>
      </div>
    </div>
  )
}
