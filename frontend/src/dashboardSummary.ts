import type { MonitoredService } from './types/service'

export function countHealthyServices(
  services: MonitoredService[],
  activeAlertServiceIds: ReadonlySet<string>,
) {
  return services.filter((service) => service.status === 'UP'
    && !activeAlertServiceIds.has(service.id)).length
}
