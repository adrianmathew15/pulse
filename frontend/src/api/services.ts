import { apiClient } from './client'
import type { CreateServiceRequest, MonitoredService, UpdateThresholdsRequest } from '../types/service'

export async function listServices(): Promise<MonitoredService[]> {
  const response = await apiClient.get<MonitoredService[]>('/services')
  return response.data
}

export async function getService(id: string): Promise<MonitoredService> {
  const response = await apiClient.get<MonitoredService>(`/services/${id}`)
  return response.data
}

export async function createService(request: CreateServiceRequest): Promise<MonitoredService> {
  const response = await apiClient.post<MonitoredService>('/services', request)
  return response.data
}

export async function deleteService(id: string): Promise<void> {
  await apiClient.delete(`/services/${id}`)
}

export async function updateThresholds(id: string, request: UpdateThresholdsRequest): Promise<MonitoredService> {
  const response = await apiClient.patch<MonitoredService>(`/services/${id}/thresholds`, request)
  return response.data
}
