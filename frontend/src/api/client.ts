import axios, { AxiosError, type InternalAxiosRequestConfig } from 'axios'
import { useAuthStore } from '@/store/authStore'
import type { ApiErrorResponse, AuthResponse } from './types'

declare module 'axios' {
  export interface InternalAxiosRequestConfig {
    _retry?: boolean
  }
}

export const apiClient = axios.create({ baseURL: '/api/v1' })

/** Instance riêng, KHÔNG gắn interceptor - dùng để gọi refresh-token mà không đệ quy vào chính nó. */
const refreshClient = axios.create({ baseURL: '/api/v1' })

apiClient.interceptors.request.use((config) => {
  const token = useAuthStore.getState().accessToken
  if (token) {
    config.headers.set('Authorization', `Bearer ${token}`)
  }
  return config
})

/**
 * Mutex refresh: nhiều request 401 xảy ra gần như đồng thời chỉ được phép gọi
 * /auth/refresh-token ĐÚNG MỘT LẦN, dùng chung một Promise cấp-module. Backend
 * làm Refresh Token Rotation (refresh token cũ bị revoke ngay khi dùng), nên
 * nếu 2 request refresh chạy song song, request đến sau sẽ dùng token đã bị
 * revoke và khiến người dùng bị văng logout oan.
 */
let refreshPromise: Promise<string> | null = null

async function refreshAccessToken(): Promise<string> {
  const { refreshToken } = useAuthStore.getState()
  if (!refreshToken) throw new Error('Không có refresh token')

  const { data } = await refreshClient.post<AuthResponse>('/auth/refresh-token', { refreshToken })
  useAuthStore.getState().setTokens({ accessToken: data.accessToken, refreshToken: data.refreshToken })
  return data.accessToken
}

apiClient.interceptors.response.use(
  (response) => response,
  async (error: AxiosError<ApiErrorResponse>) => {
    const originalRequest = error.config as InternalAxiosRequestConfig | undefined
    const isAuthEndpoint = originalRequest?.url?.startsWith('/auth/')

    if (error.response?.status !== 401 || !originalRequest || originalRequest._retry || isAuthEndpoint) {
      return Promise.reject(error)
    }

    originalRequest._retry = true

    try {
      if (!refreshPromise) {
        refreshPromise = refreshAccessToken().finally(() => {
          refreshPromise = null
        })
      }
      const newAccessToken = await refreshPromise
      originalRequest.headers.set('Authorization', `Bearer ${newAccessToken}`)
      return apiClient(originalRequest)
    } catch (refreshError) {
      useAuthStore.getState().clearSession()
      window.location.href = '/login'
      return Promise.reject(refreshError)
    }
  },
)

export function extractErrorMessage(error: unknown): string {
  if (axios.isAxiosError<ApiErrorResponse>(error) && error.response?.data) {
    const { message, fieldErrors } = error.response.data
    if (fieldErrors?.length) return fieldErrors.join('; ')
    if (message) return message
  }
  return 'Đã xảy ra lỗi, vui lòng thử lại.'
}
