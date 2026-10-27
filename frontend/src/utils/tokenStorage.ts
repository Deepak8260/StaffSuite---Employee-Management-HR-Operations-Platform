const KEY = 'staffsuite.token'

// Kept in memory as well so the app still works when localStorage is unavailable.
let memoryToken: string | null = null

export function getToken(): string | null {
  if (memoryToken) return memoryToken
  try {
    memoryToken = localStorage.getItem(KEY)
  } catch {
    /* ignore */
  }
  return memoryToken
}

export function setToken(token: string): void {
  memoryToken = token
  try {
    localStorage.setItem(KEY, token)
  } catch {
    /* ignore */
  }
}

export function clearToken(): void {
  memoryToken = null
  try {
    localStorage.removeItem(KEY)
  } catch {
    /* ignore */
  }
}
