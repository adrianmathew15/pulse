import { describe, expect, it } from 'vitest'
import type { Alert } from '../types/alert'
import type { Metric } from '../types/metric'
import type { HealthCheck } from '../types/healthCheck'
import type { Incident } from '../types/incident'
import { upsertAlert, upsertHealthCheck, upsertIncident, upsertMetric } from './realtimeState'

const metric = (id: string, timestamp: string, cpuUsage = 20): Metric => ({
  id,
  serviceId: 'service-1',
  cpuUsage,
  memoryUsage: 30,
  timestamp,
})

const alert = (status: Alert['status']): Alert => ({
  id: 'alert-1',
  serviceId: 'service-1',
  serviceName: 'Payments',
  type: 'CPU_THRESHOLD',
  message: 'CPU threshold exceeded',
  severity: 'WARNING',
  metricType: 'CPU',
  threshold: 80,
  triggeredValue: 90,
  triggeredAt: '2026-01-01T00:00:00Z',
  resolvedAt: status === 'RESOLVED' ? '2026-01-01T00:01:00Z' : null,
  status,
})

describe('real-time state updates', () => {
  it('replaces duplicate metric events and preserves chronological order', () => {
    const initial = [
      metric('metric-1', '2026-01-01T00:00:00Z'),
      metric('metric-2', '2026-01-01T00:00:01Z'),
    ]

    const updated = upsertMetric(initial, metric('metric-1', '2026-01-01T00:00:00Z', 75))

    expect(updated).toHaveLength(2)
    expect(updated.map((entry) => entry.id)).toEqual(['metric-1', 'metric-2'])
    expect(updated[0].cpuUsage).toBe(75)
  })

  it('replaces a created alert with its resolved event instead of duplicating it', () => {
    const updated = upsertAlert([alert('ACTIVE')], alert('RESOLVED'))

    expect(updated).toHaveLength(1)
    expect(updated[0].status).toBe('RESOLVED')
    expect(updated[0].resolvedAt).toBe('2026-01-01T00:01:00Z')
  })

  it('replaces duplicate health-check events and preserves chronological order', () => {
    const first: HealthCheck = {
      id: 'check-1', serviceId: 'service-1', status: 'DOWN', httpStatus: 503,
      responseTimeMs: 50, checkedAt: '2026-01-01T00:00:00Z', failureReason: 'HTTP 503',
    }
    const second: HealthCheck = {
      id: 'check-2', serviceId: 'service-1', status: 'UP', httpStatus: 200,
      responseTimeMs: 20, checkedAt: '2026-01-01T00:00:01Z', failureReason: null,
    }

    const updated = upsertHealthCheck([first, second], { ...first, responseTimeMs: 75 })

    expect(updated).toHaveLength(2)
    expect(updated.map((entry) => entry.id)).toEqual(['check-1', 'check-2'])
    expect(updated[0].responseTimeMs).toBe(75)
  })

  it('adds an incident creation event without duplicating repeated delivery', () => {
    const created: Incident = {
      id: 'incident-1', serviceId: 'service-1', serviceName: 'Payments',
      startedAt: '2026-01-01T00:00:00Z', resolvedAt: null, status: 'ACTIVE',
      failureReason: 'HTTP 503', httpStatus: 503,
    }

    const updated = upsertIncident(upsertIncident([], created), created)

    expect(updated).toHaveLength(1)
    expect(updated[0].status).toBe('ACTIVE')
  })

  it('replaces an active incident with its resolution event', () => {
    const active: Incident = {
      id: 'incident-1', serviceId: 'service-1', serviceName: 'Payments',
      startedAt: '2026-01-01T00:00:00Z', resolvedAt: null, status: 'ACTIVE',
      failureReason: 'HTTP 503', httpStatus: 503,
    }
    const resolved: Incident = {
      ...active, status: 'RESOLVED', resolvedAt: '2026-01-01T00:01:00Z',
    }

    const updated = upsertIncident([active], resolved)

    expect(updated).toHaveLength(1)
    expect(updated[0].status).toBe('RESOLVED')
    expect(updated[0].resolvedAt).toBe('2026-01-01T00:01:00Z')
  })
})
