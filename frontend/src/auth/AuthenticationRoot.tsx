import { useCallback, useEffect, useState } from 'react'
import App from '../App'
import { LoginPage } from '../components/LoginPage'
import { RealtimeProvider } from '../realtime/RealtimeProvider'
import { clearAuthSession, getAuthSession, unauthorizedEvent, type AuthSession } from './session'

export function AuthenticationRoot() {
  const [session, setSession] = useState<AuthSession | null>(() => getAuthSession())

  const logout = useCallback(() => {
    clearAuthSession()
    setSession(null)
  }, [])

  useEffect(() => {
    window.addEventListener(unauthorizedEvent, logout)
    return () => window.removeEventListener(unauthorizedEvent, logout)
  }, [logout])

  useEffect(() => {
    if (!session) return
    const remaining = session.expiresAt - Date.now()
    if (remaining <= 0) {
      logout()
      return
    }
    const timer = window.setTimeout(logout, remaining)
    return () => window.clearTimeout(timer)
  }, [logout, session])

  if (!session) return <LoginPage onAuthenticated={setSession} />
  return <RealtimeProvider token={session.token} onUnauthorized={logout}>
    <App onLogout={logout} />
  </RealtimeProvider>
}
