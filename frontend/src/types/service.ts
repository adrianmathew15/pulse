export type ServiceStatus = 'UP' | 'DOWN'

export interface MonitoredService {
  id: string
  name: string
  description: string | null
  endpoint: string | null
  status: ServiceStatus
  createdAt: string
  cpuWarningThreshold: number
  memoryWarningThreshold: number
}

export interface CreateServiceRequest {
  name: string
  description?: string
  endpoint?: string
  cpuWarningThreshold?: number
  memoryWarningThreshold?: number
}

export interface UpdateThresholdsRequest {
  cpuWarningThreshold: number
  memoryWarningThreshold: number
}

export interface ApiErrorResponse {
  timestamp: string
  status: number
  error: string
  message: string
  path: string
  fieldErrors?: Record<string, string>
}
