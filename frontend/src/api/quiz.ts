import { apiClient } from './client'
import type { QuizQuestionResponse } from './types'

export const quizApi = {
  generate: (params: { tagId?: number; level?: string; size?: number }) =>
    apiClient.get<QuizQuestionResponse[]>('/quiz/generate', { params }).then((r) => r.data),
}
