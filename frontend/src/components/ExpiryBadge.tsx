import { formatDateOnly } from '../utils/dates'

interface Props {
  expiryDate: string | null
  daysRemaining: number | null
}

/** Expiry date plus a colour-coded hint: red once expired or within a week, amber within 30 days. */
export default function ExpiryBadge({ expiryDate, daysRemaining }: Props) {
  if (!expiryDate || daysRemaining === null) return <span className="hint">No expiry</span>
  let tone = 'ok'
  let text = `${daysRemaining} days left`
  if (daysRemaining < 0) {
    tone = 'expired'
    text = `Expired ${-daysRemaining} day${daysRemaining === -1 ? '' : 's'} ago`
  } else if (daysRemaining === 0) {
    tone = 'expired'
    text = 'Expires today'
  } else if (daysRemaining <= 7) {
    tone = 'soon'
  } else if (daysRemaining <= 30) {
    tone = 'warn'
  }
  return (
    <span>
      {formatDateOnly(expiryDate)} <span className={`expiry ${tone}`}>{text}</span>
    </span>
  )
}
