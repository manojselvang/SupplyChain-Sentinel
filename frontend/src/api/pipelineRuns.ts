import apiClient from './client'
import type { PipelineRun, Page } from '../types'

export interface ListPipelineRunsParams {
  applicationId?: string
  provider?: string
  status?: string
  page?: number
  size?: number
  sort?: string
}

export async function listPipelineRuns(params: ListPipelineRunsParams): Promise<Page<PipelineRun>> {
  const response = await apiClient.get<Page<PipelineRun>>('/pipeline-runs', { params })
  return response.data
}

export async function getApplicationPipelineRuns(
  applicationId: string,
  page = 0,
  size = 10,
): Promise<Page<PipelineRun>> {
  const response = await apiClient.get<Page<PipelineRun>>(
    `/applications/${applicationId}/pipeline-runs`,
    { params: { page, size, sort: 'startedAt,desc' } },
  )
  return response.data
}
