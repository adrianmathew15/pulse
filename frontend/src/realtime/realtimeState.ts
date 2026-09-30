import type { Alert } from '../types/alert'
import type { Metric } from '../types/metric'

export function upsertMetric(current: Metric[], incoming: Metric, limit = 100): Metric[] {
  const byId = new Map(current.map((metric) => [metric.id, metric]))
  byId.set(incoming.id, incoming)
  return [...byId.values()]
    .sort((left, right) => left.timestamp.localeCompare(right.timestamp) || left.id.localeCompare(right.id))
    .slice(-limit)
}

export function upsertAlert(current: Alert[], incoming: Alert, limit = 100): Alert[] {
  const byId = new Map(current.map((alert) => [alert.id, alert]))
  byId.set(incoming.id, incoming)
  return [...byId.values()]
    .sort((left, right) => right.triggeredAt.localeCompare(left.triggeredAt) || right.id.localeCompare(left.id))
    .slice(0, limit)
}
