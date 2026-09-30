import { apiClient } from './client'
import type { TagRequest, TagResponse } from './types'

export const tagApi = {
  list: () => apiClient.get<TagResponse[]>('/tags').then((r) => r.data),

  create: (payload: TagRequest) => apiClient.post<TagResponse>('/tags', payload).then((r) => r.data),

  update: (id: number, payload: TagRequest) =>
    apiClient.put<TagResponse>(`/tags/${id}`, payload).then((r) => r.data),

  delete: (id: number) => apiClient.delete<void>(`/tags/${id}`).then((r) => r.data),
}
