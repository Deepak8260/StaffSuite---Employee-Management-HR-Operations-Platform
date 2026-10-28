import type { LucideIcon } from 'lucide-react'
import { Link } from 'react-router-dom'
import type { Tint } from './tints'

interface Props {
  to: string
  icon: LucideIcon
  tint: Tint
  label: string
}

export default function QuickActionButton({ to, icon: Icon, tint, label }: Props) {
  return (
    <Link to={to} className={`quick-action tint-${tint}`}>
      <Icon size={22} aria-hidden="true" />
      <span>{label}</span>
    </Link>
  )
}
