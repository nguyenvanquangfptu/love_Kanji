import { useCallback, useEffect, useState } from 'react'
import { Link, Navigate, useNavigate } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import { Check, CheckCircle2, Flame, Layers, Plus, RotateCcw, Target, Trophy, X } from 'lucide-react'
import { quizApi } from '@/api/quiz'
import { extractErrorMessage } from '@/api/client'
import type { QuizDirection, QuizQuestionResponse } from '@/api/types'
import { useLessonParams } from '@/lib/lesson'
import { describeAddResult, useAddToReview } from '@/lib/review'
import { cn } from '@/lib/utils'
import { SessionHeader } from '@/components/SessionHeader'
import { SpeakButton } from '@/components/SpeakButton'
import { StatTile } from '@/components/StatTile'
import { EmptyState } from '@/components/EmptyState'
import { Button } from '@/components/ui/button'
import { Card } from '@/components/ui/card'
import { Alert } from '@/components/ui/alert'
import { PageSpinner } from '@/components/ui/spinner'

const INSTRUCTIONS: Record<QuizDirection, { withSentence: string; standalone: string }> = {
  KANJI_TO_READING: {
    withSentence: 'Chọn cách đọc đúng của từ được gạch chân',
    standalone: 'Từ này đọc là gì?',
  },
  READING_TO_KANJI: {
    withSentence: 'Chọn cách viết Kanji đúng của từ được gạch chân',
    standalone: 'Chọn cách viết đúng của từ này',
  },
  MEANING: {
    withSentence: 'Từ được gạch chân có nghĩa là gì?',
    standalone: 'Từ này có nghĩa là gì?',
  },
}

const CHOICE_TEXT: Record<QuizDirection, string> = {
  KANJI_TO_READING: 'font-jp text-lg',
  READING_TO_KANJI: 'font-jp text-2xl',
  MEANING: 'text-base',
}

const PRAISES = ['Chính xác!', 'Tuyệt vời!', 'Giỏi lắm!', 'Xuất sắc!', 'Quá đỉnh!']

function SentenceWithTarget({ sentence, target }: { sentence: string; target: string }) {
  const at = sentence.indexOf(target)
  if (at < 0) return <>{sentence}</>
  return (
    <>
      {sentence.slice(0, at)}
      <span className="rounded-md bg-accent-soft px-1 font-bold underline decoration-accent-dark decoration-[3px] underline-offset-[7px]">
        {target}
      </span>
      {sentence.slice(at + target.length)}
    </>
  )
}

