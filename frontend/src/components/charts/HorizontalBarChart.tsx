import { useState } from 'react'
import ChartTooltip from './ChartTooltip'
import { roundedRightPath, truncate, useElementWidth } from './chartUtils'

export interface BarItem {
  key: string
  label: string
  value: number
  /** Extra tooltip text, e.g. "3 requests". */
  detail?: string
}

interface Props {
  items: BarItem[]
  /** One nominal series: every bar is the same colour (a CSS colour, normally var(--series-1)). */
  color: string
  /** Names the unit in the tooltip and for screen readers, e.g. "employees". */
  unit: string
  ariaLabel: string
  emptyText?: string
}

const ROW = 30
const BAR = 20          // thin: never fills the row
const VALUE_ROOM = 48   // room right of the longest bar for its value label

/** Horizontal bars for nominal categories, longest first, with the value at the tip. */
export default function HorizontalBarChart({ items, color, unit, ariaLabel, emptyText }: Props) {
  const [ref, width] = useElementWidth<HTMLDivElement>()
  const [active, setActive] = useState<{ index: number; x: number; y: number } | null>(null)

  const labelW = Math.min(180, Math.max(90, Math.round(width * 0.34)))
  const plotW = Math.max(0, width - labelW - VALUE_ROOM)
  const max = Math.max(1, ...items.map((i) => i.value))
  const scale = plotW / max
  const maxChars = Math.floor((labelW - 12) / 7)
  const height = items.length * ROW + 8

  return (
    <div className="viz-chart" ref={ref} style={{ height: Math.max(height, 40) }}>
      {items.length === 0 && <p className="viz-empty">{emptyText ?? 'No data for this period.'}</p>}
      {items.length > 0 && width > 0 && (
        <svg width={width} height={height} role="img" aria-label={ariaLabel}>
          <line x1={labelW} x2={labelW} y1={0} y2={height} className="viz-baseline" />
          {items.map((item, i) => {
            const top = 4 + i * ROW
            const w = item.value * scale
            return (
              <g
                key={item.key}
                tabIndex={0}
                role="img"
                aria-label={`${item.label}: ${item.value} ${unit}`}
                onPointerMove={(e) => {
                  const box = (e.currentTarget.ownerSVGElement as SVGSVGElement).getBoundingClientRect()
                  setActive({ index: i, x: e.clientX - box.left, y: e.clientY - box.top })
                }}
                onPointerLeave={() => setActive(null)}
                onFocus={() => setActive({ index: i, x: labelW + w, y: top })}
                onBlur={() => setActive(null)}
                className="viz-column"
              >
                <rect x={0} y={top} width={width} height={ROW} className={active?.index === i ? 'viz-hit active' : 'viz-hit'} />
                <text x={labelW - 8} y={top + ROW / 2 + 4} textAnchor="end" className="viz-label">{truncate(item.label, maxChars)}</text>
                {item.value > 0 && <path d={roundedRightPath(labelW, top + (ROW - BAR) / 2, w, BAR, 4)} fill={color} />}
                <text x={labelW + w + 8} y={top + ROW / 2 + 4} className="viz-value">{item.value}</text>
              </g>
            )
          })}
        </svg>
      )}
      {active && (
        <ChartTooltip
          x={active.x}
          y={active.y}
          containerWidth={width}
          title={items[active.index].label}
          rows={[{ label: unit, value: items[active.index].value, color }]}
          note={items[active.index].detail}
        />
      )}
    </div>
  )
}
