import { describe, expect, it } from 'vitest'
import type { MonitoredService, ServiceStatus } from './types/service'
import { countHealthyServices } from './dashboardSummary'

function service(id: string, status: ServiceStatus): MonitoredService {
  return {
    id,
    name: id,
    description: null,
    endpoint: null,
    status,
    createdAt: '2026-01-01T00:00:00Z',
    cpuWarningThreshold: 80,
    memoryWarningThreshold: 80,
  }
}

describe('dashboard summary', () => {
  it('does not count UNKNOWN or DOWN services as healthy', () => {
    const services = [service('unknown', 'UNKNOWN'), service('down', 'DOWN'), service('up', 'UP')]

    expect(countHealthyServices(services, new Set())).toBe(1)
  })
})
