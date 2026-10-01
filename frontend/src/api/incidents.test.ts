import { afterEach, describe, expect, it } from 'vitest'
import { apiClient } from './client'
import { listIncidents } from './incidents'
import type { Incident } from '../types/incident'

const originalAdapter = apiClient.defaults.adapter

afterEach(() => {
  apiClient.defaults.adapter = originalAdapter
})

describe('incident REST recovery', () => {
  it('restores service incident history from the REST endpoint', async () => {
    const incident: Incident = {
      id: 'incident-1', serviceId: 'service-1', serviceName: 'Payments API',
      startedAt: '2026-01-01T00:00:00Z', resolvedAt: null, status: 'ACTIVE',
      failureReason: 'HTTP 503', httpStatus: 503,
    }
    apiClient.defaults.adapter = async (config) => {
      expect(config.url).toBe('/services/service-1/incidents')
      expect(config.params).toEqual({ status: undefined, limit: 100 })
      return { data: [incident], status: 200, statusText: 'OK', headers: {}, config }
    }

    await expect(listIncidents('service-1')).resolves.toEqual([incident])
  })
})
