// Shared TypeScript types for the SupplyChain Sentinel frontend.
// These will be expanded as modules are implemented in later phases.

export type Role =
  | 'ADMIN'
  | 'SECURITY_ENGINEER'
  | 'DEVELOPER'
  | 'RELEASE_MANAGER'
  | 'AUDITOR'

export interface User {
  id: string
  username: string
  email: string
  displayName: string
  roles: Role[]
}

export interface AuthResponse {
  accessToken: string
  expiresIn: number
  user: User
}

export interface ApiError {
  timestamp: string
  status: number
  error: string
  message: string
  path: string
  details: string[]
}

export interface PageResponse<T> {
  content: T[]
  totalElements: number
  totalPages: number
  page: number
  size: number
}
