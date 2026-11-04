/**
 * In-memory access token store.
 *
 * The token is kept in a module-level variable that does NOT persist across
 * page refreshes. This prevents XSS attacks from stealing the token out of
 * localStorage. On refresh, the app re-authenticates silently via the
 * HttpOnly refresh-token cookie.
 *
 * Uses a subscribe/getSnapshot pattern compatible with React's
 * useSyncExternalStore so components re-render when the token changes.
 */

let accessToken: string | null = null
const listeners = new Set<() => void>()

/**
 * Returns the current access token (or null if not authenticated).
 * Called by the Axios request interceptor on every API call.
 */
export function getAccessToken(): string | null {
  return accessToken
}

/**
 * Sets or clears the access token. Notifies all subscribers so React
 * components using useSyncExternalStore re-render.
 */
export function setAccessToken(token: string | null): void {
  accessToken = token
  listeners.forEach((l) => l())
}

/**
 * Subscribe to token changes. Returns an unsubscribe function.
 * Used by React's useSyncExternalStore in AuthContext.
 */
export function subscribeTokenStore(listener: () => void): () => void {
  listeners.add(listener)
  return () => {
    listeners.delete(listener)
  }
}
