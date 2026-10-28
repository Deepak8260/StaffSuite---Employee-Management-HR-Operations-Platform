import type { ReactNode } from 'react'

export type PillTone = 'success' | 'warning' | 'danger' | 'neutral' | 'info' | 'soon'

interface Props {
  tone?: PillTone
  children: ReactNode
}

export default function StatusPill({ tone = 'neutral', children }: Props) {
  return <span className={`pill pill-${tone}`}>{children}</span>
}

/** Default colours for the statuses used across the app, so every page paints them the same way. */
const TONES: Record<string, PillTone> = {
  PENDING: 'warning',
  APPROVED: 'success',
  REJECTED: 'danger',
  CANCELLED: 'neutral',
  PRESENT: 'success',
  LATE: 'warning',
  HALF_DAY: 'warning',
  ABSENT: 'danger',
  ON_LEAVE: 'info',
  ACTIVE: 'success',
  PROBATION: 'warning',
  TERMINATED: 'neutral',
  INACTIVE: 'neutral',
}

export function toneFor(status: string): PillTone {
  return TONES[status] ?? 'neutral'
}

/** A pill for a status code ("HALF_DAY" reads "Half day"). */
export function StatusCodePill({ status }: { status: string }) {
  const label = status.charAt(0) + status.slice(1).toLowerCase().replace(/_/g, ' ')
  return <StatusPill tone={toneFor(status)}>{label}</StatusPill>
}
