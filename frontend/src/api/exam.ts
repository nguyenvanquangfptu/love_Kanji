import { apiClient } from './client'
import type {
  ExamResultResponse,
  ExamReviewResponse,
  ExamSessionResponse,
  SaveAnswerRequest,
  StartExamRequest,
  StartExamResponse,
} from './types'

export const examApi = {
  start: (payload: StartExamRequest) =>
    apiClient.post<StartExamResponse>('/exams/start', payload).then((r) => r.data),

  saveAnswer: (attemptId: number, payload: SaveAnswerRequest) =>
    apiClient.put<void>(`/exams/attempts/${attemptId}/answers`, payload).then((r) => r.data),

  getSession: (attemptId: number) =>
    apiClient.get<ExamSessionResponse>(`/exams/attempts/${attemptId}/session`).then((r) => r.data),

  submit: (attemptId: number) =>
    apiClient.post<ExamResultResponse>(`/exams/attempts/${attemptId}/submit`).then((r) => r.data),

  getReview: (attemptId: number) =>
    apiClient.get<ExamReviewResponse>(`/exams/attempts/${attemptId}/review`).then((r) => r.data),

  /** Chỉ ADMIN: sinh câu thi cho các từ trong bài của một cấp độ (chỉ câu chưa có). */
  generateQuestions: (level: string) =>
    apiClient
      .post<{ level: string; words: number; created: number }>('/exams/questions/generate', null, { params: { level } })
      .then((r) => r.data),
}
