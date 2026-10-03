import type { ExamQuestionFlag, ExamQuestionStatus, QuestionDraftResult } from '@/api/types'

/** Nhãn trạng thái duyệt của câu thi / đoạn văn. */
export const STATUS_BADGE: Record<
  ExamQuestionStatus,
  { label: string; variant: 'orange' | 'success' | 'destructive' | 'outline' }
> = {
  DRAFT: { label: 'Chờ duyệt', variant: 'orange' },
  APPROVED: { label: 'Đã duyệt', variant: 'success' },
  REJECTED: { label: 'Đã loại', variant: 'destructive' },
  RETIRED: { label: 'Đã rút', variant: 'outline' },
}

/** Cảnh báo của bước kiểm tra tự động. */
export const FLAG_LABEL: Record<ExamQuestionFlag, string> = {
  AMBIGUOUS: 'Nghi có 2 đáp án',
  WRONG_ANSWER: 'Máy chọn đáp án khác',
  ABOVE_LEVEL: 'Từ vượt cấp độ',
}

/** Nguồn của câu thi / đoạn văn. */
export const SOURCE_LABEL: Record<string, string> = {
  MANUAL: 'Soạn tay',
  GENERATED: 'Sinh từ kho từ',
  AI: 'AI viết nháp',
}

/** Tóm tắt một lần nhờ AI viết nháp câu thi. */
export function draftSummary(result: QuestionDraftResult): string {
  return [
    `${result.drafted} câu vào hàng chờ duyệt${result.flagged > 0 ? ` (${result.flagged} câu có cảnh báo)` : ''}`,
    result.rejected > 0 && `${result.rejected} câu sai cấu trúc bị loại`,
    result.unreadable > 0 && `${result.unreadable} mục không đọc được`,
  ]
    .filter(Boolean)
    .join(' · ')
}

