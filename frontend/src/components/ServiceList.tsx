import type { MonitoredService } from '../types/service'

interface Props {
  services: MonitoredService[]
  deletingId: string | null
  onView: (id: string) => void
  onDelete: (service: MonitoredService) => void
}

export function ServiceList({ services, deletingId, onView, onDelete }: Props) {
  if (services.length === 0) {
    return (
      <div className="rounded-2xl border border-dashed border-slate-700 px-6 py-14 text-center text-slate-400">
        No services registered yet. Add the first one using the form.
      </div>
    )
  }

  return (
    <div className="overflow-x-auto rounded-2xl border border-slate-800 bg-slate-900/80 shadow-xl">
      <table className="w-full min-w-[760px] text-left text-sm">
        <thead className="border-b border-slate-800 bg-slate-900 text-xs uppercase tracking-wider text-slate-400">
          <tr>
            <th className="px-5 py-4">Service</th>
            <th className="px-5 py-4">Endpoint</th>
            <th className="px-5 py-4">Status</th>
            <th className="px-5 py-4">Created</th>
            <th className="px-5 py-4 text-right">Actions</th>
          </tr>
        </thead>
        <tbody className="divide-y divide-slate-800">
          {services.map((service) => (
            <tr key={service.id} className="transition hover:bg-slate-800/40">
              <td className="px-5 py-4">
                <button className="font-semibold text-slate-100 hover:text-cyan-300" onClick={() => onView(service.id)}>
                  {service.name}
                </button>
                <p className="mt-1 max-w-sm truncate text-slate-400">{service.description || 'No description'}</p>
              </td>
              <td className="px-5 py-4 text-slate-300">
                {service.endpoint ? <a className="hover:text-cyan-300" href={service.endpoint} target="_blank" rel="noreferrer">{service.endpoint}</a> : '—'}
              </td>
              <td className="px-5 py-4">
                <span className="rounded-full bg-emerald-950 px-3 py-1 text-xs font-semibold text-emerald-300">{service.status}</span>
              </td>
              <td className="px-5 py-4 text-slate-400">{new Date(service.createdAt).toLocaleString()}</td>
              <td className="px-5 py-4 text-right">
                <button className="mr-3 text-cyan-300 hover:text-cyan-200" onClick={() => onView(service.id)}>View</button>
                <button className="text-red-300 hover:text-red-200 disabled:opacity-50"
                  onClick={() => onDelete(service)} disabled={deletingId === service.id}>
                  {deletingId === service.id ? 'Deleting…' : 'Delete'}
                </button>
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  )
}
