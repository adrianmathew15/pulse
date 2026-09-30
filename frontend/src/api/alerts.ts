import { apiClient } from './client'
import type { Alert, AlertStatus } from '../types/alert'

export async function listAlerts(serviceId: string, status?: AlertStatus, limit = 100): Promise<Alert[]> {
  const response = await apiClient.get<Alert[]>(`/services/${serviceId}/alerts`, {
    params: { status, limit },
  })
  return response.data
}

export async function listActiveAlerts(limit = 500): Promise<Alert[]> {
  const response = await apiClient.get<Alert[]>('/alerts/active', { params: { limit } })
  return response.data
}
