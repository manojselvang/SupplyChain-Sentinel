import {
  createContext,
  useContext,
  useState,
  useEffect,
  useCallback,
  useSyncExternalStore,
  type ReactNode,
} from 'react'
import type { User, Role } from '../types'
import * as authApi from '../api/auth'
import { getAccessToken, setAccessToken, subscribeTokenStore } from '../api/tokenStore'

interface AuthContextValue {
  user: User | null
  isAuthenticated: boolean
  loading: boolean
  login: (username: string, password: string) => Promise<void>
  logout: () => Promise<void>
  hasRole: (role: Role) => boolean
  hasAnyRole: (...roles: Role[]) => boolean
}

const AuthContext = createContext<AuthContextValue | undefined>(undefined)

/**
 * Authentication context backed by the real backend API.
 *
 * Access token is kept in-memory only (never localStorage) to mitigate XSS.
 * Refresh token is an HttpOnly cookie managed entirely by the backend.
 * On page load the app silently refreshes using the cookie; if the cookie
 * is absent or expired the user is treated as unauthenticated.
 */
export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<User | null>(null)
  const [loading, setLoading] = useState(true)

  // Subscribe to the in-memory token store so the component re-renders
  // when the token changes (e.g. after refresh or logout)
  const accessToken = useSyncExternalStore(subscribeTokenStore, getAccessToken)

  // On mount: attempt silent refresh via the HttpOnly cookie.
  // This replaces the old localStorage check — there is no persisted token.
  useEffect(() => {
    authApi
      .refreshToken()
      .then((res) => {
        setAccessToken(res.accessToken)
        setUser(res.user)
      })
      .catch(() => {
        // No valid refresh cookie — user is unauthenticated
        setAccessToken(null)
        setUser(null)
      })
      .finally(() => setLoading(false))
  }, [])

  const login = useCallback(async (username: string, password: string) => {
    const response = await authApi.login(username, password)
    setAccessToken(response.accessToken)
    setUser(response.user)
  }, [])

  const logout = useCallback(async () => {
    try {
      await authApi.logout()
    } catch {
      // Even if the API call fails, clear local state
    }
    setAccessToken(null)
    setUser(null)
  }, [])

  const hasRole = useCallback(
    (role: Role): boolean => user?.roles?.includes(role) ?? false,
    [user]
  )

  const hasAnyRole = useCallback(
    (...roles: Role[]): boolean => roles.some((r) => hasRole(r)),
    [hasRole]
  )

  const isAuthenticated = accessToken !== null && user !== null

  return (
    <AuthContext.Provider value={{ user, isAuthenticated, loading, login, logout, hasRole, hasAnyRole }}>
      {children}
    </AuthContext.Provider>
  )
}

export function useAuth() {
  const ctx = useContext(AuthContext)
  if (!ctx) throw new Error('useAuth must be used within AuthProvider')
  return ctx
}
