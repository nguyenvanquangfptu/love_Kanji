import { apiClient } from './client'
import type {
  AddSrsCardsResponse,
  GrammarPointStatus,
  GrammarReviewRequest,
  GrammarReviewResult,
  GrammarSession,
} from './types'

/** Ôn ngữ pháp theo lịch SM-2/FSRS như thẻ từ vựng. */
export const grammarSrsApi = {
  /** Điểm ngữ pháp của một cấp độ theo bài, kèm điểm nào đã có trong lịch ôn. */
  points: (level: string) =>
    apiClient.get<GrammarPointStatus[]>('/grammar-srs/points', { params: { level } }).then((r) => r.data),

  addCards: (grammarPointIds: number[]) =>
    apiClient.post<AddSrsCardsResponse>('/grammar-srs/cards', { grammarPointIds }).then((r) => r.data),

  /** Điểm đến hạn trước, rồi điểm mới trong giới hạn mỗi ngày. */
  session: () => apiClient.get<GrammarSession>('/grammar-srs/session').then((r) => r.data),

  review: (payload: GrammarReviewRequest) =>
    apiClient.post<GrammarReviewResult>('/grammar-srs/reviews', payload).then((r) => r.data),
}

/** Query key của TanStack Query: thêm điểm hay ôn xong thì làm mới cả hai. */
export const GRAMMAR_POINTS_KEY = ['grammar-srs', 'points'] as const
export const GRAMMAR_SESSION_KEY = ['grammar-srs', 'session'] as const
