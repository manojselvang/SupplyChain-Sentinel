import apiClient from './client'
import type { ReleaseAssessment, ReleaseApproval, Page } from '../types'

export interface ListAssessmentsParams {
  applicationId?: string
  decision?: string
  status?: string
  page?: number
  size?: number
  sort?: string
}

export async function listAssessments(params: ListAssessmentsParams): Promise<Page<ReleaseAssessment>> {
  const response = await apiClient.get<Page<ReleaseAssessment>>('/release-assessments', { params })
  return response.data
}

export async function getAssessment(id: string): Promise<ReleaseAssessment> {
  const response = await apiClient.get<ReleaseAssessment>(`/release-assessments/${id}`)
  return response.data
}

export async function createAssessment(applicationId: string, pipelineRunId: string): Promise<ReleaseAssessment> {
  const response = await apiClient.post<ReleaseAssessment>('/release-assessments', { applicationId, pipelineRunId })
  return response.data
}

export async function submitApproval(
  assessmentId: string,
  decision: 'APPROVED' | 'REJECTED',
  justification?: string,
): Promise<ReleaseApproval> {
  const response = await apiClient.post<ReleaseApproval>(
    `/release-assessments/${assessmentId}/approvals`,
    { decision, justification },
  )
  return response.data
}

export async function getApplicationAssessments(
  applicationId: string,
  page = 0,
  size = 10,
): Promise<Page<ReleaseAssessment>> {
  const response = await apiClient.get<Page<ReleaseAssessment>>(
    `/applications/${applicationId}/release-assessments`,
    { params: { page, size, sort: 'assessedAt,desc' } },
  )
  return response.data
}
