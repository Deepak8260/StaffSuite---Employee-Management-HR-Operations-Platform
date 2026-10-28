import { useEffect, useRef, useState, type RefObject } from 'react'

/** Round-number axis ticks from 0 up to just above the largest value (counts, so steps are whole numbers). */
export function niceTicks(max: number, target = 4): number[] {
  if (max <= 0) return [0, 1]
  const raw = Math.max(1, max / target)
  const magnitude = Math.pow(10, Math.floor(Math.log10(raw)))
  const step = [1, 2, 5, 10].map((m) => m * magnitude).find((s) => s >= raw) ?? raw
  const ticks: number[] = []
  for (let v = 0; v < max + step; v += step) {
    ticks.push(v)
    if (v >= max) break
  }
  return ticks
}

/** 999 -> "999", 1200 -> "1.2K". */
export function compact(n: number): string {
  if (n >= 1_000_000) return `${+(n / 1_000_000).toFixed(1)}M`
  if (n >= 1000) return `${+(n / 1000).toFixed(1)}K`
  return String(n)
}

/** A bar with only its top (data-end) corners rounded; the baseline end stays square. */
export function roundedTopPath(x: number, y: number, w: number, h: number, r: number): string {
  const rr = Math.max(0, Math.min(r, w / 2, h))
  return `M${x},${y + h} L${x},${y + rr} Q${x},${y} ${x + rr},${y} L${x + w - rr},${y} Q${x + w},${y} ${x + w},${y + rr} L${x + w},${y + h} Z`
}

/** A horizontal bar with only its right (data-end) corners rounded. */
export function roundedRightPath(x: number, y: number, w: number, h: number, r: number): string {
  const rr = Math.max(0, Math.min(r, h / 2, w))
  return `M${x},${y} L${x + w - rr},${y} Q${x + w},${y} ${x + w},${y + rr} L${x + w},${y + h - rr} Q${x + w},${y + h} ${x + w - rr},${y + h} L${x},${y + h} Z`
}

export function truncate(text: string, maxChars: number): string {
  return text.length <= maxChars ? text : text.slice(0, Math.max(1, maxChars - 1)) + '…'
}

/** Tracks an element's width so charts draw at real pixel size (text is never scaled). */
export function useElementWidth<T extends HTMLElement>(): [RefObject<T | null>, number] {
  const ref = useRef<T | null>(null)
  const [width, setWidth] = useState(0)
  useEffect(() => {
    const el = ref.current
    if (!el) return
    setWidth(el.getBoundingClientRect().width)
    const observer = new ResizeObserver((entries) => setWidth(entries[0].contentRect.width))
    observer.observe(el)
    return () => observer.disconnect()
  }, [])
  return [ref, width]
}
