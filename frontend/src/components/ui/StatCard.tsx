import type { LucideIcon } from 'lucide-react'
import type { ReactNode } from 'react'
import type { Tint } from './tints'

interface Props {
  icon: LucideIcon
  tint: Tint
  label: string
  value: string | number
  /** Small context line under the number, e.g. "75% attendance rate". */
  trend?: ReactNode
}

export default function StatCard({ icon: Icon, tint, label, value, trend }: Props) {
  return (
    <div className="stat-card">
      <span className={`icon-square tint-${tint}`}><Icon size={22} aria-hidden="true" /></span>
      <div className="stat-body">
        <span className="stat-label">{label}</span>
        <span className="stat-value">{value}</span>
        {trend && <span className="stat-trend">{trend}</span>}
      </div>
    </div>
  )
}
