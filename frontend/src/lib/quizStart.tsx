import { type ReactNode, useCallback, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import type { QuizAnswerKind } from '@/api/types'
import { QuizStartDialog } from '@/components/QuizStartDialog'

const LAST_KIND_KEY = 'quiz.answerKind'

/** Kiểu đã chọn lần trước - chỉ để chọn sẵn; trình duyệt chặn bộ nhớ thì coi như chưa chọn. */
function lastKind(): QuizAnswerKind {
  try {
    return localStorage.getItem(LAST_KIND_KEY) === 'typing' ? 'typing' : 'choice'
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

/** Đường dẫn trang làm bài cho một kiểu: gõ romaji thêm {@code answer=typing}. */
export function quizPath(path: string, kind: QuizAnswerKind) {
  return kind === 'typing' ? `${path}${path.includes('?') ? '&' : '?'}answer=typing` : path
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
