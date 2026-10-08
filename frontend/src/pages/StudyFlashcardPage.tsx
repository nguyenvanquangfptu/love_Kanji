import { useCallback, useEffect, useState } from 'react'
import { Navigate } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import { ChevronLeft, ListChecks, PartyPopper, RotateCcw, Shuffle } from 'lucide-react'
import { kanjiApi } from '@/api/kanji'
import { extractErrorMessage } from '@/api/client'
import { lessonFullTitle } from '@/lib/levels'
import { useLessonParams } from '@/lib/lesson'
import { cn, wordSizeClass } from '@/lib/utils'
import { FlipCard } from '@/components/FlipCard'
import { SessionHeader } from '@/components/SessionHeader'
import { SpeakButton } from '@/components/SpeakButton'
import { EmptyState } from '@/components/EmptyState'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Alert } from '@/components/ui/alert'
import { PageSpinner } from '@/components/ui/spinner'
import { useQuizStart } from '@/lib/quizStart'

function randomPermutation(length: number) {
  const order = Array.from({ length }, (_, i) => i)
  for (let i = order.length - 1; i > 0; i--) {
    const j = Math.floor(Math.random() * (i + 1))
    ;[order[i], order[j]] = [order[j], order[i]]
  }
  return order
}

export function StudyFlashcardPage() {
  const quiz = useQuizStart()
  const { tagId, name, query } = useLessonParams()

  const [index, setIndex] = useState(0)
  const [flipped, setFlipped] = useState(false)
  const [finished, setFinished] = useState(false)
  const [shuffledOrder, setShuffledOrder] = useState<number[] | null>(null)

  const { data, isLoading, isError, error } = useQuery({
    queryKey: ['study-vocab', tagId],
    queryFn: () => kanjiApi.search({ tagId: tagId ?? undefined, size: 500 }),
    enabled: tagId !== null,
  })

  const words = data?.content ?? []
  const order = shuffledOrder ?? words.map((_, i) => i)
  const current = words[order[index]]
  const total = words.length
  const isLast = index === total - 1

  const goTo = useCallback(
    (next: number) => {
      setFlipped(false)
      setIndex(Math.min(Math.max(next, 0), total - 1))
    },
    [total],
  )

  const advance = useCallback(() => {
    if (isLast) setFinished(true)
    else goTo(index + 1)
  }, [isLast, goTo, index])

  function restart(shuffle: boolean) {
    setShuffledOrder(shuffle ? randomPermutation(total) : null)
    setIndex(0)
    setFlipped(false)
    setFinished(false)
  }

  useEffect(() => {
    if (!current || finished) return
    function onKeyDown(e: KeyboardEvent) {
      if (e.code === 'Space') {
        e.preventDefault()
        setFlipped((f) => !f)
      } else if (e.key === 'ArrowRight') {
        advance()
      } else if (e.key === 'ArrowLeft') {
        goTo(index - 1)
      }
    }
    window.addEventListener('keydown', onKeyDown)
    return () => window.removeEventListener('keydown', onKeyDown)
  }, [current, finished, advance, goTo, index])

  if (tagId === null) return <Navigate to="/study" replace />

  const exitTo = `/study/vocab?${query}`
  const progress = finished ? 100 : total > 0 ? (index / total) * 100 : 0

  return (
    <>
      {quiz.dialog}
      <SessionHeader
        exitTo={exitTo}
        progress={progress}
        right={
          total > 0 && (
            <span className="min-w-[3.5rem] text-right text-sm font-extrabold text-muted-foreground">
              {Math.min(index + 1, total)}/{total}
            </span>
          )
        }
      />
      <main className="mx-auto max-w-2xl px-4 pb-10">
        {isLoading && <PageSpinner />}
        {isError && <Alert>{extractErrorMessage(error)}</Alert>}

        {finished && (
          <EmptyState
            icon={PartyPopper}
            iconClassName="bg-accent-soft text-accent-dark"
            title="Hoàn thành bài học!"
            description={`Bạn đã xem hết ${total} thẻ của ${lessonFullTitle(name)}. Làm bài trắc nghiệm để kiểm tra lại nhé!`}
            action={
              <div className="flex w-full max-w-xs flex-col gap-3">
                <Button size="lg" onClick={() => quiz.start(`/study/quiz?${query}`)}>
                  <ListChecks className="h-5 w-5" /> Làm trắc nghiệm
                </Button>
                <Button variant="outline" onClick={() => restart(false)}>
                  <RotateCcw className="h-5 w-5" /> Xem lại từ đầu
                </Button>
              </div>
            }
          />
        )}

        {!finished && current && (
          <>
            <p className="mb-4 text-center text-sm font-extrabold uppercase tracking-wider text-muted-foreground">
              {lessonFullTitle(name)}
            </p>

            <FlipCard
              flipped={flipped}
              onClick={() => setFlipped((f) => !f)}
              front={
                <>
                  <span className={cn('font-jp font-bold leading-none', wordSizeClass(current.character))}>
                    {current.character}
                  </span>
                  <Badge variant="secondary" className="mt-6">
                    {current.jlptLevel}
                  </Badge>
                  <p className="absolute bottom-5 text-xs font-bold text-muted-foreground">Chạm để lật thẻ</p>
                </>
              }
              back={
                <div className="flex w-full flex-col items-center gap-2 text-center">
                  {current.reading && (
                    <p className="font-jp text-xl font-bold text-secondary-dark">{current.reading}</p>
                  )}
                  <p className="font-jp text-5xl font-bold">{current.character}</p>
                  {current.hanViet && (
                    <p className="text-sm font-extrabold uppercase tracking-wide text-muted-foreground">
                      {current.hanViet}
                    </p>
                  )}
                  <p className="mt-2 text-lg font-bold leading-snug">{current.meaning}</p>
                  <SpeakButton text={current.reading ?? current.character} className="mt-1" />
                </div>
              }
            />

            <div className="mt-6 flex items-center gap-3">
              <Button
                variant="outline"
                size="icon"
                className="h-14 w-14 shrink-0"
                disabled={index === 0}
                onClick={() => goTo(index - 1)}
                aria-label="Thẻ trước"
              >
                <ChevronLeft className="h-6 w-6" />
              </Button>
              {flipped ? (
                <Button size="lg" className="flex-1" onClick={advance}>
                  {isLast ? 'Hoàn thành' : 'Tiếp theo'}
                </Button>
              ) : (
                <Button variant="secondary" size="lg" className="flex-1" onClick={() => setFlipped(true)}>
                  Lật thẻ
                </Button>
              )}
              <Button
                variant="outline"
                size="icon"
                className="h-14 w-14 shrink-0"
                onClick={() => restart(true)}
                aria-label="Trộn thẻ và học lại"
                title="Trộn thẻ"
              >
                <Shuffle className="h-6 w-6" />
              </Button>
            </div>
            <p className="mt-4 hidden text-center text-xs font-semibold text-muted-foreground sm:block">
              Phím tắt: Space lật thẻ · ← → chuyển thẻ
            </p>
          </>
        )}
      </main>
    </>
  )
}
