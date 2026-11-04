import apiClient from './client'
import type { AuthResponse, User } from '../types'

/**
 * Authentication API service.
 * The refresh token is handled automatically by the HttpOnly cookie set by the backend.
 */

export async function login(username: string, password: string): Promise<AuthResponse> {
  const response = await apiClient.post<AuthResponse>('/auth/login', { username, password })
  return response.data
}

export async function refreshToken(): Promise<AuthResponse> {
  const response = await apiClient.post<AuthResponse>('/auth/refresh')
  return response.data
}

export async function logout(): Promise<void> {
  await apiClient.post('/auth/logout')
}

export async function getCurrentUser(): Promise<User> {
  const response = await apiClient.get<User>('/auth/me')
  return response.data
}
