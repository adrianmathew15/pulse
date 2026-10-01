import type { Incident } from '../types/incident'

interface Props {
  incidents: Incident[]
  now?: number
}

export function IncidentList({ incidents, now = Date.now() }: Props) {
  if (incidents.length === 0) {
    return <p className="rounded-xl border border-dashed border-slate-700 py-8 text-center text-slate-400">No incidents for this service.</p>
  }

  return <div className="space-y-3">
    {incidents.map((incident) => <IncidentCard key={incident.id} incident={incident} now={now} />)}
  </div>
}

function IncidentCard({ incident, now }: { incident: Incident; now: number }) {
  const active = incident.status === 'ACTIVE'
  const end = incident.resolvedAt ? Date.parse(incident.resolvedAt) : now
  const duration = formatIncidentDuration(Math.max(0, end - Date.parse(incident.startedAt)))

  return <article className={`rounded-xl border p-4 ${active ? 'border-red-800 bg-red-950/30' : 'border-slate-800 bg-slate-950/50'}`}>
    <div className="flex flex-wrap items-start justify-between gap-3">
      <div>
        <p className={`font-semibold ${active ? 'text-red-200' : 'text-slate-300'}`}>{incident.serviceName}</p>
        <p className="mt-1 text-sm text-slate-400">{incident.failureReason || 'Endpoint health check failed'}</p>
      </div>
      <span className={`rounded-full px-3 py-1 text-xs font-semibold ${active ? 'bg-red-900 text-red-200' : 'bg-slate-800 text-slate-300'}`}>{incident.status}</span>
    </div>
    <dl className="mt-3 grid gap-2 text-xs text-slate-500 sm:grid-cols-4">
      <div><dt>Started</dt><dd className="mt-1 text-slate-300">{new Date(incident.startedAt).toLocaleString()}</dd></div>
      <div><dt>Resolved</dt><dd className="mt-1 text-slate-300">{incident.resolvedAt ? new Date(incident.resolvedAt).toLocaleString() : '—'}</dd></div>
      <div><dt>Duration</dt><dd className="mt-1 text-slate-300">{duration}</dd></div>
      <div><dt>HTTP status</dt><dd className="mt-1 text-slate-300">{incident.httpStatus ?? 'Unavailable'}</dd></div>
    </dl>
  </article>
}

export function formatIncidentDuration(durationMs: number) {
  const totalSeconds = Math.floor(durationMs / 1_000)
  const hours = Math.floor(totalSeconds / 3_600)
  const minutes = Math.floor((totalSeconds % 3_600) / 60)
  const seconds = totalSeconds % 60
  if (hours > 0) return `${hours}h ${minutes}m ${seconds}s`
  if (minutes > 0) return `${minutes}m ${seconds}s`
  return `${seconds}s`
}
