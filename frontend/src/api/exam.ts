import { apiClient } from './client'
import type {
  ExamResultResponse,
  ExamReviewResponse,
  ExamSessionResponse,
  ExamSittingResponse,
  JlptLevelResponse,
  SaveAnswerRequest,
  StartExamRequest,
  StartExamResponse,
  StartJlptExamRequest,
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

  /** Cấu trúc đề JLPT theo cấp độ, kèm số câu hỏi hiện có của từng dạng. */
  jlptLevels: () => apiClient.get<JlptLevelResponse[]>('/exams/jlpt/levels').then((r) => r.data),

  /** Bắt đầu buổi làm đề JLPT: tạo buổi thi và bắt đầu ngay phần đầu tiên. */
  startSitting: (payload: StartJlptExamRequest) =>
    apiClient.post<StartExamResponse>('/exams/jlpt/sittings', payload).then((r) => r.data),

  /** Bắt đầu phần kế tiếp, sau khi phần trước đã nộp hoặc hết giờ. */
  startNextSection: (sittingId: number) =>
    apiClient.post<StartExamResponse>(`/exams/jlpt/sittings/${sittingId}/next`).then((r) => r.data),

  getSitting: (sittingId: number) =>
    apiClient.get<ExamSittingResponse>(`/exams/jlpt/sittings/${sittingId}`).then((r) => r.data),

  /** Chỉ ADMIN: sinh câu thi cho các từ trong bài của một cấp độ (chỉ câu chưa có). */
  generateQuestions: (level: string) =>
    apiClient
      .post<{ level: string; words: number; created: number }>('/exams/questions/generate', null, { params: { level } })
      .then((r) => r.data),
}
