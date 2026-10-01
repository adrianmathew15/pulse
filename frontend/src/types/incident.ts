export type IncidentStatus = 'ACTIVE' | 'RESOLVED'

export interface Incident {
  id: string
  serviceId: string
  serviceName: string
  startedAt: string
  resolvedAt: string | null
  status: IncidentStatus
  failureReason: string | null
  httpStatus: number | null
}

export type IncidentEventType = 'INCIDENT_CREATED' | 'INCIDENT_RESOLVED'

export interface IncidentEvent {
  eventType: IncidentEventType
  incident: Incident
}
