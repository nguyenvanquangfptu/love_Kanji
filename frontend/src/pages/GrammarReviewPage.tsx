import { useEffect, useRef, useState } from 'react'
import { Link } from 'react-router-dom'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Check, Lightbulb, ScrollText, Trophy, X } from 'lucide-react'
import { GRAMMAR_POINTS_KEY, GRAMMAR_SESSION_KEY, grammarSrsApi } from '@/api/grammarSrs'
import { extractErrorMessage } from '@/api/client'
import type { GrammarCard, GrammarReviewResult, ReviewRating } from '@/api/types'
import { QUESTION_TYPE_META } from '@/lib/jlpt'
import { RATING_OPTIONS } from '@/lib/ratings'
import { formatInterval } from '@/lib/dates'
import { lessonFullTitle } from '@/lib/levels'
import { cn } from '@/lib/utils'
import { SessionHeader } from '@/components/SessionHeader'
import { SentenceWithTarget } from '@/components/SentenceWithTarget'
import { FuriganaToggle } from '@/components/FuriganaToggle'
import { GrammarPointDetails } from '@/components/GrammarPointNote'
import { StatTile } from '@/components/StatTile'
import { EmptyState } from '@/components/EmptyState'
import { Button, buttonVariants } from '@/components/ui/button'
import { Card } from '@/components/ui/card'
import { Alert } from '@/components/ui/alert'
import { PageSpinner } from '@/components/ui/spinner'

const LETTERS = ['A', 'B', 'C', 'D'] as const
type Letter = (typeof LETTERS)[number]

/** Phiên đang ôn: chốt một lần lúc mở trang, không đổi theo các lần chấm (khác với GRAMMAR_SESSION_KEY). */
const RUN_KEY = ['grammar-srs', 'review-run'] as const

/** Lựa chọn ứng với phím 1-4 hoặc A-D; null với phím khác. */
function letterOfKey(key: string): Letter | null {
  if (/^[1-4]$/.test(key)) return LETTERS[Number(key) - 1]
  const upper = key.toUpperCase()
  return (LETTERS as readonly string[]).includes(upper) ? (upper as Letter) : null
}

function GrammarBrief({ card }: { card: GrammarCard }) {
  return (
    <GrammarPointDetails
      point={{
        id: card.grammarPointId,
        pattern: card.pattern,
        meaningVi: card.meaningVi,
        connection: card.connection,
        explanationVi: card.explanationVi,
      }}
    />
  )
}

/**
 * Ôn ngữ pháp hôm nay: điểm có câu hỏi đã duyệt thì trả lời câu (server chấm, đúng = Nhớ, sai = Quên), điểm chưa có
 * câu thì lật thẻ và tự chấm 4 mức. Phím: 1-4 / A-D chọn đáp án hoặc chấm, Space lật thẻ, Enter sang điểm tiếp.
 */
