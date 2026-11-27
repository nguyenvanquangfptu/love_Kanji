import { type ReactNode, useCallback, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import type { QuizAnswerKind, QuizDirection } from '@/api/types'
import { QuizStartDialog } from '@/components/QuizStartDialog'

const LAST_KIND_KEY = 'quiz.answerKind'

/** Kiểu đã chọn lần trước - chỉ để chọn sẵn; trình duyệt chặn bộ nhớ thì coi như chưa chọn. */
function lastKind(): QuizAnswerKind {
  try {
    return parseQuizAnswerKind(localStorage.getItem(LAST_KIND_KEY))
  } catch {
    return 'choice'
  }
}

function rememberKind(kind: QuizAnswerKind) {
  try {
    localStorage.setItem(LAST_KIND_KEY, kind)
  } catch {
    // Không lưu được thì lần sau chọn sẵn "Chọn đáp án" - không ảnh hưởng bài làm.
  }
}

/** Kiểu trắc nghiệm từ tham số URL / bộ nhớ trình duyệt; không nhận ra thì là chọn đáp án. */
export function parseQuizAnswerKind(value: string | null): QuizAnswerKind {
  return value === 'typing' || value === 'cloze' || value === 'listening' ? value : 'choice'
}

/** Đường dẫn trang làm bài cho một kiểu: kiểu khác "chọn đáp án" thêm {@code answer=<kiểu>}. */
export function quizPath(path: string, kind: QuizAnswerKind) {
  return kind === 'choice' ? path : `${path}${path.includes('?') ? '&' : '?'}answer=${kind}`
}

/**
 * Hỏi kiểu trắc nghiệm trước khi bắt đầu: `start('/study/quiz?tagId=3')` mở hộp chọn, chọn xong mới chuyển trang.
 * Trả về cả hộp chọn để trang đặt vào cây giao diện.
 */
export function useQuizStart(): { start: (path: string) => void; dialog: ReactNode } {
  const navigate = useNavigate()
  const [pendingPath, setPendingPath] = useState<string | null>(null)
  const close = useCallback(() => setPendingPath(null), [])

  const choose = (kind: QuizAnswerKind) => {
    if (pendingPath === null) return
    rememberKind(kind)
    navigate(quizPath(pendingPath, kind))
  }

  return {
    start: setPendingPath,
    dialog: (
      <QuizStartDialog open={pendingPath !== null} preselected={lastKind()} onChoose={choose} onClose={close} />
    ),
  }
}

/** Đáp án của hướng hỏi này là nghĩa tiếng Việt (không phải chữ Nhật) - để chọn font. */
export function answersWithMeaning(direction: QuizDirection) {
  return direction === 'MEANING' || direction === 'LISTENING'
}
