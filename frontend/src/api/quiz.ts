import { apiClient } from './client'
import type { QuizAnswerRequest, QuizAnswerResponse, QuizMode, QuizQuestionResponse } from './types'

export const quizApi = {
  /** `kanjiIds`: danh sách id cách nhau bởi dấu phẩy - hỏi đúng các từ này. */
  generate: (params: {
    tagId?: number
    level?: string
    size?: number
    mode?: QuizMode
    hardWords?: boolean
    kanjiIds?: string
  }) =>
    apiClient.get<QuizQuestionResponse[]>('/quiz/generate', { params }).then((r) => r.data),

  submitAnswer: (payload: QuizAnswerRequest) =>
    apiClient.post<QuizAnswerResponse>('/quiz/answers', payload).then((r) => r.data),
}
