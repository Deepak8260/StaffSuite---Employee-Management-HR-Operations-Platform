import { useEffect } from 'react'

interface Props {
  message: string | null
  /** Called when the message times out or is dismissed, so the owner can clear it. */
  onDone: () => void
  kind?: 'success' | 'error'
}

const VISIBLE_MS = 5000

/** Snackbar for the outcome of an action: floats over the page, dismisses itself, and never shifts the layout. */
export default function Flash({ message, onDone, kind = 'success' }: Props) {
  useEffect(() => {
    if (!message) return
    const timer = window.setTimeout(onDone, VISIBLE_MS)
    return () => window.clearTimeout(timer)
  }, [message, onDone])

  if (!message) return null
  return (
    <div className={`flash flash-${kind}`} role={kind === 'error' ? 'alert' : 'status'}>
      <span>{message}</span>
      <button type="button" aria-label="Dismiss" onClick={onDone}>×</button>
    </div>
  )
}
