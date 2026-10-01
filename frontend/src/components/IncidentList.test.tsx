import { renderToStaticMarkup } from 'react-dom/server'
import { describe, expect, it } from 'vitest'
import type { Incident } from '../types/incident'
import { IncidentList } from './IncidentList'

const active: Incident = {
  id: 'incident-1',
  serviceId: 'service-1',
  serviceName: 'Payments API',
  startedAt: '2026-01-01T00:00:00Z',
  resolvedAt: null,
  status: 'ACTIVE',
  failureReason: 'Connection refused',
  httpStatus: null,
}

describe('IncidentList', () => {
  it('renders an active incident with its ongoing duration and failure details', () => {
    const markup = renderToStaticMarkup(
      <IncidentList incidents={[active]} now={Date.parse('2026-01-01T00:01:05Z')} />,
    )

    expect(markup).toContain('Payments API')
    expect(markup).toContain('ACTIVE')
    expect(markup).toContain('Connection refused')
    expect(markup).toContain('1m 5s')
    expect(markup).toContain('Unavailable')
  })

  it('renders a resolved incident with resolved time, fixed duration, and HTTP status', () => {
    const resolved: Incident = {
      ...active,
      status: 'RESOLVED',
      resolvedAt: '2026-01-01T00:02:00Z',
      failureReason: 'HTTP 503',
      httpStatus: 503,
    }

    const markup = renderToStaticMarkup(<IncidentList incidents={[resolved]} />)

    expect(markup).toContain('RESOLVED')
    expect(markup).toContain('HTTP 503')
    expect(markup).toContain('2m 0s')
    expect(markup).toContain('503')
  })
})
