import { useState, type FormEvent } from 'react'
import { login } from '../api/auth'
import { getApiErrorMessage } from '../api/client'
import type { AuthSession } from '../auth/session'

export function LoginPage({ onAuthenticated }: { onAuthenticated: (session: AuthSession) => void }) {
  const [username, setUsername] = useState('')
  const [password, setPassword] = useState('')
  const [submitting, setSubmitting] = useState(false)
  const [error, setError] = useState<string | null>(null)

  async function submit(event: FormEvent) {
    event.preventDefault()
    setSubmitting(true)
    setError(null)
    try {
      onAuthenticated(await login(username, password))
    } catch (caught) {
      setError(getApiErrorMessage(caught))
    } finally {
      setSubmitting(false)
    }
  }

  return <main className="flex min-h-screen items-center justify-center bg-slate-950 px-4 text-slate-100">
    <form onSubmit={(event) => void submit(event)} className="w-full max-w-sm rounded-2xl border border-slate-800 bg-slate-900 p-7 shadow-xl">
      <p className="text-sm font-semibold uppercase tracking-[0.3em] text-cyan-400">Infrastructure monitoring</p>
      <h1 className="mt-3 text-4xl font-bold">Pulse</h1>
      <p className="mt-2 text-sm text-slate-400">Sign in to open the monitoring dashboard.</p>
      <label className="field-label" htmlFor="username">Username</label>
      <input id="username" className="field-input" autoComplete="username" required maxLength={120}
        value={username} onChange={(event) => setUsername(event.target.value)} />
      <label className="field-label" htmlFor="password">Password</label>
      <input id="password" className="field-input" type="password" autoComplete="current-password" required
        value={password} onChange={(event) => setPassword(event.target.value)} />
      {error && <p role="alert" className="mt-4 rounded-lg bg-red-950/70 px-3 py-2 text-sm text-red-200">{error}</p>}
      <button className="mt-6 w-full rounded-lg bg-cyan-500 px-4 py-2.5 font-semibold text-slate-950 transition hover:bg-cyan-400 disabled:opacity-60"
        disabled={submitting}>{submitting ? 'Signing in...' : 'Sign in'}</button>
    </form>
  </main>
}
