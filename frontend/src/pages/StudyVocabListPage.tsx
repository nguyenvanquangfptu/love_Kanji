import { Link, Navigate, useNavigate } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import { ChevronLeft, Layers, ListChecks, SearchX } from 'lucide-react'
import { kanjiApi } from '@/api/kanji'
import { extractErrorMessage } from '@/api/client'
import { LEVEL_META, levelStyle, lessonTitle, parseTagName } from '@/lib/levels'
import { useLessonParams } from '@/lib/lesson'
import { cn } from '@/lib/utils'
import { Button } from '@/components/ui/button'
import { Card } from '@/components/ui/card'
import { Alert } from '@/components/ui/alert'
import { PageSpinner } from '@/components/ui/spinner'
import { EmptyState } from '@/components/EmptyState'
import { SpeakButton } from '@/components/SpeakButton'
import { LessonReviewCard } from '@/components/LessonReviewCard'
import { useQuizStart } from '@/lib/quizStart'

export function StudyVocabListPage() {
  const navigate = useNavigate()
  const quiz = useQuizStart()
  const { tagId, name, query } = useLessonParams()
  const { level, lesson } = parseTagName(name)
  const style = levelStyle(level)

  const { data, isLoading, isError, error } = useQuery({
    queryKey: ['study-vocab', tagId],
    queryFn: () => kanjiApi.search({ tagId: tagId ?? undefined, size: 500 }),
    enabled: tagId !== null,
  })

  if (tagId === null) return <Navigate to="/study" replace />

  const words = data?.content ?? []

  return (
    <div>
      {quiz.dialog}
      <Link
        to="/study"
        className="mb-4 inline-flex items-center gap-1 rounded-xl py-1 pr-2 text-sm font-extrabold text-muted-foreground hover:text-foreground"
      >
        <ChevronLeft className="h-5 w-5" />
        Học bài
      </Link>

      <div className={cn('flex items-center gap-4 rounded-3xl p-5', style.soft)}>
        <span
          className={cn(
            'flex h-16 w-16 shrink-0 items-center justify-center rounded-2xl border-b-4 text-2xl font-black',
            style.solid,
          )}
        >
          {lesson ?? '#'}
        </span>
        <div className="min-w-0">
          <p className={cn('truncate text-xs font-extrabold uppercase tracking-wider', style.text)}>
            {level ? `${level}${LEVEL_META[level].book ? ` · ${LEVEL_META[level].book}` : ''}` : 'Bộ từ vựng'}
          </p>
          <h1 className="text-2xl font-black sm:text-3xl">{lessonTitle(name)}</h1>
          <p className="text-sm font-bold text-muted-foreground">{data ? `${words.length} từ vựng` : '...'}</p>
        </div>
      </div>

      <div className="mt-4 grid grid-cols-2 gap-3">
        <Button
          variant="secondary"
          disabled={words.length === 0}
          onClick={() => navigate(`/study/flashcards?${query}`)}
        >
          <Layers className="h-5 w-5" strokeWidth={2.5} />
          Học bằng thẻ
        </Button>
        <Button disabled={words.length === 0} onClick={() => quiz.start(`/study/quiz?${query}`)}>
          <ListChecks className="h-5 w-5" strokeWidth={2.5} />
          Trắc nghiệm
        </Button>
      </div>

      {words.length > 0 && <LessonReviewCard tagId={tagId} wordIds={words.map((word) => word.id)} />}

      <h2 className="mb-3 mt-8 text-lg font-black">Danh sách từ vựng</h2>

      {isLoading && <PageSpinner />}
      {isError && <Alert>{extractErrorMessage(error)}</Alert>}
      {data && words.length === 0 && (
        <EmptyState icon={SearchX} title="Bài này chưa có từ vựng" description="Hãy quay lại sau nhé." />
      )}

      <div className="grid gap-3 md:grid-cols-2">
        {words.map((word) => (
          <Card key={word.id} className="flex items-center gap-4 p-4">
            <div className="flex min-w-[4.5rem] shrink-0 flex-col items-center">
              {word.reading && (
                <span className="font-jp text-xs font-bold text-secondary-dark">{word.reading}</span>
              )}
              <span className="font-jp text-3xl font-bold leading-tight">{word.character}</span>
            </div>
            <div className="min-w-0 flex-1">
              {word.hanViet && (
                <p className={cn('text-xs font-extrabold uppercase tracking-wide', style.text)}>{word.hanViet}</p>
              )}
              <p className="text-sm font-semibold leading-snug">{word.meaning}</p>
            </div>
            <SpeakButton text={word.reading ?? word.character} />
          </Card>
        ))}
      </div>
    </div>
  )
}
