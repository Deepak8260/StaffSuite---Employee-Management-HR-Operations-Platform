import { createContext, useCallback, useEffect, useMemo, useState, type ReactNode } from 'react'
import * as authApi from '../api/auth'
import { setUnauthorizedHandler } from '../api/axiosInstance'
import type { AuthUser } from '../types/auth'
import { userFromToken } from '../utils/jwt'
import { clearToken, getToken, setToken } from '../utils/tokenStorage'

export interface AuthContextValue {
  user: AuthUser | null
  token: string | null
  login: (email: string, password: string) => Promise<void>
  logout: () => Promise<void>
}

// eslint-disable-next-line react-refresh/only-export-components
export const AuthContext = createContext<AuthContextValue | null>(null)

function initialSession(): { token: string | null; user: AuthUser | null } {
  const token = getToken()
  const user = token ? userFromToken(token) : null
  if (token && !user) clearToken()
  return { token: user ? token : null, user }
}

export function AuthProvider({ children }: { children: ReactNode }) {
  const [session, setSession] = useState(initialSession)

  const clearSession = useCallback(() => {
    clearToken()
    setSession({ token: null, user: null })
  }, [])

  useEffect(() => {
    setUnauthorizedHandler(clearSession)
    return () => setUnauthorizedHandler(null)
  }, [clearSession])

  const login = useCallback(async (email: string, password: string) => {
    const res = await authApi.login(email, password)
    const user = userFromToken(res.token)
    if (!user) throw new Error('Received an invalid token')
    setToken(res.token)
    setSession({ token: res.token, user })
  }, [])

  const logout = useCallback(async () => {
    try {
      await authApi.logout()
    } catch {
      /* the session is discarded locally regardless */
    }
    clearSession()
  }, [clearSession])

  const value = useMemo<AuthContextValue>(
    () => ({ user: session.user, token: session.token, login, logout }),
    [session, login, logout],
  )

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}
