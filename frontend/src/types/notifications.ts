export type NotificationType =
  | 'LEAVE_REQUESTED'
  | 'LEAVE_APPROVED'
  | 'LEAVE_REJECTED'
  | 'ATTENDANCE_CORRECTION_REQUESTED'
  | 'ATTENDANCE_CORRECTION_APPROVED'
  | 'ATTENDANCE_CORRECTION_REJECTED'
  | 'DOCUMENT_EXPIRING'

export interface NotificationItem {
  id: number
  type: NotificationType
  message: string
  referenceId: number | null
  read: boolean
  readAt: string | null
  createdAt: string
}

export interface PendingApprovals {
  leaveRequests: number
  attendanceCorrections: number
  total: number
}
