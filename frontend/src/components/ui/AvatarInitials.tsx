interface Props {
  name: string
  size?: 'sm' | 'md' | 'lg'
}

const PALETTE = 6

/** Same name, same colour, every time: the hue comes from a hash of the name. */
function hueSlot(name: string): number {
  let hash = 0
  for (const ch of name) hash = (hash * 31 + ch.charCodeAt(0)) >>> 0
  return hash % PALETTE
}

export function initialsOf(name: string): string {
  const parts = name.trim().split(/\s+/).filter(Boolean)
  if (parts.length === 0) return '?'
  if (parts.length === 1) return parts[0].charAt(0).toUpperCase()
  return (parts[0].charAt(0) + parts[parts.length - 1].charAt(0)).toUpperCase()
}

export default function AvatarInitials({ name, size = 'md' }: Props) {
  return (
    <span className={`avatar avatar-${size} avatar-c${hueSlot(name)}`} title={name} aria-hidden="true">
      {initialsOf(name)}
    </span>
  )
}
