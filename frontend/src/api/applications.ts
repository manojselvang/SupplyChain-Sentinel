import apiClient from './client'
import type {
  Application,
  CreateApplicationRequest,
  UpdateApplicationRequest,
  Page,
} from '../types'

export interface ListApplicationsParams {
  search?: string
  criticality?: string
  lifecycleStatus?: string
  page?: number
  size?: number
  sort?: string
}

export async function listApplications(params: ListApplicationsParams): Promise<Page<Application>> {
  const response = await apiClient.get<Page<Application>>('/applications', { params })
  return response.data
}

export async function getApplication(id: string): Promise<Application> {
  const response = await apiClient.get<Application>(`/applications/${id}`)
  return response.data
}

export async function createApplication(data: CreateApplicationRequest): Promise<Application> {
  const response = await apiClient.post<Application>('/applications', data)
  return response.data
}

export async function updateApplication(id: string, data: UpdateApplicationRequest): Promise<Application> {
  const response = await apiClient.put<Application>(`/applications/${id}`, data)
  return response.data
}

export async function deleteApplication(id: string): Promise<void> {
  await apiClient.delete(`/applications/${id}`)
}
