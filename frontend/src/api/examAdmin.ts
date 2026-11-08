import { apiClient } from './client'
import type {
  AdminExamPassage,
  AdminExamQuestion,
  AdminExamQuestionRequest,
  BulkApprovalResult,
  ExamImportResult,
  ExamQuestionStatus,
  ExamSectionName,
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
  /** Chỉ câu có báo lỗi của người học đang chờ xem. */
  reported?: boolean
  /** Chỉ câu của một đề tự soạn (mã đề, vd. N3-05), theo thứ tự câu trong đề. */
  test?: string
  page?: number
  size?: number
}

/** Bộ lọc của "duyệt tất cả": bắt buộc có cấp độ; các trường còn lại bỏ trống = không lọc. */
export interface ApproveAllFilter {
  level: string
  type?: JlptQuestionType
  /** Chỉ dùng khi duyệt đoạn văn. */
  section?: ExamSectionName
  test?: string
  /** Chỉ dùng khi duyệt câu lẻ. */
  grammarPointId?: number
}

/** Chỉ ADMIN: duyệt câu thi đề JLPT. */
export const examAdminApi = {
  search: (filter: ExamQuestionFilter) =>
    apiClient.get<Page<AdminExamQuestion>>('/admin/exam-questions', { params: filter }).then((r) => r.data),

  update: (id: number, payload: AdminExamQuestionRequest) =>
    apiClient.put<AdminExamQuestion>(`/admin/exam-questions/${id}`, payload).then((r) => r.data),

  /** Duyệt / loại (cần lý do) / rút khỏi đề / đưa về chờ duyệt. */
  changeStatus: (id: number, status: ExamQuestionStatus, note?: string) =>
    apiClient
      .post<AdminExamQuestion>(`/admin/exam-questions/${id}/status`, {
        status,
        note,
      })
      .then((r) => r.data),

  /** Duyệt một lượt các câu đã đọc trên trang; chỉ câu chờ duyệt, không cảnh báo, đúng cấu trúc mới được duyệt. */
  approveAll: (ids: number[]) => apiClient.post<BulkApprovalResult>('/admin/exam-questions/approve', { ids }).then((r) => r.data),

  /** Duyệt mọi câu chờ duyệt khớp bộ lọc (không chỉ một trang); câu có cảnh báo / sai cấu trúc giữ nguyên. */
  approveAllMatching: (filter: ApproveAllFilter) =>
    apiClient.post<BulkApprovalResult>('/admin/exam-questions/approve-all', filter).then((r) => r.data),

  /** Bỏ qua các báo lỗi đang mở của một câu (câu không sai); không đổi trạng thái câu. */
  dismissReports: (id: number) => apiClient.post<AdminExamQuestion>(`/admin/exam-questions/${id}/reports/dismiss`).then((r) => r.data),

  /** Phân tích câu hỏi ngay (như job hằng tuần): thống kê từng câu, gắn cờ câu đáng ngờ. */
  analyze: () =>
    apiClient
      .post<{ analyzed: number; flagged: number }>('/admin/exam-questions/analysis', null, { timeout: 120_000 })
      .then((r) => r.data),

  stats: () => apiClient.get<QuestionBankStats[]>('/admin/exam-questions/stats').then((r) => r.data),

  /** Nhập một đề tự soạn (nội dung file JSON); dryRun = chỉ kiểm tra. Có lỗi thì không ghi gì. */
  importTest: (test: unknown, dryRun: boolean) =>
    apiClient
      .post<ExamImportResult>('/admin/exam-questions/import', test, {
        params: { dryRun },
        timeout: 60_000,
      })
      .then((r) => r.data),

  /** Nhập một bài đọc lẻ của phần Đọc hiểu (dạng {@code type}); dryRun = chỉ kiểm tra. Có lỗi thì không ghi gì. */
  importReading: (type: JlptQuestionType, passage: unknown, dryRun: boolean) =>
    apiClient
      .post<ExamImportResult>('/admin/exam-questions/import/reading', passage, {
        params: { type, dryRun },
        timeout: 60_000,
      })
      .then((r) => r.data),

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

  /** Đoạn văn của một cấp độ (文章の文法, bài đọc), kèm câu hỏi; lọc thêm theo phần thi, dạng câu, trạng thái. */
  passages: (filter: {
    level: string
    section?: ExamSectionName
    type?: JlptQuestionType
    status?: ExamQuestionStatus
    /** Chỉ đoạn của một đề tự soạn (mã đề, vd. N3-DH01). */
    test?: string
    page?: number
    size?: number
  }) => apiClient.get<Page<AdminExamPassage>>('/admin/exam-passages', { params: filter }).then((r) => r.data),

  updatePassage: (id: number, payload: { title: string; content: string }) =>
    apiClient.put<AdminExamPassage>(`/admin/exam-passages/${id}`, payload).then((r) => r.data),

  /** Nhờ AI viết nháp một đoạn văn 文章の文法 (tốn 2 request Gemini); đoạn văn chờ người duyệt cả đoạn. */
  draftPassage: (level: string) =>
    apiClient.post<PassageDraftResult>('/admin/exam-passages/drafts', { level }, { timeout: 120_000 }).then((r) => r.data),

  /** Duyệt / loại / rút cả đoạn văn cùng mọi câu hỏi của nó. */
  changePassageStatus: (id: number, status: ExamQuestionStatus, note?: string) =>
    apiClient
      .post<AdminExamPassage>(`/admin/exam-passages/${id}/status`, {
        status,
        note,
      })
      .then((r) => r.data),

  /** Duyệt một lượt các đoạn đã đọc trên trang; chỉ đoạn chờ duyệt, không cảnh báo, hợp lệ mới được duyệt. */
  approveAllPassages: (ids: number[]) =>
    apiClient.post<BulkApprovalResult>('/admin/exam-passages/approve', { ids }).then((r) => r.data),

  /** Duyệt mọi đoạn chờ duyệt khớp bộ lọc (không chỉ một trang); đoạn có cảnh báo / không hợp lệ giữ nguyên. */
  approveAllPassagesMatching: (filter: ApproveAllFilter) =>
    apiClient.post<BulkApprovalResult>('/admin/exam-passages/approve-all', filter).then((r) => r.data),
}
