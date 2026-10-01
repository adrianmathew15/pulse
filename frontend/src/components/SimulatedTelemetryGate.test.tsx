import { renderToStaticMarkup } from 'react-dom/server'
import { describe, expect, it } from 'vitest'
import type { ServiceStatus } from '../types/service'
import { SimulatedTelemetryGate } from './SimulatedTelemetryGate'

function renderTelemetry(status: ServiceStatus) {
  return renderToStaticMarkup(
    <SimulatedTelemetryGate status={status}>
      <section data-testid="simulated-telemetry">CPU and memory telemetry</section>
    </SimulatedTelemetryGate>,
  )
}

describe('simulated telemetry visibility', () => {
  it('shows CPU and memory telemetry while the service is UP', () => {
    expect(renderTelemetry('UP')).toContain('data-testid="simulated-telemetry"')
  })

  it('hides CPU and memory telemetry while the service is DOWN', () => {
    expect(renderTelemetry('DOWN')).toBe('')
  })

  it('hides CPU and memory telemetry while the service is UNKNOWN', () => {
    expect(renderTelemetry('UNKNOWN')).toBe('')
  })

  it('updates telemetry across UNKNOWN to UP to DOWN to UP transitions', () => {
    const visibility = (['UNKNOWN', 'UP', 'DOWN', 'UP'] as ServiceStatus[])
      .map((status) => renderTelemetry(status).includes('simulated-telemetry'))

    expect(visibility).toEqual([false, true, false, true])
  })
})
