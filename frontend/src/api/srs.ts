import { apiClient } from './client'
import type {
  AddSrsCardsResponse,
  DailyCardResponse,
  Page,
  ReviewRequest,
  ReviewResponse,
  SrsStatsResponse,
  SrsTagStatusResponse,
} from './types'

export const srsApi = {
  getDailyCards: (params: { page?: number; size?: number }) =>
    apiClient.get<Page<DailyCardResponse>>('/srs/daily-cards', { params }).then((r) => r.data),

  submitReview: (payload: ReviewRequest) =>
    apiClient.post<ReviewResponse>('/srs/review', payload).then((r) => r.data),

  getStats: () => apiClient.get<SrsStatsResponse>('/srs/stats').then((r) => r.data),

  addCards: (kanjiIds: number[]) =>
    apiClient.post<AddSrsCardsResponse>('/srs/cards', { kanjiIds }).then((r) => r.data),

  getTagStatus: (tagId: number) =>
    apiClient.get<SrsTagStatusResponse>('/srs/cards/status', { params: { tagId } }).then((r) => r.data),
}
