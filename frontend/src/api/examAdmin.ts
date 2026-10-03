import { apiClient } from './client'
import type {
  AdminExamPassage,
  AdminExamQuestion,
  AdminExamQuestionRequest,
  BulkApprovalResult,
  ExamQuestionStatus,
  JlptQuestionType,
  Page,
  PassageDraftResult,
  QuestionBankStats,
  QuestionDraftResult,
} from './types'

export interface ExamQuestionFilter {
  level?: string
  type?: JlptQuestionType
  status?: ExamQuestionStatus
  flagged?: boolean
  grammarPointId?: number
  page?: number
  size?: number
}

/** Chỉ ADMIN: duyệt câu thi đề JLPT. */
export const examAdminApi = {
  search: (filter: ExamQuestionFilter) =>
    apiClient.get<Page<AdminExamQuestion>>('/admin/exam-questions', { params: filter }).then((r) => r.data),

  update: (id: number, payload: AdminExamQuestionRequest) =>
    apiClient.put<AdminExamQuestion>(`/admin/exam-questions/${id}`, payload).then((r) => r.data),

  /** Duyệt / loại (cần lý do) / rút khỏi đề / đưa về chờ duyệt. */
  changeStatus: (id: number, status: ExamQuestionStatus, note?: string) =>
    apiClient.post<AdminExamQuestion>(`/admin/exam-questions/${id}/status`, { status, note }).then((r) => r.data),

  /** Duyệt một lượt các câu đã đọc trên trang; chỉ câu chờ duyệt, không cảnh báo, đúng cấu trúc mới được duyệt. */
  approveAll: (ids: number[]) =>
    apiClient.post<BulkApprovalResult>('/admin/exam-questions/approve', { ids }).then((r) => r.data),

  stats: () => apiClient.get<QuestionBankStats[]>('/admin/exam-questions/stats').then((r) => r.data),

  /** Nhờ AI viết nháp câu ngữ pháp cho một điểm ngữ pháp (tốn 2 request Gemini); câu nháp chờ người duyệt. */
  draft: (grammarPointId: number, type: JlptQuestionType, count: number) =>
    apiClient
      .post<QuestionDraftResult>('/admin/exam-questions/drafts', { grammarPointId, type, count }, { timeout: 120_000 })
      .then((r) => r.data),

  /** Nhờ AI viết nháp câu 言い換え類義 / 用法, mỗi câu cho một từ trong bài của cấp độ chưa có câu dạng đó. */
  draftVocabulary: (level: string, type: JlptQuestionType, count: number) =>
    apiClient
      .post<QuestionDraftResult>('/admin/exam-questions/vocabulary-drafts', { level, type, count }, { timeout: 120_000 })
      .then((r) => r.data),

  /** Đoạn văn 文章の文法 của một cấp độ, kèm câu hỏi. */
  passages: (filter: { level: string; status?: ExamQuestionStatus; page?: number; size?: number }) =>
    apiClient.get<Page<AdminExamPassage>>('/admin/exam-passages', { params: filter }).then((r) => r.data),

  updatePassage: (id: number, payload: { title: string; content: string }) =>
    apiClient.put<AdminExamPassage>(`/admin/exam-passages/${id}`, payload).then((r) => r.data),

  /** Nhờ AI viết nháp một đoạn văn 文章の文法 (tốn 2 request Gemini); đoạn văn chờ người duyệt cả đoạn. */
  draftPassage: (level: string) =>
    apiClient
      .post<PassageDraftResult>('/admin/exam-passages/drafts', { level }, { timeout: 120_000 })
      .then((r) => r.data),

  /** Duyệt / loại / rút cả đoạn văn cùng mọi câu hỏi của nó. */
  changePassageStatus: (id: number, status: ExamQuestionStatus, note?: string) =>
    apiClient.post<AdminExamPassage>(`/admin/exam-passages/${id}/status`, { status, note }).then((r) => r.data),
}
