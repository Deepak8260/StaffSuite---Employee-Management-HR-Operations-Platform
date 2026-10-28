import { formatDateOnly } from '../../utils/dates'
import StatusPill, { type PillTone } from '../../components/ui/StatusPill'

interface Props {
  expiryDate: string | null
  daysRemaining: number | null
}

/** Expiry date plus a status pill: expired or within a week = danger, within 30 days = warning, else neutral. */
export default function ExpiryPill({ expiryDate, daysRemaining }: Props) {
  if (!expiryDate || daysRemaining === null) return <span className="hint">No expiry</span>
  let tone: PillTone = 'neutral'
  let text = `${daysRemaining} days left`
  if (daysRemaining < 0) {
    tone = 'danger'
    text = `Expired ${-daysRemaining} day${daysRemaining === -1 ? '' : 's'} ago`
  } else if (daysRemaining === 0) {
    tone = 'danger'
    text = 'Expires today'
  } else if (daysRemaining <= 7) {
    tone = 'danger'
  } else if (daysRemaining <= 30) {
    tone = 'warning'
  }
  return (
    <span className="expiry-cell">
      <span>{formatDateOnly(expiryDate)}</span> <StatusPill tone={tone}>{text}</StatusPill>
    </span>
  )
}
