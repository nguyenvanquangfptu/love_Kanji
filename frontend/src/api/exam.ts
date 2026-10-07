import { apiClient } from './client'
import type {
  ExamResultResponse,
  ExamReviewResponse,
  ExamSessionResponse,
  ExamSittingResponse,
  JlptLeaderboardEntry,
  JlptLevelResponse,
  PracticeQuestion,
  SaveAnswerRequest,
  StartExamRequest,
  StartExamResponse,
  QuestionReportReason,
  StartJlptExamRequest,
  WeakGrammarPoint,
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

  /** Bảng xếp hạng đề JLPT của cấp độ (tách riêng với bảng xếp hạng thi nhanh). */
  jlptLeaderboard: (level: string, limit: number) =>
    apiClient
      .get<JlptLeaderboardEntry[]>('/exams/jlpt/leaderboard', { params: { level, limit } })
      .then((r) => r.data),

  /** Dòng của người học trên bảng xếp hạng đề JLPT; rank null khi chưa có buổi thi trọn vẹn. */
  myJlptRank: (level: string) =>
    apiClient.get<JlptLeaderboardEntry>('/exams/jlpt/leaderboard/me', { params: { level } }).then((r) => r.data),

  /** Báo lỗi một câu đã làm; câu bị nhiều người báo tự rút khỏi đề chờ duyệt lại. */
  reportQuestion: (questionId: number, payload: { reason: QuestionReportReason; note?: string }) =>
    apiClient.post<void>(`/exams/questions/${questionId}/reports`, payload).then((r) => r.data),

  /** Các điểm ngữ pháp người học hay làm sai trong các đề JLPT gần đây của một cấp độ. */
  weakGrammar: (level: string) =>
    apiClient.get<WeakGrammarPoint[]>('/exams/jlpt/weak-grammar', { params: { level } }).then((r) => r.data),

  /** Câu luyện lại (đã duyệt, kèm đáp án) cho các điểm ngữ pháp; luyện không tính giờ, không lưu kết quả. */
  grammarPractice: (level: string, grammarPointIds: number[]) =>
    apiClient
      .get<PracticeQuestion[]>('/exams/jlpt/grammar-practice', {
        params: { level, grammarPointIds: grammarPointIds.join(',') },
      })
      .then((r) => r.data),

  /** Chỉ ADMIN: sinh câu thi cho các từ trong bài của một cấp độ (chỉ câu chưa có). */
  generateQuestions: (level: string) =>
    apiClient
      .post<{ level: string; words: number; created: number }>('/exams/questions/generate', null, { params: { level } })
      .then((r) => r.data),
}
