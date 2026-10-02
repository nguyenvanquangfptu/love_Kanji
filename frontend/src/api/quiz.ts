import { apiClient } from './client'
import type { QuizAnswerRequest, QuizAnswerResponse, QuizMode, QuizQuestionResponse } from './types'

export const quizApi = {
  generate: (params: { tagId?: number; level?: string; size?: number; mode?: QuizMode; hardWords?: boolean }) =>
    apiClient.get<QuizQuestionResponse[]>('/quiz/generate', { params }).then((r) => r.data),

  submitAnswer: (payload: QuizAnswerRequest) =>
    apiClient.post<QuizAnswerResponse>('/quiz/answers', payload).then((r) => r.data),
}
