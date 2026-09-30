import { useCallback, useEffect, useMemo, useState, type FormEvent } from 'react'
import { CartesianGrid, Line, LineChart, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts'
import { listAlerts } from '../api/alerts'
import { getApiErrorMessage } from '../api/client'
import { listMetrics } from '../api/metrics'
import { updateThresholds } from '../api/services'
import { useRealtimeSubscription } from '../realtime/RealtimeProvider'
import { upsertAlert, upsertMetric } from '../realtime/realtimeState'
import type { Alert, AlertEvent } from '../types/alert'
import type { Metric } from '../types/metric'
import type { MonitoredService } from '../types/service'

interface Props {
  service: MonitoredService
  onClose: () => void
  onServiceUpdated: (service: MonitoredService) => void
  onAlertsChanged: () => void
}

export function ServiceDetails({ service, onClose, onServiceUpdated, onAlertsChanged }: Props) {
  const [metrics, setMetrics] = useState<Metric[]>([])
  const [alerts, setAlerts] = useState<Alert[]>([])
  const [loading, setLoading] = useState(true)
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [cpuThreshold, setCpuThreshold] = useState(String(service.cpuWarningThreshold))
  const [memoryThreshold, setMemoryThreshold] = useState(String(service.memoryWarningThreshold))

  const loadData = useCallback(async () => {
    setLoading(true)
    setError(null)
    try {
      const [metricHistory, alertHistory] = await Promise.all([
        listMetrics(service.id),
        listAlerts(service.id),
      ])
      setMetrics(metricHistory)
      setAlerts(alertHistory)
      onAlertsChanged()
    } catch (caught) {
      setError(getApiErrorMessage(caught))
    } finally {
      setLoading(false)
    }
  }, [service.id, onAlertsChanged])

  useEffect(() => { void loadData() }, [loadData])
  const connected = useRealtimeSubscription<Metric>(
    `/topic/services/${service.id}/metrics`,
    (metric) => setMetrics((current) => upsertMetric(current, metric)),
    () => { void loadData() },
  )
  useRealtimeSubscription<AlertEvent>(
    `/topic/services/${service.id}/alerts`,
    (event) => setAlerts((current) => upsertAlert(current, event.alert)),
  )
  useEffect(() => {
    setCpuThreshold(String(service.cpuWarningThreshold))
    setMemoryThreshold(String(service.memoryWarningThreshold))
  }, [service.cpuWarningThreshold, service.memoryWarningThreshold])

  async function saveThresholds(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const cpu = Number(cpuThreshold)
    const memory = Number(memoryThreshold)
    if (!Number.isFinite(cpu) || cpu <= 0 || cpu > 100
      || !Number.isFinite(memory) || memory <= 0 || memory > 100) {
      setError('CPU and memory thresholds must be greater than 0 and at most 100.')
      return
    }
    setSaving(true)
    setError(null)
    try {
      onServiceUpdated(await updateThresholds(service.id, {
        cpuWarningThreshold: cpu,
        memoryWarningThreshold: memory,
      }))
    } catch (caught) {
      setError(getApiErrorMessage(caught))
    } finally {
      setSaving(false)
    }
  }

  const latest = metrics.at(-1)
  const chartData = useMemo(() => metrics.map((metric) => ({
    ...metric,
    time: new Date(metric.timestamp).toLocaleTimeString(),
  })), [metrics])

  return (
    <aside className="mb-6 rounded-2xl border border-cyan-900 bg-cyan-950/20 p-6">
      <div className="flex items-start justify-between gap-4">
        <div>
          <div className="flex items-center gap-3"><p className="text-xs font-semibold uppercase tracking-[0.25em] text-cyan-400">Service details</p><span className={`text-xs ${connected ? 'text-emerald-400' : 'text-amber-400'}`}>{connected ? 'Live' : 'Reconnecting'}</span></div>
          <h3 className="mt-2 text-2xl font-semibold">{service.name}</h3>
        </div>
        <div className="flex items-center gap-4">
          <button onClick={() => void loadData()} disabled={loading}
            className="text-sm font-semibold text-cyan-300 hover:text-cyan-200 disabled:opacity-50">
            {loading ? 'Loading...' : 'Refresh data'}
          </button>
          <button onClick={onClose} aria-label="Close service details" className="text-2xl text-slate-400 hover:text-white">&times;</button>
        </div>
      </div>
      <dl className="mt-5 grid gap-4 text-sm sm:grid-cols-2">
        <div><dt className="text-slate-500">Description</dt><dd className="mt-1 text-slate-200">{service.description || 'No description'}</dd></div>
        <div><dt className="text-slate-500">Status</dt><dd className="mt-1 text-emerald-300">{service.status}</dd></div>
        <div><dt className="text-slate-500">Endpoint</dt><dd className="mt-1 break-all text-slate-200">{service.endpoint || 'Not provided'}</dd></div>
        <div><dt className="text-slate-500">Created</dt><dd className="mt-1 text-slate-200">{new Date(service.createdAt).toLocaleString()}</dd></div>
      </dl>

      <form onSubmit={saveThresholds} className="mt-6 grid items-end gap-4 rounded-xl border border-slate-800 bg-slate-950/40 p-4 sm:grid-cols-[1fr_1fr_auto]">
        <div><label className="mb-2 block text-sm text-slate-400" htmlFor="detail-cpu-threshold">CPU warning %</label><input id="detail-cpu-threshold" className="field-input" type="number" min="0.01" max="100" step="0.01" value={cpuThreshold} onChange={(event) => setCpuThreshold(event.target.value)} /></div>
        <div><label className="mb-2 block text-sm text-slate-400" htmlFor="detail-memory-threshold">Memory warning %</label><input id="detail-memory-threshold" className="field-input" type="number" min="0.01" max="100" step="0.01" value={memoryThreshold} onChange={(event) => setMemoryThreshold(event.target.value)} /></div>
        <button className="rounded-lg bg-slate-700 px-4 py-2.5 font-semibold hover:bg-slate-600 disabled:opacity-50" disabled={saving}>{saving ? 'Saving...' : 'Save thresholds'}</button>
      </form>

      {error && <p role="alert" className="mt-5 rounded-lg bg-red-950/70 px-4 py-3 text-red-200">{error}</p>}

      <section className="mt-7 border-t border-slate-800 pt-6">
        <div className="mb-5 flex items-end justify-between"><div><p className="text-xs font-semibold uppercase tracking-[0.25em] text-cyan-400">Telemetry</p><h4 className="mt-2 text-xl font-semibold">Current metrics</h4></div>{latest && <span className="text-xs text-slate-500">Updated {new Date(latest.timestamp).toLocaleString()}</span>}</div>
        {loading && metrics.length === 0 ? <p className="py-8 text-center text-slate-400">Loading metric history...</p>
          : metrics.length === 0 ? <p className="rounded-xl border border-dashed border-slate-700 py-8 text-center text-slate-400">No metrics yet. The simulator will create the first sample shortly.</p>
          : <>
            <div className="grid gap-4 sm:grid-cols-2"><MetricValue label="CPU usage" value={latest!.cpuUsage} color="text-cyan-300" /><MetricValue label="Memory usage" value={latest!.memoryUsage} color="text-violet-300" /></div>
            <div className="mt-6 grid gap-5 xl:grid-cols-2"><MetricChart title="CPU usage over time" data={chartData} dataKey="cpuUsage" color="#22d3ee" /><MetricChart title="Memory usage over time" data={chartData} dataKey="memoryUsage" color="#c4b5fd" /></div>
            <div className="mt-6 overflow-x-auto"><h4 className="mb-3 font-semibold">Recent values</h4><table className="w-full min-w-[560px] text-left text-sm"><thead className="text-xs uppercase tracking-wider text-slate-500"><tr><th className="py-2">Time</th><th>CPU</th><th>Memory</th></tr></thead><tbody className="divide-y divide-slate-800">{metrics.slice(-10).reverse().map((metric) => <tr key={metric.id}><td className="py-2 text-slate-400">{new Date(metric.timestamp).toLocaleString()}</td><td className="text-cyan-300">{metric.cpuUsage.toFixed(2)}%</td><td className="text-violet-300">{metric.memoryUsage.toFixed(2)}%</td></tr>)}</tbody></table></div>
          </>}
      </section>

      <section className="mt-7 border-t border-slate-800 pt-6">
        <div className="mb-4 flex items-end justify-between"><div><p className="text-xs font-semibold uppercase tracking-[0.25em] text-amber-400">Alerts</p><h4 className="mt-2 text-xl font-semibold">Alert history</h4></div><span className="text-sm text-slate-400">{alerts.filter((alert) => alert.status === 'ACTIVE').length} active</span></div>
        {alerts.length === 0 ? <p className="rounded-xl border border-dashed border-slate-700 py-8 text-center text-slate-400">No alert episodes for this service.</p>
          : <div className="space-y-3">{alerts.map((alert) => <AlertCard key={alert.id} alert={alert} />)}</div>}
      </section>
    </aside>
  )
}

function AlertCard({ alert }: { alert: Alert }) {
  const active = alert.status === 'ACTIVE'
  return <article className={`rounded-xl border p-4 ${active ? 'border-amber-700 bg-amber-950/30' : 'border-slate-800 bg-slate-950/50'}`}>
    <div className="flex flex-wrap items-start justify-between gap-3"><div><p className={`font-semibold ${active ? 'text-amber-200' : 'text-slate-300'}`}>{alert.metricType} threshold exceeded</p><p className="mt-1 text-sm text-slate-400">{alert.serviceName} · {alert.triggeredValue.toFixed(2)}% / threshold {alert.threshold.toFixed(2)}%</p></div><span className={`rounded-full px-3 py-1 text-xs font-semibold ${active ? 'bg-amber-900 text-amber-200' : 'bg-slate-800 text-slate-300'}`}>{alert.status}</span></div>
    <dl className="mt-3 grid gap-2 text-xs text-slate-500 sm:grid-cols-3"><div><dt>Severity</dt><dd className="mt-1 text-slate-300">{alert.severity}</dd></div><div><dt>Triggered</dt><dd className="mt-1 text-slate-300">{new Date(alert.triggeredAt).toLocaleString()}</dd></div><div><dt>Resolved</dt><dd className="mt-1 text-slate-300">{alert.resolvedAt ? new Date(alert.resolvedAt).toLocaleString() : '—'}</dd></div></dl>
  </article>
}

function MetricValue({ label, value, color }: { label: string; value: number; color: string }) {
  return <div className="rounded-xl border border-slate-800 bg-slate-950/60 p-5"><p className="text-sm text-slate-400">{label}</p><p className={`mt-2 text-3xl font-bold ${color}`}>{value.toFixed(2)}%</p></div>
}

function MetricChart({ title, data, dataKey, color }: { title: string; data: Array<Metric & { time: string }>; dataKey: 'cpuUsage' | 'memoryUsage'; color: string }) {
  return <div className="rounded-xl border border-slate-800 bg-slate-950/60 p-4"><h4 className="mb-4 font-semibold">{title}</h4><div className="h-56"><ResponsiveContainer width="100%" height="100%"><LineChart data={data} margin={{ top: 5, right: 10, left: -20, bottom: 5 }}><CartesianGrid stroke="#1e293b" strokeDasharray="3 3" /><XAxis dataKey="time" stroke="#64748b" minTickGap={28} /><YAxis domain={[0, 100]} stroke="#64748b" unit="%" /><Tooltip contentStyle={{ background: '#020617', borderColor: '#334155' }} /><Line type="monotone" dataKey={dataKey} stroke={color} strokeWidth={2} dot={false} isAnimationActive={false} /></LineChart></ResponsiveContainer></div></div>
}
