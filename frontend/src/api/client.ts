import axios from 'axios'
import type { ApiErrorResponse } from '../types/service'
import { clearAuthSession, getAuthSession, unauthorizedEvent } from '../auth/session'

const configuredApiBaseUrl = import.meta.env.VITE_API_BASE_URL?.trim()

export const apiClient = axios.create({
  baseURL: configuredApiBaseUrl || '/api',
  headers: { 'Content-Type': 'application/json' },
})

apiClient.interceptors.request.use((config) => {
  const session = getAuthSession()
  if (session) config.headers.set('Authorization', `Bearer ${session.token}`)
  return config
})

apiClient.interceptors.response.use(undefined, (error: unknown) => {
  if (axios.isAxiosError(error) && error.response?.status === 401
    && !error.config?.url?.endsWith('/auth/login')) {
    clearAuthSession()
    if (typeof window !== 'undefined') window.dispatchEvent(new Event(unauthorizedEvent))
  }
  return Promise.reject(error)
})

export function getApiErrorMessage(error: unknown): string {
  if (axios.isAxiosError<ApiErrorResponse>(error)) {
    const body = error.response?.data
    if (body?.fieldErrors && Object.keys(body.fieldErrors).length > 0) {
      return Object.values(body.fieldErrors).join(' ')
    }
    return body?.message ?? error.message
  }
  return error instanceof Error ? error.message : 'An unexpected error occurred'
}
