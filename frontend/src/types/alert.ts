export type AlertStatus = 'ACTIVE' | 'RESOLVED'
export type AlertMetricType = 'CPU' | 'MEMORY'
export type AlertType = 'CPU_THRESHOLD' | 'MEMORY_THRESHOLD'

export interface Alert {
  id: string
  serviceId: string
  serviceName: string
  type: AlertType
  message: string
  severity: 'WARNING'
  metricType: AlertMetricType
  threshold: number
  triggeredValue: number
  triggeredAt: string
  resolvedAt: string | null
  status: AlertStatus
}

export interface AlertEvent {
  eventType: 'CREATED' | 'RESOLVED'
  alert: Alert
}
