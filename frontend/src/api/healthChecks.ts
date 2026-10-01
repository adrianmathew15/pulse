import { apiClient } from './client'
import type { HealthCheck } from '../types/healthCheck'

export async function listHealthChecks(serviceId: string, limit = 100): Promise<HealthCheck[]> {
  const response = await apiClient.get<HealthCheck[]>(`/services/${serviceId}/health-checks`, {
    params: { limit },
  })
  return response.data
}
