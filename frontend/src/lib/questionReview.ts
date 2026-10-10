import type {
  ExamQuestionFlag,
  ExamQuestionStatus,
  PassageDraftResult,
  QuestionDraftResult,
  QuestionReportReason,
} from '@/api/types'

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
  REPORTED: 'Người học báo lỗi',
  STATS: 'Thống kê đáng ngờ',
}

/** Lý do người học báo lỗi câu hỏi. */
export const REPORT_REASON_LABEL: Record<QuestionReportReason, string> = {
  WRONG_ANSWER: 'Đáp án sai',
  AMBIGUOUS: 'Có hơn một đáp án đúng',
  UNCLEAR: 'Câu khó hiểu hoặc viết sai',
  OTHER: 'Lý do khác',
}

/** Nguồn của câu thi / đoạn văn. */
export const SOURCE_LABEL: Record<string, string> = {
  MANUAL: 'Soạn tay',
  GENERATED: 'Sinh từ kho từ',
  AI: 'AI viết nháp',
  IMPORTED: 'Đề tự soạn',
}

/** Mã câu đề gốc dễ đọc: "N3-05/NP/15" → "N3-05 · Ngữ pháp câu 15" (đoạn văn: "câu 19-23"). */
export function sourceRefLabel(ref: string): string {
  const [code, part, number] = ref.split('/')
  const partLabel = part === 'TV' ? 'Từ vựng' : part === 'NP' ? 'Ngữ pháp' : part
  return number ? `${code} · ${partLabel} câu ${number}` : ref
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

/** Tóm tắt một lần nhờ AI viết nháp đoạn văn 文章の文法. */
export function passageDraftSummary(result: PassageDraftResult): string {
  if (result.status === 'REJECTED') {
    return `Đoạn #${result.passageId} sai cấu trúc nên bị loại - xem lý do ở mục "Đã loại".`
  }
  return `Đoạn #${result.passageId} (${result.questions} chỗ trống) vào hàng chờ duyệt${
    result.flag ? ` - ⚠ ${FLAG_LABEL[result.flag]}` : ''
  }.`
}
