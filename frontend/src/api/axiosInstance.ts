import axios, { AxiosError } from 'axios'
import type { ApiError } from '../types/auth'
import { getConfig } from '../utils/config'
import { getToken } from '../utils/tokenStorage'

export const api = axios.create({
  baseURL: getConfig().API_BASE_URL,
  headers: { 'Content-Type': 'application/json' },
})

let onUnauthorized: (() => void) | null = null

/** AuthProvider registers this so a 401 clears the session and sends the user to /login. */
export function setUnauthorizedHandler(handler: (() => void) | null): void {
  onUnauthorized = handler
}

// Auth endpoints that legitimately return 401/400 without meaning "session expired".
const PUBLIC_PATHS = ['/auth/login', '/auth/reset-password-request', '/auth/reset-password-confirm']

api.interceptors.request.use((config) => {
  const token = getToken()
  if (token) config.headers.Authorization = `Bearer ${token}`
  return config
})

api.interceptors.response.use(
  (response) => response,
  (error: AxiosError<ApiError>) => {
    const url = error.config?.url ?? ''
    if (error.response?.status === 401 && !PUBLIC_PATHS.some((p) => url.includes(p))) {
      onUnauthorized?.()
    }
    return Promise.reject(error)
  },
)

export function errorMessage(error: unknown, fallback = 'Something went wrong'): string {
  if (axios.isAxiosError<ApiError>(error)) {
    return error.response?.data?.message ?? fallback
  }
  return fallback
}
