import { beforeEach, describe, expect, it, vi } from 'vitest'
import { clearAuthSession, getAuthSession, saveAuthSession } from './session'

const values = new Map<string, string>()
const sessionStorage = {
  getItem: (key: string) => values.get(key) ?? null,
  setItem: (key: string, value: string) => { values.set(key, value) },
  removeItem: (key: string) => { values.delete(key) },
}

Object.defineProperty(globalThis, 'window', {
  configurable: true,
  value: { sessionStorage },
})

describe('authentication session', () => {
  beforeEach(() => {
    values.clear()
    vi.restoreAllMocks()
  })

  it('stores a token only for the browser session and restores it before expiry', () => {
    vi.spyOn(Date, 'now').mockReturnValue(1_000)
    expect(saveAuthSession('signed-token', 5_000)).toEqual({
      token: 'signed-token', expiresAt: 6_000,
    })
    expect(getAuthSession()).toEqual({ token: 'signed-token', expiresAt: 6_000 })
  })

  it('removes expired or malformed sessions', () => {
    vi.spyOn(Date, 'now').mockReturnValue(10_000)
    values.set('pulse.auth', JSON.stringify({ token: 'expired', expiresAt: 9_999 }))
    expect(getAuthSession()).toBeNull()
    expect(values.has('pulse.auth')).toBe(false)

    values.set('pulse.auth', 'not-json')
    expect(getAuthSession()).toBeNull()
    clearAuthSession()
    expect(values.has('pulse.auth')).toBe(false)
  })
})
