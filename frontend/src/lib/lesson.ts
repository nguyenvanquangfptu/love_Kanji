import { useSearchParams } from 'react-router-dom'
import { JLPT_LEVELS, type JlptLevel } from '@/api/types'

/** Query string định danh một bài học (tag) - dùng chung cho trang danh sách, flashcard và trắc nghiệm. */
export function lessonQuery(tag: { id: number; name: string }) {
  return `tagId=${tag.id}&name=${encodeURIComponent(tag.name)}`
}

export function useLessonParams() {
  const [searchParams] = useSearchParams()
  const rawId = Number(searchParams.get('tagId'))
  const tagId = Number.isInteger(rawId) && rawId > 0 ? rawId : null
  const name = searchParams.get('name') ?? ''
  return { tagId, name, query: tagId ? lessonQuery({ id: tagId, name }) : '' }
}

/** Số câu cho trắc nghiệm tổng hợp cả cấp độ (backend chặn tối đa 50 câu mỗi lượt). */
export const LEVEL_QUIZ_SIZES = [10, 20, 30, 50] as const

/** Query string của trắc nghiệm tổng hợp: câu hỏi ngẫu nhiên từ toàn bộ từ vựng một cấp độ, không theo bài. */
export function levelQuizQuery(level: JlptLevel, size: number) {
  return `level=${level}&size=${size}`
}

export function useLevelQuizParams() {
  const [searchParams] = useSearchParams()
  const rawLevel = searchParams.get('level')
  const level = JLPT_LEVELS.find((l) => l === rawLevel) ?? null
  const rawSize = Number(searchParams.get('size'))
  const size = LEVEL_QUIZ_SIZES.find((s) => s === rawSize) ?? LEVEL_QUIZ_SIZES[0]
  return { level, size }
}
