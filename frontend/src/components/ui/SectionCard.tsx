import type { LucideIcon } from 'lucide-react'
import type { ReactNode } from 'react'
import { Link } from 'react-router-dom'
import type { Tint } from './tints'

interface Props {
  title: string
  icon?: LucideIcon
  tint?: Tint
  /** Adds a "View all" link in the header. */
  viewAllTo?: string
  viewAllLabel?: string
  /** Extra controls on the right of the header. */
  actions?: ReactNode
  children: ReactNode
  className?: string
}

/** The white rounded card every list, table and form block sits in. */
export default function SectionCard({ title, icon: Icon, tint = 'blue', viewAllTo, viewAllLabel = 'View all', actions, children, className = '' }: Props) {
  return (
    <section className={`section-card ${className}`}>
      <header className="section-head">
        <h2>
          {Icon && <span className={`icon-square sm tint-${tint}`}><Icon size={16} aria-hidden="true" /></span>}
          {title}
        </h2>
        <div className="section-head-right">
          {actions}
          {viewAllTo && <Link className="view-all" to={viewAllTo}>{viewAllLabel}</Link>}
        </div>
      </header>
      <div className="section-body">{children}</div>
    </section>
  )
}
