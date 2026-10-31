import { UserRoundCheck } from 'lucide-react'
import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { getProfile } from '../api/employees'
import { useAuth } from '../hooks/useAuth'
import type { ProfileCompletion } from '../types/employee'

/** Shown on Home until the signed-in employee has filled in everything they can on their own profile. */
export default function ProfileCompletionBanner() {
  const { user } = useAuth()
  const employeeId = user?.employeeId ?? null
  const [completion, setCompletion] = useState<ProfileCompletion | null>(null)

  useEffect(() => {
    if (employeeId === null) return
    let cancelled = false
    getProfile(employeeId)
      .then((p) => { if (!cancelled) setCompletion(p.completion) })
      .catch(() => { /* the banner is a nicety; stay hidden if it cannot load */ })
    return () => { cancelled = true }
  }, [employeeId])

  if (employeeId === null || !completion || completion.complete) return null
  return (
    <div className="completion-banner" role="status">
      <UserRoundCheck size={22} aria-hidden="true" />
      <div className="completion-banner-text">
        <strong>Complete your profile: {completion.percent}% done</strong>
        <div className="progress small" aria-hidden="true"><span style={{ width: `${completion.percent}%` }} /></div>
      </div>
      <Link to={`/employees/${employeeId}/profile`} className="button">Complete now</Link>
    </div>
  )
}
