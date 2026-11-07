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
  totalPipelineRuns: number
  successfulPipelineRuns: number
  failedPipelineRuns: number
  pipelineSuccessRate: number
  recentPipelineRuns: RecentPipelineRun[]
  openFindings: number
  criticalOpenFindings: number
  highOpenFindings: number
  openFindingsBySeverity: DistributionEntry[]
  recentFindings: RecentFinding[]
  totalSbomDocuments: number
  totalSbomComponents: number
  verifiedArtifacts: number
  failedArtifactVerifications: number
  recentSbomDocuments: RecentSbom[]
  recentArtifactVerifications: RecentArtifactVerification[]
  totalReleaseAssessments: number
  passAssessments: number
  warnAssessments: number
  blockAssessments: number
  approvedReleases: number
  pendingApprovals: number
  recentReleaseAssessments: RecentReleaseAssessment[]
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

// ── Pipeline Runs ──────────────────────────────────────────

export interface PipelineRun {
  id: string
  applicationId: string
  applicationName: string
  provider: string
  externalRunId: string
  workflowName: string | null
  repositoryUrl: string
  branchName: string | null
  commitSha: string | null
  commitMessage: string | null
  runNumber: string | null
  status: string
  triggerEvent: string | null
  startedAt: string | null
  completedAt: string | null
  artifactName: string | null
  artifactVersion: string | null
  artifactDigest: string | null
  createdAt: string
  updatedAt: string
}

export interface RecentPipelineRun {
  id: string
  applicationName: string
  provider: string
  workflowName: string | null
  status: string
  branchName: string | null
  startedAt: string
  completedAt: string | null
}

export const PIPELINE_PROVIDERS = ['JENKINS', 'GITHUB_ACTIONS'] as const
export const PIPELINE_STATUSES = ['QUEUED', 'RUNNING', 'SUCCESS', 'FAILED', 'CANCELLED'] as const

// ── Security Findings ──────────────────────────────────────

export interface SecurityFinding {
  id: string
  applicationId: string
  applicationName: string
  pipelineRunId: string | null
  scanner: string
  vulnerabilityId: string | null
  ruleId: string | null
  title: string
  description: string | null
  severity: string
  affectedPackage: string | null
  installedVersion: string | null
  fixedVersion: string | null
  affectedLocation: string | null
  remediationUrl: string | null
  status: string
  firstSeen: string
  lastSeen: string
  updatedAt: string
}

export interface RecentFinding {
  id: string
  applicationName: string
  title: string
  vulnerabilityId: string | null
  severity: string
  scanner: string
  status: string
  lastSeen: string
}

export const FINDING_SEVERITIES = ['CRITICAL', 'HIGH', 'MEDIUM', 'LOW', 'INFO', 'UNKNOWN'] as const
export const FINDING_STATUSES = ['OPEN', 'ACKNOWLEDGED', 'REMEDIATED', 'ACCEPTED'] as const

// ── SBOM Management ────────────────────────────────────────

export interface SbomDocument {
  id: string
  applicationId: string
  applicationName: string
  pipelineRunId: string | null
  artifactDigest: string | null
  format: string
  specVersion: string | null
  serialNumber: string | null
  documentVersion: number | null
  documentSha256: string
  componentCount: number
  generatedAt: string | null
  ingestedAt: string
  createdAt: string
}

export interface SbomComponent {
  id: string
  bomRef: string | null
  componentType: string | null
  groupName: string | null
  name: string
  version: string | null
  purl: string | null
  cpe: string | null
  scope: string | null
  supplier: string | null
  licenses: string | null
  hashes: string | null
  createdAt: string
}

export interface RecentSbom {
  id: string
  applicationName: string
  specVersion: string | null
  componentCount: number
  artifactDigest: string | null
  ingestedAt: string
}

// ── Artifact Integrity ─────────────────────────────────────

export interface ArtifactVerification {
  id: string
  applicationId: string
  applicationName: string
  pipelineRunId: string | null
  artifactReference: string
  artifactDigest: string | null
  verificationTool: string
  verificationMode: string
  verificationStatus: string
  signerIdentity: string | null
  oidcIssuer: string | null
  publicKeyFingerprint: string | null
  cosignVersion: string | null
  verifiedAt: string | null
  reportedAt: string
  createdAt: string
}

export interface RecentArtifactVerification {
  id: string
  applicationName: string
  artifactReference: string
  verificationStatus: string
  verificationMode: string
  reportedAt: string
}

export const VERIFICATION_STATUSES = ['VERIFIED', 'FAILED', 'MISSING_SIGNATURE', 'ERROR'] as const
export const VERIFICATION_MODES = ['KEY', 'KEYLESS'] as const

// ── Release Assurance ──────────────────────────────────────

export interface ReleaseAssessment {
  id: string
  applicationId: string
  applicationName: string
  pipelineRunId: string
  artifactDigest: string
  riskScore: number
  decision: string
  policyVersion: string
  assessmentStatus: string
  hardBlockReasons: string | null
  scoreBreakdown: string | null
  evidenceSnapshot: string | null
  requestedBy: string | null
  assessedAt: string
  createdAt: string
  approvals: ReleaseApproval[]
  auditHistory: AuditEvent[]
}

export interface ReleaseApproval {
  id: string
  approverId: string | null
  decision: string
  justification: string | null
  approvedAt: string
  createdAt: string
}

export interface AuditEvent {
  id: string
  actorId: string | null
  actorRole: string | null
  action: string
  entityType: string | null
  entityId: string | null
  eventDetails: string | null
  occurredAt: string
}

export interface RecentReleaseAssessment {
  id: string
  applicationName: string
  decision: string
  riskScore: number
  assessmentStatus: string
  artifactDigest: string | null
  assessedAt: string
}

export const ASSESSMENT_DECISIONS = ['PASS', 'WARN', 'BLOCK'] as const
export const ASSESSMENT_STATUSES = ['PENDING_APPROVAL', 'READY_FOR_DEPLOYMENT', 'REJECTED', 'BLOCKED'] as const
