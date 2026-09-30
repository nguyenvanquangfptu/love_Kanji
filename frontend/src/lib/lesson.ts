import { useSearchParams } from 'react-router-dom'

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
