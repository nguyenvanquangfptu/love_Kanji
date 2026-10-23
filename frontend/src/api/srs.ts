import { apiClient } from './client'
import type {
  AddSrsCardsResponse,
  DailyCardResponse,
  DailyPlanResponse,
  HardWordsResponse,
  Page,
  ReviewRequest,
  ReviewResponse,
  SrsStatsResponse,
  SrsTagStatusResponse,
} from './types'

export const srsApi = {
  /** `extra`: ôn thêm ngoài kế hoạch hôm nay - lấy mọi thẻ đến hạn. */
  getDailyCards: (params: { page?: number; size?: number; extra?: boolean }) =>
    apiClient.get<Page<DailyCardResponse>>('/srs/daily-cards', { params }).then((r) => r.data),

  submitReview: (payload: ReviewRequest) =>
    apiClient.post<ReviewResponse>('/srs/review', payload).then((r) => r.data),

  /** Hoàn tác lần chấm thẻ gần nhất của một từ: thẻ về y như trước lần chấm (trong 30 phút, khi chưa trả lời lại từ đó). */
  undoReview: (kanjiId: number) => apiClient.post<void>(`/srs/cards/${kanjiId}/undo-review`).then((r) => r.data),

  getStats: () => apiClient.get<SrsStatsResponse>('/srs/stats').then((r) => r.data),

  getDailyPlan: () => apiClient.get<DailyPlanResponse>('/srs/daily-plan').then((r) => r.data),

  getHardWords: () => apiClient.get<HardWordsResponse>('/srs/hard-words').then((r) => r.data),

  /** Để trống là xoá ghi chú. */
  saveNote: (vars: { kanjiId: number; note: string }) =>
    apiClient.put<void>(`/srs/cards/${vars.kanjiId}/note`, { note: vars.note }).then((r) => r.data),

  addCards: (kanjiIds: number[]) =>
    apiClient.post<AddSrsCardsResponse>('/srs/cards', { kanjiIds }).then((r) => r.data),

  getTagStatus: (tagId: number) =>
    apiClient.get<SrsTagStatusResponse>('/srs/cards/status', { params: { tagId } }).then((r) => r.data),
}
