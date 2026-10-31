import { useEffect, useState } from 'react'
import { fetchPhotoObjectUrl } from '../../api/employees'
import AvatarInitials from './AvatarInitials'

interface Props {
  employeeId: number
  name: string
  /** Set when the employee has a photo; changing it (a new upload) reloads the image. */
  photoUrl: string | null
  /** Bumped by the parent after an upload so the same URL is fetched again. */
  version?: number
  className?: string
}

/** The employee's photo, or their initials in the same circle when there is none (or it cannot be loaded). */
export default function PhotoAvatar({ employeeId, name, photoUrl, version = 0, className = '' }: Props) {
  const [loaded, setLoaded] = useState<{ key: string; src: string } | null>(null)
  const key = photoUrl ? `${employeeId}:${photoUrl}:${version}` : null

  useEffect(() => {
    if (!key) return
    let cancelled = false
    let created: string | null = null
    fetchPhotoObjectUrl(employeeId)
      .then((url) => {
        if (cancelled) {
          URL.revokeObjectURL(url)
          return
        }
        created = url
        setLoaded({ key, src: url })
      })
      .catch(() => { /* fall back to initials */ })
    return () => {
      cancelled = true
      if (created) URL.revokeObjectURL(created)
    }
  }, [employeeId, key])

  const src = key && loaded?.key === key ? loaded.src : null
  return src
    ? <img className={`photo-avatar ${className}`} src={src} alt={name} />
    : <AvatarInitials name={name} size="xl" />
}
