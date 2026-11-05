import apiClient from './client'
import type { SecurityFinding, Page } from '../types'

export interface ListFindingsParams {
  applicationId?: string
  severity?: string
  status?: string
  scanner?: string
  vulnerabilityId?: string
  search?: string
  page?: number
  size?: number
  sort?: string
}

export async function listSecurityFindings(params: ListFindingsParams): Promise<Page<SecurityFinding>> {
  const response = await apiClient.get<Page<SecurityFinding>>('/security-findings', { params })
  return response.data
}

export async function getSecurityFinding(id: string): Promise<SecurityFinding> {
  const response = await apiClient.get<SecurityFinding>(`/security-findings/${id}`)
  return response.data
}

export async function updateFindingStatus(id: string, status: string): Promise<SecurityFinding> {
  const response = await apiClient.patch<SecurityFinding>(`/security-findings/${id}/status`, { status })
  return response.data
}

export async function getApplicationSecurityFindings(
  applicationId: string,
  page = 0,
  size = 10,
): Promise<Page<SecurityFinding>> {
  const response = await apiClient.get<Page<SecurityFinding>>(
    `/applications/${applicationId}/security-findings`,
    { params: { page, size, sort: 'lastSeen,desc' } },
  )
  return response.data
}
