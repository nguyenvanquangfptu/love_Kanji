import { apiClient } from './client'
import type { KanjiRequest, KanjiResponse, Page } from './types'

export const kanjiApi = {
  search: (params: { level?: string; keyword?: string; tagId?: number; page?: number; size?: number }) =>
    apiClient.get<Page<KanjiResponse>>('/kanji', { params }).then((r) => r.data),

  getById: (id: number) => apiClient.get<KanjiResponse>(`/kanji/${id}`).then((r) => r.data),

  create: (payload: KanjiRequest) => apiClient.post<KanjiResponse>('/kanji', payload).then((r) => r.data),

  update: (id: number, payload: KanjiRequest) =>
    apiClient.put<KanjiResponse>(`/kanji/${id}`, payload).then((r) => r.data),

  delete: (id: number) => apiClient.delete<void>(`/kanji/${id}`).then((r) => r.data),
}
