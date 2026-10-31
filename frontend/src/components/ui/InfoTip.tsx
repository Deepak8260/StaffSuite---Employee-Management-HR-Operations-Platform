import { useId, useRef, useState, type ReactNode } from 'react'

/** How long the card stays after the pointer leaves, so it can cross the gap from the icon onto the card. */
const LINGER_MS = 250

interface Pos { left: number; top: number; width: number; maxHeight: number }

/**
 * A small circled "i". Its card opens to the right of the icon on hover or keyboard focus, and toggles on tap for
 * touch screens. The card is placed from the icon's position on screen, so a dialog's edges never clip it.
 */
export default function InfoTip({ label, children }: { label: string; children: ReactNode }) {
  const [pinned, setPinned] = useState(false)
  const [pos, setPos] = useState<Pos | null>(null)
  const button = useRef<HTMLButtonElement>(null)
  const timer = useRef<number | undefined>(undefined)
  const id = useId()

  function place() {
    window.clearTimeout(timer.current)
    const r = button.current?.getBoundingClientRect()
    if (!r) return
    const margin = 16
    const tall = 640 // roughly how tall the card is, so it can sit fully on screen
    const room = window.innerWidth - r.right - 10 - margin
    const beside = room >= 320
    const top = beside
      ? Math.max(margin, Math.min(r.top - 14, window.innerHeight - tall))
      : Math.min(r.bottom + 8, Math.max(margin, window.innerHeight - tall))
    // not enough room beside the icon (narrow screens): use the full width just below it
    setPos({ left: beside ? r.right + 10 : margin, top, width: beside ? Math.min(600, room) : window.innerWidth - margin * 2, maxHeight: window.innerHeight - top - margin })
  }

  function hide() {
    window.clearTimeout(timer.current)
    setPinned(false)
    setPos(null)
  }

  return (
    <span className="info-tip" onMouseEnter={place} onMouseLeave={() => { if (!pinned) timer.current = window.setTimeout(() => setPos(null), LINGER_MS) }}>
      <button ref={button} type="button" className="info-tip-btn" aria-label={label} aria-describedby={pos ? id : undefined}
        aria-expanded={pos !== null}
        onFocus={place} onBlur={hide}
        onClick={() => { if (pinned) hide(); else { place(); setPinned(true) } }}>
        i
      </button>
      {pos && (
        <span role="tooltip" id={id} onMouseDown={(e) => e.preventDefault()} className="info-tip-body" style={{ left: pos.left, top: pos.top, width: pos.width, maxHeight: pos.maxHeight }}>
          {children}
        </span>
      )}
    </span>
  )
}
