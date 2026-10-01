import type { ReactNode } from 'react'
import type { ServiceStatus } from '../types/service'

interface Props {
  status: ServiceStatus
  children: ReactNode
}

export function SimulatedTelemetryGate({ status, children }: Props) {
  return status === 'UP' ? children : null
}
