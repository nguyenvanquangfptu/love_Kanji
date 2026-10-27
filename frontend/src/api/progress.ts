import { apiClient } from './client'
import type { ProgressResponse, StreakResponse } from './types'

export const progressApi = {
  get: () => apiClient.get<ProgressResponse>('/progress').then((r) => r.data),

  /** Chỉ chuỗi ngày học - cho thanh bên. */
  getStreak: () => apiClient.get<StreakResponse>('/progress/streak').then((r) => r.data),
}
