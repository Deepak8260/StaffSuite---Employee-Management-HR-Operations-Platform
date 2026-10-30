import { useCallback, useEffect, useRef, useState, type PointerEvent as ReactPointerEvent, type ReactNode } from 'react'

interface Props {
  title: string
  /** Extra information shown under the title (category, upload time, ...). */
  details?: ReactNode
  /** In-memory URL of the PDF, or null while it is still loading. */
  url: string | null
  /** Name used when the file is saved to disk (the browser would otherwise use a random blob id). */
  fileName?: string
  loading?: boolean
  error?: string | null
  onClose: () => void
}

interface Rect { x: number; y: number; w: number; h: number }

type Edge = 'n' | 's' | 'e' | 'w' | 'ne' | 'nw' | 'se' | 'sw'
const EDGES: Edge[] = ['n', 's', 'e', 'w', 'ne', 'nw', 'se', 'sw']
const MIN_W = 380
const MIN_H = 300

function initialRect(): Rect {
  const w = Math.min(1000, Math.round(window.innerWidth * 0.94))
  const h = Math.min(960, Math.round(window.innerHeight * 0.9))
  return { x: Math.round((window.innerWidth - w) / 2), y: Math.round((window.innerHeight - h) / 2), w, h }
}

/** Full-screen PDF viewer used both for previewing a file before upload and for viewing stored documents. */
export default function PdfViewerModal({ title, details, url, fileName, loading, error, onClose }: Props) {
  const [rect, setRect] = useState<Rect>(initialRect)
  const [dragging, setDragging] = useState(false)
  const frameRef = useRef<HTMLIFrameElement>(null)

  useEffect(() => {
    function onKey(e: KeyboardEvent) {
      if (e.key === 'Escape') onClose()
    }
    document.addEventListener('keydown', onKey)
    return () => document.removeEventListener('keydown', onKey)
  }, [onClose])

  const startResize = useCallback((edge: Edge, e: ReactPointerEvent) => {
    e.preventDefault()
    const startX = e.clientX
    const startY = e.clientY
    const start = rect
    setDragging(true)
    function move(ev: PointerEvent) {
      const dx = ev.clientX - startX
      const dy = ev.clientY - startY
      let { x, y, w, h } = start
      if (edge.includes('e')) w = start.w + dx
      if (edge.includes('s')) h = start.h + dy
      if (edge.includes('w')) { w = start.w - dx; x = start.x + dx }
      if (edge.includes('n')) { h = start.h - dy; y = start.y + dy }
      // keep a usable minimum size, anchoring the edge opposite the one being dragged
      if (w < MIN_W) { if (edge.includes('w')) x = start.x + start.w - MIN_W; w = MIN_W }
      if (h < MIN_H) { if (edge.includes('n')) y = start.y + start.h - MIN_H; h = MIN_H }
      // and never let the window leave the screen
      if (x < 0) { if (edge.includes('w')) w += x; x = 0 }
      if (y < 0) { if (edge.includes('n')) h += y; y = 0 }
      if (x + w > window.innerWidth) w = window.innerWidth - x
      if (y + h > window.innerHeight) h = window.innerHeight - y
      setRect({ x, y, w, h })
    }
    function up() {
      setDragging(false)
      window.removeEventListener('pointermove', move)
      window.removeEventListener('pointerup', up)
    }
    window.addEventListener('pointermove', move)
    window.addEventListener('pointerup', up)
  }, [rect])

  // the browser's own PDF toolbar saves blob files under a random id, so download and print are offered here instead
  function download() {
    if (!url) return
    const a = document.createElement('a')
    a.href = url
    a.download = fileName ?? title
    document.body.appendChild(a)
    a.click()
    a.remove()
  }

  function print() {
    frameRef.current?.contentWindow?.focus()
    frameRef.current?.contentWindow?.print()
  }

  return (
    <div className="modal-backdrop" role="dialog" aria-modal="true" aria-label={title} onMouseDown={(e) => e.target === e.currentTarget && onClose()}>
      <div className="modal pdf-modal resizable" style={{ left: rect.x, top: rect.y, width: rect.w, height: rect.h }}>
        <div className="pdf-head">
          <div>
            <h2>{title}</h2>
            {details && <div className="hint">{details}</div>}
          </div>
          <div className="pdf-actions">
            {url && <button type="button" className="secondary" onClick={download}>Download</button>}
            {url && <button type="button" className="secondary" onClick={print}>Print</button>}
            <button type="button" onClick={onClose}>Close</button>
          </div>
        </div>
        <div className="pdf-body">
          {loading && <p className="pdf-message">Loading document…</p>}
          {error && <p className="pdf-message error">{error}</p>}
          {url && <iframe ref={frameRef} className="pdf-frame" src={`${url}#toolbar=0`} title={title} style={dragging ? { pointerEvents: 'none' } : undefined} />}
        </div>
        {EDGES.map((edge) => (
          <div key={edge} className={`resize-handle resize-${edge}`} onPointerDown={(e) => startResize(edge, e)} aria-hidden="true" />
        ))}
      </div>
    </div>
  )
}
