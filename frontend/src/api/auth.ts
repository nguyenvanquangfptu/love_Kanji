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

  /** Ends every session, this one included: clear the session and send the user to /login afterwards. */
  changePassword: (payload: ChangePasswordRequest) =>
    apiClient.post<void>('/auth/change-password', payload).then((r) => r.data),
}
