import { apiClient } from './client'
import type {
  AuthResponse,
  ChangePasswordRequest,
  LoginRequest,
  RegisterRequest,
} from './types'

export const authApi = {
  register: (payload: RegisterRequest) =>
    apiClient.post<AuthResponse>('/auth/register', payload).then((r) => r.data),

  login: (payload: LoginRequest) =>
    apiClient.post<AuthResponse>('/auth/login', payload).then((r) => r.data),

  logout: (refreshToken: string) =>
    apiClient.post<void>('/auth/logout', { refreshToken }).then((r) => r.data),

  changePassword: (payload: ChangePasswordRequest) =>
    apiClient.post<void>('/auth/change-password', payload).then((r) => r.data),
}
