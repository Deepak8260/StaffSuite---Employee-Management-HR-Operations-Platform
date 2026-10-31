import type { Role } from '../types/auth'
import type { NotificationItem, NotificationType } from '../types/notifications'

/** Fired after notifications are read/changed anywhere, so the bell can refresh its count straight away. */
export const NOTIFICATIONS_CHANGED = 'staffsuite:notifications-changed'

export function announceNotificationsChanged(): void {
  window.dispatchEvent(new Event(NOTIFICATIONS_CHANGED))
}

export const TYPE_LABELS: Record<NotificationType, string> = {
  LEAVE_REQUESTED: 'Leave request',
  LEAVE_APPROVED: 'Leave approved',
  LEAVE_REJECTED: 'Leave rejected',
  ATTENDANCE_CORRECTION_REQUESTED: 'Attendance correction',
  ATTENDANCE_CORRECTION_APPROVED: 'Correction approved',
  ATTENDANCE_CORRECTION_REJECTED: 'Correction rejected',
  DOCUMENT_EXPIRING: 'Document expiring',
  PROFILE_COMPLETED: 'Profile completed',
}

/** Where to take the user when they open a notification: the page that shows the item it is about. */
export function notificationPath(n: NotificationItem, role: Role | undefined): string | null {
  const manager = role === 'MANAGER'
  const id = n.referenceId
  switch (n.type) {
    case 'LEAVE_REQUESTED':
      return `${manager ? '/leave/team' : '/leave/all'}${id ? `?request=${id}` : ''}`
    case 'LEAVE_APPROVED':
    case 'LEAVE_REJECTED':
      return `/leave${id ? `?request=${id}` : ''}`
    case 'ATTENDANCE_CORRECTION_REQUESTED':
      return `${manager ? '/attendance/team' : '/attendance/reports'}${id ? `?correction=${id}` : ''}`
    case 'ATTENDANCE_CORRECTION_APPROVED':
    case 'ATTENDANCE_CORRECTION_REJECTED':
      return `/attendance${id ? `?correction=${id}` : ''}`
    case 'DOCUMENT_EXPIRING':
      return '/admin/documents/expiring'
    case 'PROFILE_COMPLETED':
      return id ? `/employees/${id}/profile` : null
    default:
      return null
  }
}

export function timeAgo(iso: string): string {
  const seconds = Math.max(0, Math.round((Date.now() - new Date(iso).getTime()) / 1000))
  if (seconds < 60) return 'just now'
  const minutes = Math.floor(seconds / 60)
  if (minutes < 60) return `${minutes} min ago`
  const hours = Math.floor(minutes / 60)
  if (hours < 24) return `${hours} h ago`
  const days = Math.floor(hours / 24)
  if (days < 7) return `${days} d ago`
  return new Date(iso).toLocaleDateString()
}
