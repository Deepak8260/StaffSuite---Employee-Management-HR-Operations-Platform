import type { ReactNode } from 'react'

export interface Column<T> {
  key: string
  header: ReactNode
  render: (row: T) => ReactNode
  align?: 'left' | 'right' | 'center'
  /** Optional class on the cell, e.g. for a narrow column. */
  className?: string
}

interface Props<T> {
  columns: Column<T>[]
  /** null means "still loading". */
  rows: T[] | null
  rowKey: (row: T) => string | number
  emptyText?: string
  rowClassName?: (row: T) => string | undefined
  caption?: string
}

/** One table look for the whole app: header row, row hover, scrolls sideways on narrow screens, loading and empty states. */
export default function DataTable<T>({ columns, rows, rowKey, emptyText = 'Nothing to show yet.', rowClassName, caption }: Props<T>) {
  return (
    <div className="table-wrap">
      <table className="data-table">
        {caption && <caption className="sr-only">{caption}</caption>}
        <thead>
          <tr>
            {columns.map((c) => (
              <th key={c.key} className={`${c.align ? `a-${c.align}` : ''} ${c.className ?? ''}`}>{c.header}</th>
            ))}
          </tr>
        </thead>
        <tbody>
          {rows === null && (
            <tr><td colSpan={columns.length} className="table-state">Loading…</td></tr>
          )}
          {rows !== null && rows.length === 0 && (
            <tr><td colSpan={columns.length} className="table-state">{emptyText}</td></tr>
          )}
          {rows?.map((row) => (
            <tr key={rowKey(row)} className={rowClassName?.(row)}>
              {columns.map((c) => (
                <td key={c.key} className={`${c.align ? `a-${c.align}` : ''} ${c.className ?? ''}`}>{c.render(row)}</td>
              ))}
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  )
}
