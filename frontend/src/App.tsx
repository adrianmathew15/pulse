import { useCallback, useEffect, useState } from 'react'
import { listActiveAlerts } from './api/alerts'
import { createService, deleteService, getService, listServices } from './api/services'
import { getApiErrorMessage } from './api/client'
import { ServiceDetails } from './components/ServiceDetails'
import { ServiceList } from './components/ServiceList'
import { ServiceRegistrationForm } from './components/ServiceRegistrationForm'
import { useRealtimeSubscription } from './realtime/RealtimeProvider'
import type { Alert, AlertEvent } from './types/alert'
import type { CreateServiceRequest, MonitoredService } from './types/service'

function App() {
  const [services, setServices] = useState<MonitoredService[]>([])
  const [selected, setSelected] = useState<MonitoredService | null>(null)
  const [loading, setLoading] = useState(true)
  const [deletingId, setDeletingId] = useState<string | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [activeAlerts, setActiveAlerts] = useState<Alert[]>([])

  const loadServices = useCallback(async () => {
    setError(null)
    try {
      const [registeredServices, activeAlerts] = await Promise.all([listServices(), listActiveAlerts()])
      setServices(registeredServices)
      setActiveAlerts(activeAlerts)
    } catch (caught) {
      setError(getApiErrorMessage(caught))
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => { void loadServices() }, [loadServices])

  useRealtimeSubscription<AlertEvent>('/topic/alerts', (event) => {
    setActiveAlerts((current) => {
      const byId = new Map(current.map((alert) => [alert.id, alert]))
      if (event.eventType === 'CREATED' && event.alert.status === 'ACTIVE') {
        byId.set(event.alert.id, event.alert)
      } else {
        byId.delete(event.alert.id)
      }
      return [...byId.values()]
    })
  }, () => { void loadServices() })

  const activeAlertServiceIds = new Set(activeAlerts.map((alert) => alert.serviceId))

  async function register(request: CreateServiceRequest) {
    try {
      await createService(request)
      await loadServices()
    } catch (caught) {
      throw new Error(getApiErrorMessage(caught))
    }
  }

  async function view(id: string) {
    setError(null)
    try {
      setSelected(await getService(id))
    } catch (caught) {
      setError(getApiErrorMessage(caught))
    }
  }

  async function remove(service: MonitoredService) {
    if (!window.confirm(`Delete ${service.name}? This cannot be undone.`)) return
    setDeletingId(service.id)
    setError(null)
    try {
      await deleteService(service.id)
      if (selected?.id === service.id) setSelected(null)
      await loadServices()
    } catch (caught) {
      setError(getApiErrorMessage(caught))
    } finally {
      setDeletingId(null)
    }
  }

  function serviceUpdated(updated: MonitoredService) {
    setSelected(updated)
    setServices((current) => current.map((service) => service.id === updated.id ? updated : service))
  }

  return (
    <main className="min-h-screen bg-slate-950 px-4 py-10 text-slate-100 sm:px-6 lg:px-8">
      <div className="mx-auto max-w-7xl">
        <header className="mb-10">
          <p className="text-sm font-semibold uppercase tracking-[0.3em] text-cyan-400">Infrastructure monitoring</p>
          <h1 className="mt-3 text-5xl font-bold tracking-tight">Pulse</h1>
          <p className="mt-3 max-w-2xl text-slate-400">Register and manage the services that Pulse will monitor.</p>
        </header>

        <section className="mb-8 grid gap-4 sm:grid-cols-3">
          <SummaryCard label="Total services" value={services.length} tone="text-cyan-300" />
          <SummaryCard label="Active alerts" value={activeAlerts.length} tone="text-amber-300" />
          <SummaryCard label="Healthy services" value={services.filter((service) => service.status === 'UP' && !activeAlertServiceIds.has(service.id)).length} tone="text-emerald-300" />
        </section>

        <div className="grid gap-8 lg:grid-cols-[360px_1fr]">
          <ServiceRegistrationForm onCreate={register} />
          <section>
            <div className="mb-5 flex items-end justify-between">
              <div><p className="text-xs font-semibold uppercase tracking-[0.25em] text-cyan-400">Inventory</p><h2 className="mt-2 text-2xl font-semibold">Registered services</h2></div>
              <span className="text-sm text-slate-400">{services.length} total</span>
            </div>
            {error && <p role="alert" className="mb-5 rounded-lg bg-red-950/70 px-4 py-3 text-red-200">{error}</p>}
            {selected && <ServiceDetails service={selected} onClose={() => setSelected(null)}
              onServiceUpdated={serviceUpdated} onAlertsChanged={loadServices} />}
            {loading ? <p className="py-12 text-center text-slate-400">Loading services...</p>
              : <ServiceList services={services} deletingId={deletingId} onView={(id) => void view(id)} onDelete={(service) => void remove(service)} />}
          </section>
        </div>
      </div>
    </main>
  )
}

function SummaryCard({ label, value, tone }: { label: string; value: number; tone: string }) {
  return <div className="rounded-2xl border border-slate-800 bg-slate-900/80 p-5 shadow-lg"><p className="text-sm text-slate-400">{label}</p><p className={`mt-2 text-3xl font-bold ${tone}`}>{value}</p></div>
}

export default App
