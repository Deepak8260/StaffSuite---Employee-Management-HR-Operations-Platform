import { useEffect, useState } from 'react'
import { fetchPhotoObjectUrl } from '../../api/employees'
import { initialsOf } from '../../components/ui/AvatarInitials'
import type { Tint } from '../../components/ui/tints'

// photos fetched for the chart currently on screen; cleared when the page opens so a newly uploaded photo shows up
const cache = new Map<number, string>()

export function clearPhotoCache() {
  cache.forEach((url) => URL.revokeObjectURL(url))
  cache.clear()
}

/** The employee's photo when there is one, otherwise their initials in their department's colour. */
export default function OrgAvatar({ id, name, hasPhoto, tint, size = 'md' }: { id: number; name: string; hasPhoto: boolean; tint: Tint; size?: 'sm' | 'md' }) {
  const [src, setSrc] = useState<string | null>(() => cache.get(id) ?? null)

  useEffect(() => {
    if (!hasPhoto || cache.has(id)) return
    let cancelled = false
    fetchPhotoObjectUrl(id)
      .then((url) => {
        if (cancelled) {
          URL.revokeObjectURL(url)
          return
        }
        cache.set(id, url)
        setSrc(url)
      })
      .catch(() => { /* initials stay */ })
    return () => { cancelled = true }
  }, [id, hasPhoto])

  const shown = hasPhoto ? (src ?? cache.get(id) ?? null) : null
  return shown
    ? <img className={`org-avatar ${size}`} src={shown} alt="" />
    : <span className={`org-avatar ${size} tint-${tint}`} aria-hidden="true">{initialsOf(name)}</span>
}
