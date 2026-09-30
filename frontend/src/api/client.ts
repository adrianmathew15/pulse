import axios from 'axios'
import type { ApiErrorResponse } from '../types/service'

export const apiClient = axios.create({
  baseURL: '/api',
  headers: { 'Content-Type': 'application/json' },
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
