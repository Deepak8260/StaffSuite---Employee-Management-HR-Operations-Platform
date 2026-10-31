import { MoreVertical, type LucideIcon } from 'lucide-react'
import { useEffect, useRef, useState } from 'react'
import { Link } from 'react-router-dom'

export interface RowMenuItem {
  label: string
  icon: LucideIcon
  /** Opens a page. */
  to?: string
  /** Or runs an action. */
  onSelect?: () => void
  /** Shown in red, for things that cannot be undone. */
  danger?: boolean
}

/** A three-dots button that opens a menu of the things you can do with one table row. */
export default function RowMenu({ label, items }: { label: string; items: RowMenuItem[] }) {
  const button = useRef<HTMLButtonElement>(null)
  const menu = useRef<HTMLDivElement>(null)
  const [pos, setPos] = useState<{ left: number; top: number } | null>(null)

  useEffect(() => {
    if (!pos) return
    const close = () => setPos(null)
    const onDown = (e: MouseEvent) => {
      const t = e.target as Node
      if (!menu.current?.contains(t) && !button.current?.contains(t)) close()
    }
    const onKey = (e: KeyboardEvent) => { if (e.key === 'Escape') close() }
    document.addEventListener('mousedown', onDown)
    document.addEventListener('keydown', onKey)
    window.addEventListener('resize', close)
    window.addEventListener('scroll', close, true)   // a fixed menu would otherwise drift away from its row
    return () => {
      document.removeEventListener('mousedown', onDown)
      document.removeEventListener('keydown', onKey)
      window.removeEventListener('resize', close)
      window.removeEventListener('scroll', close, true)
    }
  }, [pos])

  function toggle() {
    if (pos) return setPos(null)
    const r = button.current?.getBoundingClientRect()
    if (!r) return
    const width = 208
    const height = items.length * 42 + 12
    // opens under the button, right edges aligned; flips upward near the bottom of the window
    const left = Math.max(8, Math.min(r.right - width, window.innerWidth - width - 8))
    const top = r.bottom + 6 + height > window.innerHeight ? Math.max(8, r.top - 6 - height) : r.bottom + 6
    setPos({ left, top })
  }

  return (
    <>
      <button ref={button} type="button" className="row-menu-btn" aria-label={label} aria-haspopup="menu" aria-expanded={pos !== null} onClick={toggle}>
        <MoreVertical size={18} aria-hidden="true" />
      </button>
      {pos && (
        <div ref={menu} className="row-menu" role="menu" style={{ left: pos.left, top: pos.top }}>
          {items.map(({ label: text, icon: Icon, to, onSelect, danger }) => {
            const content = <><Icon size={17} aria-hidden="true" /> {text}</>
            const cls = `row-menu-item ${danger ? 'danger-item' : ''}`
            return to
              ? <Link key={text} to={to} role="menuitem" className={cls} onClick={() => setPos(null)}>{content}</Link>
              : <button key={text} type="button" role="menuitem" className={cls} onClick={() => { setPos(null); onSelect?.() }}>{content}</button>
          })}
        </div>
      )}
    </>
  )
}
