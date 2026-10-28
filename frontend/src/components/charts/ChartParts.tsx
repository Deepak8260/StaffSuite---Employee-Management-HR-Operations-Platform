import { useState, type ReactNode } from 'react'
import { errorMessage } from '../../api/axiosInstance'

export interface LegendItem {
  label: string
  color: string
  /** Shown beside the name, so the legend also carries the totals. */
  value?: number | string
}

/** Always shown for two or more series; swatches are rectangles because the marks are bars. */
export function ChartLegend({ items }: { items: LegendItem[] }) {
  return (
    <ul className="viz-legend">
      {items.map((i) => (
        <li key={i.label}>
          <span className="viz-swatch" style={{ background: i.color }} />
          <span>{i.label}</span>
          {i.value !== undefined && <strong>{i.value}</strong>}
        </li>
      ))}
    </ul>
  )
}

/** The table twin of a chart: every value is readable without the picture or a tooltip. */
export function ChartTable({ caption, headers, rows }: { caption: string; headers: string[]; rows: (string | number)[][] }) {
  return (
    <div className="viz-table-wrap">
      <table className="viz-table">
        <caption>{caption}</caption>
        <thead>
          <tr>{headers.map((h, i) => <th key={h} className={i > 0 ? 'num' : undefined}>{h}</th>)}</tr>
        </thead>
        <tbody>
          {rows.map((r, ri) => (
            <tr key={ri}>{r.map((c, ci) => <td key={ci} className={ci > 0 ? 'num' : undefined}>{c}</td>)}</tr>
          ))}
          {rows.length === 0 && <tr><td colSpan={headers.length}>No data for this period.</td></tr>}
        </tbody>
      </table>
    </div>
  )
}

interface CardProps {
  title: string
  subtitle?: ReactNode
  chart: ReactNode
  table: ReactNode
  /** While true the previous drawing stays put, dimmed, instead of flashing a skeleton. */
  loading?: boolean
  error?: string | null
  onExport?: () => Promise<void>
}

/** A report widget: title, Chart/Table switch, Export CSV, and the content. The filters live above, not in here. */
export function ChartCard({ title, subtitle, chart, table, loading, error, onExport }: CardProps) {
  const [view, setView] = useState<'chart' | 'table'>('chart')
  const [exporting, setExporting] = useState(false)
  const [exportError, setExportError] = useState<string | null>(null)

  async function handleExport() {
    if (!onExport) return
    setExporting(true)
    setExportError(null)
    try {
      await onExport()
    } catch (err) {
      setExportError(errorMessage(err, err instanceof Error ? err.message : 'Could not export the report'))
    } finally {
      setExporting(false)
    }
  }

  return (
    <section className="viz-card">
      <header className="viz-card-head">
        <div>
          <h2>{title}</h2>
          {subtitle && <p className="viz-subtitle">{subtitle}</p>}
        </div>
        <div className="viz-card-actions">
          <div className="segmented" role="group" aria-label={`${title} view`}>
            <button type="button" className={view === 'chart' ? 'on' : ''} onClick={() => setView('chart')}>Chart</button>
            <button type="button" className={view === 'table' ? 'on' : ''} onClick={() => setView('table')}>Table</button>
          </div>
          {onExport && (
            <button type="button" onClick={handleExport} disabled={exporting}>{exporting ? 'Exporting…' : 'Export CSV'}</button>
          )}
        </div>
      </header>
      {(exportError || error) && <p className="error">{exportError ?? error}</p>}
      <div className={loading ? 'viz-body loading' : 'viz-body'} aria-busy={loading}>
        {view === 'chart' ? chart : table}
      </div>
    </section>
  )
}
