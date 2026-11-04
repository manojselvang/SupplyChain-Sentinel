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

// ── Application Inventory ──────────────────────────────────

export interface Application {
  id: string
  applicationKey: string
  name: string
  description: string | null
  businessUnit: string | null
  businessOwner: string | null
  technicalOwner: string | null
  criticality: string
  lifecycleStatus: string
  dataClassification: string
  repositoryUrl: string | null
  primaryLanguage: string | null
  hostingEnvironment: string
  internetFacing: boolean
  createdAt: string
  updatedAt: string
}

export interface CreateApplicationRequest {
  applicationKey: string
  name: string
  description?: string
  businessUnit?: string
  businessOwner?: string
  technicalOwner?: string
  criticality: string
  lifecycleStatus: string
  dataClassification: string
  repositoryUrl?: string
  primaryLanguage?: string
  hostingEnvironment: string
  internetFacing?: boolean
}

export interface UpdateApplicationRequest {
  name?: string
  description?: string
  businessUnit?: string
  businessOwner?: string
  technicalOwner?: string
  criticality?: string
  lifecycleStatus?: string
  dataClassification?: string
  repositoryUrl?: string
  primaryLanguage?: string
  hostingEnvironment?: string
  internetFacing?: boolean
}

export interface Page<T> {
  content: T[]
  totalElements: number
  totalPages: number
  number: number
  size: number
  first: boolean
  last: boolean
  empty: boolean
}

// Enum constants for dropdowns
export const CRITICALITY_LEVELS = ['CRITICAL', 'HIGH', 'MEDIUM', 'LOW'] as const
export const LIFECYCLE_STATUSES = ['PLANNING', 'ACTIVE', 'DEPRECATED', 'RETIRED'] as const
export const DATA_CLASSIFICATIONS = ['PUBLIC', 'INTERNAL', 'CONFIDENTIAL', 'RESTRICTED'] as const
export const HOSTING_ENVIRONMENTS = ['ON_PREMISES', 'AWS', 'AZURE', 'GCP', 'HYBRID'] as const

// ── Dashboard ─────────────────────────────────────────────

export interface DashboardSummary {
  totalApplications: number
  activeApplications: number
  criticalApplications: number
  highCriticalityApplications: number
  internetFacingApplications: number
  highCriticalInternetFacing: number
  criticalityDistribution: DistributionEntry[]
  lifecycleStatusDistribution: DistributionEntry[]
  hostingEnvironmentDistribution: DistributionEntry[]
  businessUnitDistribution: DistributionEntry[]
  recentApplications: RecentApplication[]
}

export interface DistributionEntry {
  key: string
  count: number
}

export interface RecentApplication {
  id: string
  name: string
  applicationKey: string
  criticality: string
  lifecycleStatus: string
  updatedAt: string
}
