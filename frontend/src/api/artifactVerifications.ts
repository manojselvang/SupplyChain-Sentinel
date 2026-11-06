import apiClient from './client'
import type { ArtifactVerification, Page } from '../types'

export interface ListVerificationsParams {
  applicationId?: string
  pipelineRunId?: string
  status?: string
  artifactDigest?: string
  page?: number
  size?: number
  sort?: string
}

export async function listArtifactVerifications(
  params: ListVerificationsParams,
): Promise<Page<ArtifactVerification>> {
  const response = await apiClient.get<Page<ArtifactVerification>>(
    '/artifact-verifications',
    { params },
  )
  return response.data
}

export async function getApplicationArtifactVerifications(
  applicationId: string,
  page = 0,
  size = 10,
): Promise<Page<ArtifactVerification>> {
  const response = await apiClient.get<Page<ArtifactVerification>>(
    `/applications/${applicationId}/artifact-verifications`,
    { params: { page, size, sort: 'createdAt,desc' } },
  )
  return response.data
}