export function StudyQuizPage() {
  const navigate = useNavigate()
  const { tagId, query } = useLessonParams()

  const [index, setIndex] = useState(0)
  const [selected, setSelected] = useState<number | null>(null)
  const [score, setScore] = useState(0)
  const [streak, setStreak] = useState(0)
  const [bestStreak, setBestStreak] = useState(0)
  const [mistakes, setMistakes] = useState<QuizQuestionResponse[]>([])
  const [attempt, setAttempt] = useState(0)

  const { data: questions, isLoading, isError, error } = useQuery({
    queryKey: ['study-quiz', tagId, attempt],
    queryFn: () => quizApi.generate({ tagId: tagId ?? undefined, size: 10 }),
    enabled: tagId !== null,
    staleTime: Infinity,
    gcTime: 0,
  })

  const total = questions?.length ?? 0
  const current = questions?.[index]
  const isDone = total > 0 && index >= total
  const answered = selected !== null

  const choose = useCallback(
    (choiceIndex: number) => {
      if (!current || selected !== null || choiceIndex >= current.choices.length) return
      setSelected(choiceIndex)
      if (choiceIndex === current.correctIndex) {
        const nextStreak = streak + 1
        setScore((s) => s + 1)
        setStreak(nextStreak)
        setBestStreak((best) => Math.max(best, nextStreak))
      } else {
        setStreak(0)
        setMistakes((list) => [...list, current])
      }
    },
    [current, selected, streak],
  )

  const next = useCallback(() => {
    setSelected(null)
    setIndex((i) => i + 1)
  }, [])

  useEffect(() => {
    if (!current || isDone) return
    function onKeyDown(e: KeyboardEvent) {
      if (!answered && /^[1-4]$/.test(e.key)) {
        choose(Number(e.key) - 1)
      } else if (answered && e.key === 'Enter' && !(e.target instanceof HTMLButtonElement)) {
        next()
      }
    }
    window.addEventListener('keydown', onKeyDown)
    return () => window.removeEventListener('keydown', onKeyDown)
  }, [current, isDone, answered, choose, next])

  function restart() {
    setIndex(0)
    setSelected(null)
    setScore(0)
    setStreak(0)
    setBestStreak(0)
    setMistakes([])
    setAttempt((a) => a + 1)
  }

  if (tagId === null) return <Navigate to="/study" replace />

  const exitTo = `/study/vocab?${query}`
  const progress = total > 0 ? ((index + (answered ? 1 : 0)) / total) * 100 : 0
  const isCorrect = answered && current !== undefined && selected === current.correctIndex

  return (
    <>
      <SessionHeader
        exitTo={exitTo}
        progress={isDone ? 100 : progress}
        right={
          streak >= 2 && !isDone ? (
            <span className="flex items-center gap-1 text-lg font-black text-orange" title="Chuỗi trả lời đúng">
              <Flame className="h-6 w-6 fill-orange" />
              {streak}
            </span>
          ) : null
        }
      />

      <main className="mx-auto max-w-2xl px-4 pb-64 sm:pb-44">
        {isLoading && <PageSpinner label="Đang soạn câu hỏi..." />}
        {isError && <Alert>{extractErrorMessage(error)}</Alert>}
        {questions && total === 0 && (
          <EmptyState icon={Target} title="Chưa tạo được câu hỏi" description="Bài này chưa đủ từ vựng để làm trắc nghiệm." />
        )}

        {isDone && (
          <QuizResults
            score={score}
            total={total}
            bestStreak={bestStreak}
            mistakes={mistakes}
            onRestart={restart}
            onFlashcards={() => navigate(`/study/flashcards?${query}`)}
            exitTo={exitTo}
          />
        )}

        {current && !isDone && (
          <div key={index} className="animate-pop-in">
            <h1 className="text-xl font-black sm:text-2xl">
              {current.sentence
                ? INSTRUCTIONS[current.direction].withSentence
                : INSTRUCTIONS[current.direction].standalone}
            </h1>

            <div className="mt-6">
              {current.sentence ? (
                <Card className="p-5 sm:p-6">
                  <p className="font-jp text-xl leading-[2.4] sm:text-2xl sm:leading-[2.4]">
                    <SentenceWithTarget sentence={current.sentence} target={current.prompt} />
                  </p>
                </Card>
              ) : (
                <div className="flex justify-center rounded-3xl border-2 border-dashed border-border py-8">
                  <span className="font-jp text-5xl font-bold sm:text-6xl">{current.prompt}</span>
                </div>
              )}
            </div>

            <div className="mt-8 grid gap-3 sm:grid-cols-2">
              {current.choices.map((choice, i) => {
                const isCorrectChoice = i === current.correctIndex
                const isSelected = i === selected
                return (
                  <button
                    key={i}
                    type="button"
                    disabled={answered}
                    onClick={() => choose(i)}
                    className={cn(
                      'flex min-h-16 items-center gap-3 rounded-2xl border-2 border-b-4 px-4 py-3 text-left font-bold transition-all disabled:cursor-default',
                      !answered && 'border-border bg-card hover:bg-muted active:translate-y-[2px] active:border-b-2',
                      answered && isCorrectChoice && 'border-primary bg-primary-soft text-primary-dark',
                      answered && isSelected && !isCorrectChoice && 'border-destructive bg-destructive-soft text-destructive-dark',
                      answered && !isCorrectChoice && !isSelected && 'border-border bg-card opacity-50',
                    )}
                  >
                    <span
                      className={cn(
                        'flex h-8 w-8 shrink-0 items-center justify-center rounded-lg border-2 text-sm font-black',
                        answered && isCorrectChoice
                          ? 'border-primary text-primary-dark'
                          : answered && isSelected
                            ? 'border-destructive text-destructive-dark'
                            : 'border-border text-muted-foreground',
                      )}
                    >
                      {i + 1}
                    </span>
                    <span className={cn('leading-snug', CHOICE_TEXT[current.direction])}>{choice}</span>
                  </button>
                )
              })}
            </div>
          </div>
        )}
      </main>

      {answered && current && !isDone && (
        <FeedbackBar correct={isCorrect} question={current} praise={PRAISES[index % PRAISES.length]} onContinue={next} />
      )}
    </>
  )
}

function FeedbackBar({
  correct,
  question,
  praise,
  onContinue,
}: {
  correct: boolean
  question: QuizQuestionResponse
  praise: string
  onContinue: () => void
}) {
  const correctAnswer = question.choices[question.correctIndex]
  return (
    <div
      role="status"
      className={cn(
        'pb-safe fixed inset-x-0 bottom-0 z-30 animate-slide-up border-t-2',
        correct ? 'border-primary/40 bg-primary-soft' : 'border-destructive/40 bg-destructive-soft',
      )}
    >
      <div className="mx-auto flex max-w-2xl flex-col gap-4 px-4 py-5 sm:flex-row sm:items-center">
        <div className="flex flex-1 items-start gap-3">
          <span
            className={cn(
              'flex h-12 w-12 shrink-0 animate-bounce-in items-center justify-center rounded-full bg-white',
              correct ? 'text-primary' : 'text-destructive',
            )}
          >
            {correct ? <Check className="h-7 w-7" strokeWidth={4} /> : <X className="h-7 w-7" strokeWidth={4} />}
          </span>
          <div className="min-w-0 flex-1">
            <p className={cn('text-xl font-black', correct ? 'text-primary-dark' : 'text-destructive-dark')}>
              {correct ? praise : 'Chưa đúng rồi!'}
            </p>
            {!correct && (
              <p className="font-bold text-destructive-dark">
                Đáp án: <span className={cn(question.direction !== 'MEANING' && 'font-jp')}>{correctAnswer}</span>
              </p>
            )}
            <p className="mt-0.5 text-sm font-semibold text-foreground/80">
              <span className="font-jp font-bold">{question.character}</span>
              {question.reading && <span className="font-jp">（{question.reading}）</span>} — {question.meaning}
            </p>
          </div>
          <SpeakButton text={question.reading ?? question.character} />
        </div>
        <Button variant={correct ? 'default' : 'destructive'} size="lg" className="w-full sm:w-44" onClick={onContinue}>
          Tiếp tục
        </Button>
      </div>
    </div>
  )
}

