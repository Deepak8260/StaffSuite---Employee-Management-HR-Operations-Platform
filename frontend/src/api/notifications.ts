import type { PageResponse } from '../types/auth'
import type { NotificationItem, PendingApprovals } from '../types/notifications'
import { api } from './axiosInstance'

export async function listNotifications(page: number, size: number, unreadOnly = false): Promise<PageResponse<NotificationItem>> {
  const { data } = await api.get<PageResponse<NotificationItem>>('/api/v1/notifications', {
    params: { page, size, unreadOnly },
  })
  return data
}

export async function getUnreadCount(): Promise<number> {
  const { data } = await api.get<{ count: number }>('/api/v1/notifications/unread-count')
  return data.count
}

export async function getPendingApprovals(): Promise<PendingApprovals> {
  const { data } = await api.get<PendingApprovals>('/api/v1/notifications/pending-approvals')
  return data
}

export async function markNotificationRead(id: number): Promise<NotificationItem> {
  const { data } = await api.put<NotificationItem>(`/api/v1/notifications/${id}/read`)
  return data
}

export async function markAllNotificationsRead(): Promise<number> {
  const { data } = await api.put<{ updated: number }>('/api/v1/notifications/read-all')
  return data.updated
}
