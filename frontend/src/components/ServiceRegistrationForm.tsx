import { useState, type FormEvent } from 'react'
import type { CreateServiceRequest } from '../types/service'

interface Props {
  onCreate: (request: CreateServiceRequest) => Promise<void>
}

export function ServiceRegistrationForm({ onCreate }: Props) {
  const [name, setName] = useState('')
  const [description, setDescription] = useState('')
  const [endpoint, setEndpoint] = useState('')
  const [cpuThreshold, setCpuThreshold] = useState('80')
  const [memoryThreshold, setMemoryThreshold] = useState('80')
  const [error, setError] = useState<string | null>(null)
  const [success, setSuccess] = useState<string | null>(null)
  const [submitting, setSubmitting] = useState(false)

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setError(null)
    setSuccess(null)

    if (!name.trim()) {
      setError('Service name is required.')
      return
    }
    if (endpoint.trim()) {
      try {
        const url = new URL(endpoint)
        if (!['http:', 'https:'].includes(url.protocol)) throw new Error()
      } catch {
        setError('Endpoint must be a valid HTTP or HTTPS URL.')
        return
      }
    }
    const cpu = Number(cpuThreshold)
    const memory = Number(memoryThreshold)
    if (!Number.isFinite(cpu) || cpu <= 0 || cpu > 100
      || !Number.isFinite(memory) || memory <= 0 || memory > 100) {
      setError('CPU and memory thresholds must be greater than 0 and at most 100.')
      return
    }

    setSubmitting(true)
    try {
      const createdName = name.trim()
      await onCreate({
        name: createdName,
        description: description.trim() || undefined,
        endpoint: endpoint.trim() || undefined,
        cpuWarningThreshold: cpu,
        memoryWarningThreshold: memory,
      })
      setName('')
      setDescription('')
      setEndpoint('')
      setCpuThreshold('80')
      setMemoryThreshold('80')
      setSuccess(`${createdName} was registered.`)
    } catch (caught) {
      setError(caught instanceof Error ? caught.message : 'Could not register the service.')
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <form onSubmit={submit} className="rounded-2xl border border-slate-800 bg-slate-900/80 p-6 shadow-xl">
      <div className="mb-6">
        <p className="text-xs font-semibold uppercase tracking-[0.25em] text-cyan-400">Registration</p>
        <h2 className="mt-2 text-2xl font-semibold">Add a monitored service</h2>
      </div>

      <label className="field-label" htmlFor="service-name">Service name</label>
      <input id="service-name" className="field-input" value={name} maxLength={120}
        onChange={(event) => setName(event.target.value)} required disabled={submitting} />

      <label className="field-label" htmlFor="service-description">Description</label>
      <textarea id="service-description" className="field-input min-h-24 resize-y" value={description}
        maxLength={1000} onChange={(event) => setDescription(event.target.value)} disabled={submitting} />

      <label className="field-label" htmlFor="service-endpoint">Endpoint</label>
      <input id="service-endpoint" className="field-input" value={endpoint} maxLength={2048}
        placeholder="https://api.example.com" type="url"
        onChange={(event) => setEndpoint(event.target.value)} disabled={submitting} />

      <div className="grid grid-cols-2 gap-4">
        <div>
          <label className="field-label" htmlFor="cpu-threshold">CPU warning %</label>
          <input id="cpu-threshold" className="field-input" type="number" min="0.01" max="100" step="0.01"
            value={cpuThreshold} onChange={(event) => setCpuThreshold(event.target.value)} required disabled={submitting} />
        </div>
        <div>
          <label className="field-label" htmlFor="memory-threshold">Memory warning %</label>
          <input id="memory-threshold" className="field-input" type="number" min="0.01" max="100" step="0.01"
            value={memoryThreshold} onChange={(event) => setMemoryThreshold(event.target.value)} required disabled={submitting} />
        </div>
      </div>

      {error && <p role="alert" className="mt-4 rounded-lg bg-red-950/70 px-4 py-3 text-sm text-red-200">{error}</p>}
      {success && <p role="status" className="mt-4 rounded-lg bg-emerald-950/70 px-4 py-3 text-sm text-emerald-200">{success}</p>}

      <button className="mt-6 w-full rounded-lg bg-cyan-400 px-4 py-3 font-semibold text-slate-950 transition hover:bg-cyan-300 disabled:cursor-not-allowed disabled:opacity-60"
        type="submit" disabled={submitting}>
        {submitting ? 'Registering...' : 'Register service'}
      </button>
    </form>
  )
}
