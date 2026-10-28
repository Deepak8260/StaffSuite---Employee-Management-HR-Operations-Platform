import type { ReactNode } from 'react'

export interface TooltipRow {
  label: string
  value: number | string
  /** CSS colour of the short key line; omitted for rows that are not a series (e.g. a total). */
  color?: string
}

interface Props {
  /** Pointer position inside the chart container. */
  x: number
  y: number
  containerWidth: number
  title: string
  rows: TooltipRow[]
  note?: ReactNode
}

/**
 * One tooltip listing every series at the hovered position. The value leads and the series name
 * follows; rows are keyed with a short line in the series colour. All text goes in as text nodes.
 */
export default function ChartTooltip({ x, y, containerWidth, title, rows, note }: Props) {
  const flip = x > containerWidth / 2
  return (
    <div
      className="viz-tooltip"
      role="status"
      style={{ left: flip ? x - 12 : x + 12, top: Math.max(0, y), transform: flip ? 'translateX(-100%)' : undefined }}
    >
      <div className="viz-tooltip-title">{title}</div>
      {rows.map((r) => (
        <div className="viz-tooltip-row" key={r.label}>
          <span className="viz-tooltip-key" style={{ background: r.color ?? 'transparent' }} />
          <strong>{r.value}</strong>
          <span>{r.label}</span>
        </div>
      ))}
      {note && <div className="viz-tooltip-note">{note}</div>}
    </div>
  )
}
