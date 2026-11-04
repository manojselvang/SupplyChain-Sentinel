import axios, {
  type AxiosInstance,
  type AxiosError,
  type InternalAxiosRequestConfig,
} from 'axios'
import type { ApiError, AuthResponse } from '../types'
import { getAccessToken, setAccessToken } from './tokenStore'

const baseURL = import.meta.env.VITE_API_BASE_URL || '/api/v1'

export const apiClient: AxiosInstance = axios.create({
  baseURL,
  headers: {
    'Content-Type': 'application/json',
  },
  timeout: 15000,
  withCredentials: true, // send HttpOnly refresh-token cookie
})

// ── Request interceptor: attach in-memory JWT access token ──
apiClient.interceptors.request.use((config) => {
  const token = getAccessToken()
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

// ── Response interceptor: automatic token refresh on 401 ──

let isRefreshing = false
let refreshPromise: Promise<string> | null = null

async function doRefresh(): Promise<string> {
  // Use a bare axios call to avoid triggering the interceptor recursively
  const response = await axios.post<AuthResponse>(`${baseURL}/auth/refresh`, null, {
    withCredentials: true,
  })
  const newToken = response.data.accessToken
  setAccessToken(newToken)
  return newToken
}

apiClient.interceptors.response.use(
  (response) => response,
  async (error: AxiosError) => {
    const originalRequest = error.config as InternalAxiosRequestConfig & {
      _retry?: boolean
    }

    // Only attempt refresh on 401, never on refresh/login endpoints
    if (
      error.response?.status !== 401 ||
      originalRequest._retry ||
      originalRequest.url?.includes('/auth/refresh') ||
      originalRequest.url?.includes('/auth/login')
    ) {
      return Promise.reject(error)
    }

    originalRequest._retry = true

    // If a refresh is already in flight, wait for it (prevents duplicate requests)
    if (isRefreshing && refreshPromise) {
      try {
        const newToken = await refreshPromise
        originalRequest.headers.Authorization = `Bearer ${newToken}`
        return apiClient(originalRequest)
      } catch {
        return Promise.reject(error)
      }
    }

    // Start a new refresh
    isRefreshing = true
    refreshPromise = doRefresh()

    try {
      const newToken = await refreshPromise
      originalRequest.headers.Authorization = `Bearer ${newToken}`
      return apiClient(originalRequest)
    } catch {
      // Refresh failed — clear in-memory token and redirect to login
      setAccessToken(null)
      if (window.location.pathname !== '/login') {
        window.location.href = '/login'
      }
      return Promise.reject(error)
    } finally {
      isRefreshing = false
      refreshPromise = null
    }
  }
)

// ── Error helper ───────────────────────────────────────────
export function extractApiError(err: AxiosError): ApiError {
  if (err.response?.data) {
    return err.response.data as ApiError
  }
  return {
    timestamp: new Date().toISOString(),
    status: err.response?.status ?? 0,
    error: err.name,
    message: err.message,
    path: err.config?.url ?? '',
    details: [],
  }
}

export default apiClient
