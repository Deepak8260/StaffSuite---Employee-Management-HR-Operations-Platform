import { useState } from 'react'
import ChartTooltip from './ChartTooltip'
import { compact, niceTicks, roundedTopPath, useElementWidth } from './chartUtils'

export interface ChartSeries {
  key: string
  label: string
  /** A CSS colour, normally a var(--series-N) token. */
  color: string
}

export interface ChartCategory {
  /** Short text under the column. */
  label: string
  /** Longer text for the tooltip heading and screen readers. */
  title: string
}

interface Props {
  categories: ChartCategory[]
  series: ChartSeries[]
  /** values[seriesIndex][categoryIndex] */
  values: number[][]
  ariaLabel: string
  height?: number
  /** Print the value on top of each column (only used for a single series with few columns). */
  capLabels?: boolean
  emptyText?: string
  /** Extra line in the tooltip for one column. */
  note?: (index: number) => string | null
}

const MARGIN = { top: 16, right: 8, bottom: 30, left: 38 }
const MAX_BAR = 24          // bars are thin: never fill the slot
const GAP = 2               // surface-coloured gap between stacked segments
const MIN_LABEL_PX = 56

/**
 * Columns, stacked when there is more than one series. Each column is its own hover/focus target
 * (full height, wider than the bar), so the reader never has to land on a sliver.
 */
export default function ColumnChart({ categories, series, values, ariaLabel, height = 260, capLabels, emptyText, note }: Props) {
  const [ref, width] = useElementWidth<HTMLDivElement>()
  const [active, setActive] = useState<{ index: number; x: number; y: number } | null>(null)

  const n = categories.length
  const totals = categories.map((_, i) => series.reduce((sum, _s, si) => sum + (values[si]?.[i] ?? 0), 0))
  const ticks = niceTicks(Math.max(0, ...totals))
  const yMax = ticks[ticks.length - 1]
  const plotW = Math.max(0, width - MARGIN.left - MARGIN.right)
  const plotH = height - MARGIN.top - MARGIN.bottom
  const band = n > 0 ? plotW / n : 0
  const barW = Math.max(1, Math.min(MAX_BAR, band - 2))
  const scale = plotH / yMax
  const labelStep = Math.max(1, Math.ceil(MIN_LABEL_PX / Math.max(band, 1)))
  const allZero = totals.every((t) => t === 0)
  const showCaps = !!capLabels && series.length === 1 && n <= 12 && barW >= 14

  function show(index: number, x: number, y: number) {
    setActive({ index, x, y })
  }

  return (
    <div className="viz-chart" ref={ref} style={{ height }}>
      {n === 0 && <p className="viz-empty">{emptyText ?? 'No data for this period.'}</p>}
      {n > 0 && width > 0 && (
        <svg width={width} height={height} role="img" aria-label={ariaLabel}>
          {/* hairline, solid, recessive gridlines with compact tick labels */}
          {ticks.map((t) => {
            const y = MARGIN.top + plotH - t * scale
            return (
              <g key={t}>
                <line x1={MARGIN.left} x2={width - MARGIN.right} y1={y} y2={y} className={t === 0 ? 'viz-baseline' : 'viz-grid'} />
                <text x={MARGIN.left - 8} y={y + 4} textAnchor="end" className="viz-tick">{compact(t)}</text>
              </g>
            )
          })}

          {categories.map((c, i) => {
            const cx = MARGIN.left + band * i
            const mid = cx + band / 2
            let cumulative = 0
            let hasBelow = false
            let topSeries = -1
            series.forEach((_s, si) => {
              if ((values[si]?.[i] ?? 0) > 0) topSeries = si
            })
            const summary = `${c.title}: ${series.map((s, si) => `${s.label} ${values[si]?.[i] ?? 0}`).join(', ')}`
            return (
              <g
                key={c.title + i}
                tabIndex={0}
                role="img"
                aria-label={summary}
                onPointerMove={(e) => {
                  const box = (e.currentTarget.ownerSVGElement as SVGSVGElement).getBoundingClientRect()
                  show(i, e.clientX - box.left, e.clientY - box.top)
                }}
                onPointerLeave={() => setActive(null)}
                onFocus={() => show(i, mid, MARGIN.top + 8)}
                onBlur={() => setActive(null)}
                className="viz-column"
              >
                {/* the hit target: the whole column band, much bigger than the mark */}
                <rect x={cx} y={MARGIN.top} width={band} height={plotH} className={active?.index === i ? 'viz-hit active' : 'viz-hit'} />
                {series.map((s, si) => {
                  const v = values[si]?.[i] ?? 0
                  if (v <= 0) return null
                  const segH = v * scale
                  const top = MARGIN.top + plotH - (cumulative + v) * scale
                  cumulative += v
                  const gapped = hasBelow && segH > 3
                  const h = gapped ? segH - GAP : segH
                  hasBelow = true
                  const x = mid - barW / 2
                  return si === topSeries
                    ? <path key={s.key} d={roundedTopPath(x, top, barW, h, 4)} fill={s.color} />
                    : <rect key={s.key} x={x} y={top} width={barW} height={h} fill={s.color} />
                })}
                {showCaps && totals[i] > 0 && (
                  <text x={mid} y={MARGIN.top + plotH - totals[i] * scale - 5} textAnchor="middle" className="viz-value">{totals[i]}</text>
                )}
                {i % labelStep === 0 && (
                  <text x={mid} y={height - 9} textAnchor="middle" className="viz-tick">{c.label}</text>
                )}
              </g>
            )
          })}
          {allZero && (
            <text x={MARGIN.left + plotW / 2} y={MARGIN.top + plotH / 2} textAnchor="middle" className="viz-tick">
              {emptyText ?? 'Nothing recorded in this period'}
            </text>
          )}
        </svg>
      )}
      {active && (
        <ChartTooltip
          x={active.x}
          y={active.y}
          containerWidth={width}
          title={categories[active.index].title}
          rows={[
            ...series.map((s, si) => ({ label: s.label, value: values[si]?.[active.index] ?? 0, color: s.color })),
            ...(series.length > 1 ? [{ label: 'Total', value: totals[active.index] }] : []),
          ]}
          note={note?.(active.index)}
        />
      )}
    </div>
  )
}
