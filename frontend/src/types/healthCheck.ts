import type { ServiceStatus } from './service'

export interface HealthCheck {
  id: string
  serviceId: string
  status: ServiceStatus
  httpStatus: number | null
  responseTimeMs: number
  checkedAt: string
  failureReason: string | null
}
