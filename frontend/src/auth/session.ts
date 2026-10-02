export interface AuthSession {
  token: string
  expiresAt: number
}

const storageKey = 'pulse.auth'
export const unauthorizedEvent = 'pulse:unauthorized'

export function getAuthSession(): AuthSession | null {
  if (typeof window === 'undefined') return null
  try {
    const raw = window.sessionStorage.getItem(storageKey)
    if (!raw) return null
    const session = JSON.parse(raw) as Partial<AuthSession>
    if (typeof session.token !== 'string' || typeof session.expiresAt !== 'number'
      || session.expiresAt <= Date.now()) {
      clearAuthSession()
      return null
    }
    return { token: session.token, expiresAt: session.expiresAt }
  } catch {
    clearAuthSession()
    return null
  }
}

export function saveAuthSession(token: string, expiresIn: number): AuthSession {
  const session = { token, expiresAt: Date.now() + expiresIn }
  if (typeof window !== 'undefined') {
    window.sessionStorage.setItem(storageKey, JSON.stringify(session))
  }
  return session
}

export function clearAuthSession() {
  if (typeof window !== 'undefined') window.sessionStorage.removeItem(storageKey)
}
