import apiClient from './client'
import type { SbomDocument, SbomComponent, Page } from '../types'

export interface ListSbomsParams {
  applicationId?: string
  artifactDigest?: string
  page?: number
  size?: number
  sort?: string
}

export interface ListComponentsParams {
  name?: string
  purl?: string
  page?: number
  size?: number
  sort?: string
}

export async function listSboms(params: ListSbomsParams): Promise<Page<SbomDocument>> {
  const response = await apiClient.get<Page<SbomDocument>>('/sboms', { params })
  return response.data
}

export async function getSbom(id: string): Promise<SbomDocument> {
  const response = await apiClient.get<SbomDocument>(`/sboms/${id}`)
  return response.data
}

export async function getSbomComponents(
  id: string,
  params: ListComponentsParams,
): Promise<Page<SbomComponent>> {
  const response = await apiClient.get<Page<SbomComponent>>(`/sboms/${id}/components`, { params })
  return response.data
}

export async function getApplicationSboms(
  applicationId: string,
  page = 0,
  size = 10,
): Promise<Page<SbomDocument>> {
  const response = await apiClient.get<Page<SbomDocument>>(
    `/applications/${applicationId}/sboms`,
    { params: { page, size, sort: 'createdAt,desc' } },
  )
  return response.data
}
