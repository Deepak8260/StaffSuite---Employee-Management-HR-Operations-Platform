import type { LoginResponse } from '../types/auth'
import { api } from './axiosInstance'

export async function login(email: string, password: string): Promise<LoginResponse> {
  const { data } = await api.post<LoginResponse>('/api/v1/auth/login', { email, password })
  return data
}

export async function logout(): Promise<void> {
  await api.post('/api/v1/auth/logout')
}

export async function changePassword(currentPassword: string, newPassword: string): Promise<void> {
  await api.post('/api/v1/auth/change-password', { currentPassword, newPassword })
}

export async function requestPasswordReset(email: string): Promise<void> {
  await api.post('/api/v1/auth/reset-password-request', { email })
}

export async function confirmPasswordReset(token: string, newPassword: string): Promise<void> {
  await api.post('/api/v1/auth/reset-password-confirm', { token, newPassword })
}
