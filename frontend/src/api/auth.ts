import { apiClient } from './client'
import { saveAuthSession, type AuthSession } from '../auth/session'

interface LoginResponse {
  token: string
  expiresIn: number
}

export async function login(username: string, password: string): Promise<AuthSession> {
  const response = await apiClient.post<LoginResponse>('/auth/login', { username, password })
  return saveAuthSession(response.data.token, response.data.expiresIn)
}
