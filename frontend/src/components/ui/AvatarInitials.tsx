interface Props {
  name: string
  size?: 'sm' | 'md' | 'lg' | 'xl'
}

export function initialsOf(name: string): string {
  const parts = name.trim().split(/\s+/).filter(Boolean)
  if (parts.length === 0) return '?'
  if (parts.length === 1) return parts[0].charAt(0).toUpperCase()
  return (parts[0].charAt(0) + parts[parts.length - 1].charAt(0)).toUpperCase()
}

export default function AvatarInitials({ name, size = 'md' }: Props) {
  return (
    <span className={`avatar avatar-${size}`} title={name} aria-hidden="true">
      {initialsOf(name)}
    </span>
  )
}
