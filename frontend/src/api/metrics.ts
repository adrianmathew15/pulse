import { apiClient } from './client'
import type { Metric } from '../types/metric'

export async function listMetrics(serviceId: string, limit = 100): Promise<Metric[]> {
  const response = await apiClient.get<Metric[]>(`/services/${serviceId}/metrics`, {
    params: { limit },
  })
  return response.data
}
