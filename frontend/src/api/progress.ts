import { apiClient } from './client'
import type { ProgressResponse } from './types'

export const progressApi = {
  get: () => apiClient.get<ProgressResponse>('/progress').then((r) => r.data),
}
