import type { ExamQuestionPublicResponse } from '@/api/types'

/**
 * Backend chỉ trả lại đáp án đã tick khi khôi phục phiên thi (GET .../session),
 * KHÔNG trả lại nội dung câu hỏi (tránh phải thiết kế thêm endpoint lộ câu hỏi
 * ngoài luồng bắt đầu thi). Vì vậy Frontend tự cache nội dung câu hỏi lúc
 * `/exams/start` vào sessionStorage, dùng để hiển thị lại khi F5 giữa giờ thi.
 * Giới hạn: mất cache (đổi trình duyệt, xoá sessionStorage) thì không thể hiển
 * thị lại nội dung câu hỏi - trường hợp này người dùng vẫn nộp được bài vì
 * remainingSeconds + answers vẫn khôi phục được từ Backend.
 */
interface CachedExam {
  jlptLevel: string
  questions: ExamQuestionPublicResponse[]
}

function key(attemptId: number) {
  return `exam-cache:${attemptId}`
}

export function cacheExamQuestions(attemptId: number, data: CachedExam) {
  try {
    sessionStorage.setItem(key(attemptId), JSON.stringify(data))
  } catch {
    // sessionStorage có thể bị chặn (chế độ ẩn danh) - bỏ qua, chỉ mất khả năng khôi phục khi F5
  }
}

export function getCachedExamQuestions(attemptId: number): CachedExam | null {
  try {
    const raw = sessionStorage.getItem(key(attemptId))
    return raw ? (JSON.parse(raw) as CachedExam) : null
  } catch {
    return null
  }
}
