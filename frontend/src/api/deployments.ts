import apiClient from './client'
import type { DeploymentRecord, Page } from '../types'

export interface ListDeploymentsParams {
  applicationId?: string
  syncStatus?: string
  healthStatus?: string
  environment?: string
  page?: number
  size?: number
  sort?: string
}

export async function listDeployments(params: ListDeploymentsParams): Promise<Page<DeploymentRecord>> {
  const response = await apiClient.get<Page<DeploymentRecord>>('/deployments', { params })
  return response.data
}

export async function getApplicationDeployments(
  applicationId: string,
  page = 0,
  size = 10,
): Promise<Page<DeploymentRecord>> {
  const response = await apiClient.get<Page<DeploymentRecord>>(
    `/applications/${applicationId}/deployments`,
    { params: { page, size, sort: 'deployedAt,desc' } },
  )
  return response.data
}