export function GrammarReviewPage() {
  const queryClient = useQueryClient()
  const [index, setIndex] = useState(0)
  const [flipped, setFlipped] = useState(false)
  const [chosen, setChosen] = useState<Letter | null>(null)
  const [result, setResult] = useState<GrammarReviewResult | null>(null)
  const [stats, setStats] = useState({ reviewed: 0, asked: 0, correct: 0 })
  /** Lúc điểm hiện tại hiện ra - để đo thời gian trả lời. */
  const shownAt = useRef(0)

  const { data: run, isLoading, isError, error } = useQuery({
    queryKey: RUN_KEY,
    queryFn: grammarSrsApi.session,
    staleTime: Infinity,
    refetchOnMount: 'always',
  })
  const cards = run?.cards ?? []
  const current: GrammarCard | undefined = cards[index]
  const isDone = cards.length > 0 && index >= cards.length

  const review = useMutation({
    mutationFn: grammarSrsApi.review,
    onSuccess: (data, variables) => {
      setStats((s) => ({
        reviewed: s.reviewed + 1,
        asked: s.asked + (variables.questionId ? 1 : 0),
        correct: s.correct + (variables.questionId && data.correct ? 1 : 0),
      }))
      if (variables.questionId) setResult(data)
      else next()
    },
  })

  // Ôn xong (hoặc rời trang giữa chừng): danh sách ngữ pháp, phiên hôm nay và chuỗi ngày học cần lấy lại.
  useEffect(
    () => () => {
      queryClient.invalidateQueries({ queryKey: GRAMMAR_SESSION_KEY })
      queryClient.invalidateQueries({ queryKey: GRAMMAR_POINTS_KEY })
      queryClient.invalidateQueries({ queryKey: ['progress'] })
    },
    [queryClient],
  )

  function next() {
    setIndex((i) => i + 1)
    setFlipped(false)
    setChosen(null)
    setResult(null)
    review.reset()
  }

  useEffect(() => {
    shownAt.current = Date.now()
  }, [index, run])

  function answer(letter: Letter) {
    if (!current?.question || chosen || review.isPending) return
    setChosen(letter)
    review.mutate({
      grammarPointId: current.grammarPointId,
      questionId: current.question.id,
      chosenOption: letter,
      responseMs: Date.now() - shownAt.current,
    })
  }

  function rate(rating: ReviewRating) {
    if (!current || current.question || !flipped || review.isPending) return
    review.mutate({ grammarPointId: current.grammarPointId, rating, responseMs: Date.now() - shownAt.current })
  }

  useEffect(() => {
    if (!current || isDone) return
    function onKeyDown(e: KeyboardEvent) {
      if (e.target instanceof HTMLButtonElement && (e.key === 'Enter' || e.key === ' ')) return
      if (current!.question) {
        const letter = letterOfKey(e.key)
        if (!chosen && letter) answer(letter)
        else if (result && e.key === 'Enter') next()
      } else if (!flipped && (e.key === ' ' || e.key === 'Enter')) {
        e.preventDefault()
        setFlipped(true)
      } else if (flipped && /^[1-4]$/.test(e.key)) {
        rate(Number(e.key) as ReviewRating)
      }
    }
    window.addEventListener('keydown', onKeyDown)
    return () => window.removeEventListener('keydown', onKeyDown)
  })

  const progress = cards.length > 0 ? ((index + (result ? 1 : 0)) / cards.length) * 100 : 0
  const question = current?.question ?? null
  const correctOption = result?.correctOption ?? null

  return (
    <>
      <SessionHeader exitTo="/grammar" progress={isDone ? 100 : progress} />

      <main className={cn('mx-auto max-w-2xl px-4', result ? 'pb-[70vh]' : 'pb-24')}>
        {isLoading && <PageSpinner label="Đang lấy các điểm cần ôn..." />}
        {isError && <Alert>{extractErrorMessage(error)}</Alert>}
        {run && cards.length === 0 && (
          <EmptyState
            icon={ScrollText}
            title="Hôm nay không còn điểm ngữ pháp nào cần ôn"
            description={
              run.newWaiting > 0
                ? 'Đã học đủ điểm mới hôm nay - mai học tiếp nhé.'
                : 'Thêm vài điểm ngữ pháp vào ôn tập để bắt đầu.'
            }
          />
        )}

        {isDone && (
          <div className="flex flex-col items-center pt-6 text-center">
            <span className="flex h-24 w-24 animate-bounce-in items-center justify-center rounded-full bg-purple-soft text-purple-dark">
              <Trophy className="h-12 w-12" strokeWidth={2.5} />
            </span>
            <h1 className="mt-4 text-3xl font-black">Xong phần ngữ pháp hôm nay!</h1>
            <p className="mt-1 text-muted-foreground">Các điểm đã ôn sẽ quay lại đúng lúc bạn sắp quên.</p>
            <div className="mt-6 grid w-full grid-cols-2 gap-3">
              <StatTile label="Đã ôn" value={`${stats.reviewed} điểm`} tone="purple" icon={ScrollText} />
              <StatTile
                label="Câu đúng"
                value={stats.asked > 0 ? `${stats.correct}/${stats.asked}` : '-'}
                tone="primary"
                icon={Check}
              />
            </div>
            <div className="mt-8 flex w-full flex-col gap-3 sm:flex-row">
              <Link to="/flashcards" className={cn(buttonVariants({ size: 'lg' }), 'flex-1')}>
                Ôn từ vựng
              </Link>
              <Link to="/grammar" className={cn(buttonVariants({ variant: 'outline', size: 'lg' }), 'flex-1')}>
                Về Ngữ pháp
              </Link>
            </div>
          </div>
        )}

        {current && !isDone && (
          <div key={current.grammarPointId} className="animate-pop-in">
            <p className="text-sm font-bold text-muted-foreground">
              Ngữ pháp {index + 1}/{cards.length} · {current.lesson ? lessonFullTitle(current.lesson) : current.jlptLevel}
              {current.newCard && <span className="ml-2 text-purple-dark">· Điểm mới</span>}
            </p>

            {question ? (
              <>
                <h1 className="mt-1 text-xl font-black sm:text-2xl">{question.questionText}</h1>
                <p className="mt-1 font-jp text-sm font-bold text-muted-foreground">
                  {QUESTION_TYPE_META[question.questionType]?.jp ?? question.questionType}
                </p>
                {question.sentence && (
                  <Card className="mt-6 p-5 sm:p-6">
                    <p className="font-jp text-xl leading-[2.4] sm:text-2xl sm:leading-[2.4]">
                      <SentenceWithTarget
                        sentence={question.sentence}
                        target={question.highlight}
                        segments={question.sentenceRuby}
                      />
                    </p>
                    <div className="mt-2 flex justify-end">
                      <FuriganaToggle segments={question.sentenceRuby} />
                    </div>
                  </Card>
                )}
                <div className="mt-8 grid gap-3 sm:grid-cols-2">
                  {LETTERS.map((letter) => {
                    const isCorrect = letter === correctOption
                    const isChosen = letter === chosen
                    return (
                      <button
                        key={letter}
                        type="button"
                        disabled={chosen !== null}
                        onClick={() => answer(letter)}
                        className={cn(
                          'flex min-h-16 items-center gap-3 rounded-2xl border-2 border-b-4 px-4 py-3 text-left font-bold transition-all disabled:cursor-default',
                          !chosen && 'border-border bg-card hover:bg-muted active:translate-y-[2px] active:border-b-2',
                          chosen && !result && isChosen && 'border-secondary bg-secondary-soft text-secondary-dark',
                          result && isCorrect && 'border-primary bg-primary-soft text-primary-dark',
                          result && isChosen && !isCorrect && 'border-destructive bg-destructive-soft text-destructive-dark',
                          chosen && !isCorrect && !isChosen && 'border-border bg-card opacity-50',
                        )}
                      >
                        <span className="flex h-8 w-8 shrink-0 items-center justify-center rounded-lg border-2 border-current text-sm font-black">
                          {letter}
                        </span>
                        <span className="font-jp text-lg leading-snug">{question[`option${letter}`]}</span>
                      </button>
                    )
                  })}
                </div>
              </>
            ) : (
              <>
                <Card className="mt-4 flex min-h-56 flex-col items-center justify-center gap-2 p-6 text-center">
                  <p className="font-jp text-4xl font-black text-purple-dark">{current.pattern}</p>
                  {current.connection && (
                    <p className="font-semibold text-muted-foreground">
                      Cách nối: <span className="font-jp text-foreground">{current.connection}</span>
                    </p>
                  )}
                  {!flipped && <p className="mt-2 text-sm font-bold text-muted-foreground">Mẫu này nghĩa là gì, dùng thế nào?</p>}
                </Card>
                {flipped ? (
                  <div className="mt-4 animate-pop-in">
                    <GrammarBrief card={current} />
                    <p className="mb-3 mt-6 text-center text-sm font-extrabold text-muted-foreground">
                      Bạn nhớ mẫu này thế nào?
                    </p>
                    <div className="grid grid-cols-4 gap-2">
                      {RATING_OPTIONS.map(({ rating, label, className }) => (
                        <button
                          key={rating}
                          type="button"
                          disabled={review.isPending}
                          onClick={() => rate(rating)}
                          className={cn(
                            'flex flex-col items-center rounded-2xl border-b-4 px-2 py-2.5 transition-all hover:brightness-105 active:translate-y-[2px] active:border-b-2 disabled:opacity-60',
                            className,
                          )}
                        >
                          <span className="text-base font-black leading-none">{label}</span>
                          <span className="mt-1 text-[11px] font-bold leading-tight opacity-90">
                            {formatInterval(current.intervals[rating - 1])}
                          </span>
                        </button>
                      ))}
                    </div>
                    <p className="mt-3 hidden text-center text-xs font-semibold text-muted-foreground sm:block">
                      Phím tắt: số 1-4 để chấm
                    </p>
                  </div>
                ) : (
                  <Button variant="secondary" size="lg" className="mt-4 w-full" onClick={() => setFlipped(true)}>
                    Hiện nghĩa
                  </Button>
                )}
              </>
            )}

            {review.isError && (
              <Alert className="mt-4">
                {extractErrorMessage(review.error)}{' '}
                <button type="button" className="font-extrabold underline" onClick={next}>
                  Bỏ qua điểm này
                </button>
              </Alert>
            )}
          </div>
        )}
      </main>

      {result && current && !isDone && (
        <div
          role="status"
          className={cn(
            'pb-safe fixed inset-x-0 bottom-0 z-30 animate-slide-up border-t-2',
            result.correct ? 'border-primary/40 bg-primary-soft' : 'border-destructive/40 bg-destructive-soft',
          )}
        >
          <div className="mx-auto flex max-w-2xl flex-col gap-4 px-4 py-5 sm:flex-row sm:items-center">
            <div className="flex flex-1 items-start gap-3">
              <span
                className={cn(
                  'flex h-12 w-12 shrink-0 animate-bounce-in items-center justify-center rounded-full bg-card',
                  result.correct ? 'text-primary' : 'text-destructive',
                )}
              >
                {result.correct ? <Check className="h-7 w-7" strokeWidth={4} /> : <X className="h-7 w-7" strokeWidth={4} />}
              </span>
              <div className="max-h-[45vh] min-w-0 flex-1 overflow-y-auto">
                <p className={cn('text-xl font-black', result.correct ? 'text-primary-dark' : 'text-destructive-dark')}>
                  {result.correct ? 'Chính xác!' : `Chưa đúng - đáp án ${result.correctOption}`}
                </p>
                <p className="text-sm font-bold text-foreground/80">
                  Ôn lại sau {formatInterval(result.intervalDays)}
                </p>
                {result.explanation && (
                  <p className="mt-1 flex items-start gap-1.5 text-sm font-semibold text-foreground/80">
                    <Lightbulb className="mt-0.5 h-4 w-4 shrink-0" strokeWidth={2.5} />
                    <span className="min-w-0 whitespace-pre-line break-words">{result.explanation}</span>
                  </p>
                )}
                <div className="mt-2">
                  <GrammarBrief card={current} />
                </div>
              </div>
            </div>
            <Button
              variant={result.correct ? 'default' : 'destructive'}
              size="lg"
              className="w-full sm:w-44"
              onClick={next}
            >
              Tiếp tục
            </Button>
          </div>
        </div>
      )}
    </>
  )
}
