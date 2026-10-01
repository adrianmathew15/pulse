import { apiClient } from './client'
import type { Incident, IncidentStatus } from '../types/incident'

export async function listIncidents(
  serviceId: string,
  status?: IncidentStatus,
  limit = 100,
): Promise<Incident[]> {
  const response = await apiClient.get<Incident[]>(`/services/${serviceId}/incidents`, {
    params: { status, limit },
  })
  return response.data
}

export async function listActiveIncidents(limit = 100): Promise<Incident[]> {
  const response = await apiClient.get<Incident[]>('/incidents/active', { params: { limit } })
  return response.data
}
