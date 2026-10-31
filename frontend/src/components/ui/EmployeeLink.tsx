import type { ReactNode } from 'react'
import { Link } from 'react-router-dom'

/** An employee's name (or anything else) linking to their profile page. Use it wherever a person is named. */
export default function EmployeeLink({ id, children, className }: { id: number | null | undefined; children: ReactNode; className?: string }) {
  if (id == null) return <>{children}</>
  return <Link to={`/employees/${id}/profile`} className={`employee-link ${className ?? ''}`}>{children}</Link>
}