function QuizResults({
  score,
  total,
  bestStreak,
  mistakes,
  onRestart,
  onFlashcards,
  exitTo,
}: {
  score: number
  total: number
  bestStreak: number
  mistakes: QuizQuestionResponse[]
  onRestart: () => void
  onFlashcards: () => void
  exitTo: string
}) {
  const percent = Math.round((score / total) * 100)
  const title =
    percent === 100 ? 'Hoàn hảo!' : percent >= 80 ? 'Xuất sắc!' : percent >= 50 ? 'Làm tốt lắm!' : 'Cố lên nhé!'
  const uniqueMistakes = mistakes.filter((m, i) => mistakes.findIndex((x) => x.kanjiId === m.kanjiId) === i)
  const addToReview = useAddToReview()

  return (
    <div className="flex flex-col items-center pt-6 text-center">
      <span className="flex h-24 w-24 animate-bounce-in items-center justify-center rounded-full bg-accent-soft text-accent-dark">
        <Trophy className="h-12 w-12" strokeWidth={2.5} />
      </span>
      <h1 className="mt-4 text-3xl font-black">{title}</h1>
      <p className="mt-1 text-muted-foreground">Bạn đã hoàn thành bài trắc nghiệm</p>

      <div className="mt-6 grid w-full grid-cols-3 gap-3">
        <StatTile label="Câu đúng" value={`${score}/${total}`} tone="primary" icon={Check} />
        <StatTile label="Chính xác" value={`${percent}%`} tone="secondary" icon={Target} />
        <StatTile label="Chuỗi đúng" value={bestStreak} tone="orange" icon={Flame} />
      </div>

      {uniqueMistakes.length > 0 && (
        <div className="mt-8 w-full text-left">
          <div className="mb-3 flex flex-wrap items-center justify-between gap-2">
            <h2 className="text-lg font-black">Từ cần ôn lại ({uniqueMistakes.length})</h2>
            {addToReview.isSuccess ? (
              <span className="flex items-center gap-1.5 text-sm font-bold text-primary-dark">
                <CheckCircle2 className="h-4 w-4 shrink-0" />
                {describeAddResult(addToReview.data)}
              </span>
            ) : (
              <Button
                size="sm"
                variant="secondary"
                disabled={addToReview.isPending}
                onClick={() => addToReview.mutate(uniqueMistakes.map((m) => m.kanjiId))}
              >
                <Plus className="h-4 w-4" strokeWidth={3} />
                {addToReview.isPending ? 'Đang thêm...' : 'Thêm vào Ôn tập'}
              </Button>
            )}
          </div>
          {addToReview.isError && <Alert className="mb-3">{extractErrorMessage(addToReview.error)}</Alert>}
          <div className="flex flex-col gap-2">
            {uniqueMistakes.map((m) => (
              <Card key={m.kanjiId} className="flex items-center gap-3 p-3">
                <div className="flex min-w-[4rem] shrink-0 flex-col items-center">
                  {m.reading && <span className="font-jp text-xs font-bold text-secondary-dark">{m.reading}</span>}
                  <span className="font-jp text-2xl font-bold">{m.character}</span>
                </div>
                <p className="flex-1 text-sm font-semibold">{m.meaning}</p>
                <SpeakButton text={m.reading ?? m.character} />
              </Card>
            ))}
          </div>
        </div>
      )}

      <div className="mt-8 flex w-full flex-col gap-3 sm:flex-row">
        <Button size="lg" className="flex-1" onClick={onRestart}>
          <RotateCcw className="h-5 w-5" /> Làm bộ câu hỏi mới
        </Button>
        <Button variant="outline" size="lg" className="flex-1" onClick={onFlashcards}>
          <Layers className="h-5 w-5" /> Ôn bằng thẻ
        </Button>
      </div>
      <Link to={exitTo} className="mt-5 text-sm font-extrabold text-secondary hover:underline">
        Về bài học
      </Link>
    </div>
  )
}
