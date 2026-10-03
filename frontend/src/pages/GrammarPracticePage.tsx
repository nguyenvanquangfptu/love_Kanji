import { useEffect, useState } from 'react'
import { Link, Navigate, useSearchParams } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import { Check, Lightbulb, RotateCcw, Target, Trophy, X } from 'lucide-react'
import { examApi } from '@/api/exam'
import { extractErrorMessage } from '@/api/client'
import type { PracticeQuestion } from '@/api/types'
import { QUESTION_TYPE_META } from '@/lib/jlpt'
import { cn } from '@/lib/utils'
import { SessionHeader } from '@/components/SessionHeader'
import { SentenceWithTarget } from '@/components/SentenceWithTarget'
import { StatTile } from '@/components/StatTile'
import { EmptyState } from '@/components/EmptyState'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Card } from '@/components/ui/card'
import { Alert } from '@/components/ui/alert'
import { PageSpinner } from '@/components/ui/spinner'

const LETTERS = ['A', 'B', 'C', 'D'] as const

/** Ghi lựa chọn cho câu thứ index; mỗi câu chỉ chọn được một lần. */
const answerAt = (index: number, choice: number) => (answers: number[]) =>
  answers.length === index ? [...answers, choice] : answers

/** Lựa chọn (0-3) ứng với phím 1-4 hoặc A-D; -1 với phím khác. */
function choiceOfKey(key: string) {
  if (/^[1-4]$/.test(key)) return Number(key) - 1
  return LETTERS.indexOf(key.toUpperCase() as (typeof LETTERS)[number])
}

/**
 * Luyện lại các điểm ngữ pháp hay sai (mở từ trang buổi thi JLPT): câu đã duyệt của các điểm đó, chấm ngay từng câu kèm
 * giải thích, không tính giờ, không lưu kết quả. ?level=N4&points=1,2,3&sitting=<id để quay về>.
 */
export function GrammarPracticePage() {
  const [params] = useSearchParams()
  const level = params.get('level') ?? ''
  const points = (params.get('points') ?? '')
    .split(',')
    .filter((id) => /^\d+$/.test(id))
    .map(Number)
  const sittingId = params.get('sitting')
  const exitTo = sittingId && /^\d+$/.test(sittingId) ? `/exam/jlpt/${sittingId}` : '/exam'

  const [round, setRound] = useState(0)
  const [index, setIndex] = useState(0)
  /** Lựa chọn của các câu đã làm, theo thứ tự. */
  const [answers, setAnswers] = useState<number[]>([])

  const { data: questions, isLoading, isError, error } = useQuery({
    queryKey: ['grammar-practice', level, points.join(','), round],
    queryFn: () => examApi.grammarPractice(level, points),
    enabled: level !== '' && points.length > 0,
    staleTime: Infinity,
  })

  const total = questions?.length ?? 0
  const current: PracticeQuestion | undefined = questions?.[index]
  const selected = index < answers.length ? answers[index] : null
  const answered = selected !== null
  const isDone = total > 0 && index >= total
  const correctIndex = current ? LETTERS.indexOf(current.correctOption) : -1
  const score = answers.filter(
    (choice, i) => questions?.[i] !== undefined && choice === LETTERS.indexOf(questions[i].correctOption),
  ).length

  useEffect(() => {
    if (!current || isDone) return
    function onKeyDown(e: KeyboardEvent) {
      const choice = choiceOfKey(e.key)
      if (!answered && choice >= 0) {
        setAnswers(answerAt(index, choice))
      } else if (answered && e.key === 'Enter' && !(e.target instanceof HTMLButtonElement)) {
        setIndex((i) => i + 1)
      }
    }
    window.addEventListener('keydown', onKeyDown)
    return () => window.removeEventListener('keydown', onKeyDown)
  }, [current, isDone, answered, index])

  function restart() {
    setIndex(0)
    setAnswers([])
    setRound((r) => r + 1)
  }

  if (level === '' || points.length === 0) return <Navigate to="/exam" replace />

  const progress = total > 0 ? ((index + (answered ? 1 : 0)) / total) * 100 : 0

  return (
    <>
      <SessionHeader exitTo={exitTo} progress={isDone ? 100 : progress} />

      <main className="mx-auto max-w-2xl px-4 pb-72 sm:pb-52">
        {isLoading && <PageSpinner label="Đang lấy câu luyện..." />}
        {isError && <Alert>{extractErrorMessage(error)}</Alert>}
        {questions && total === 0 && (
          <EmptyState
            icon={Target}
            title="Chưa có câu luyện"
            description={`Chưa có câu ${level} đã duyệt nào cho các điểm ngữ pháp này.`}
          />
        )}

        {isDone && (
          <div className="flex flex-col items-center pt-6 text-center">
            <span className="flex h-24 w-24 animate-bounce-in items-center justify-center rounded-full bg-accent-soft text-accent-dark">
              <Trophy className="h-12 w-12" strokeWidth={2.5} />
            </span>
            <h1 className="mt-4 text-3xl font-black">Đã luyện xong!</h1>
            <p className="mt-1 text-muted-foreground">Kết quả luyện tập không tính vào điểm thi.</p>
            <div className="mt-6 grid w-full grid-cols-2 gap-3">
              <StatTile label="Câu đúng" value={`${score}/${total}`} tone="primary" icon={Check} />
              <StatTile label="Chính xác" value={`${Math.round((score / total) * 100)}%`} tone="secondary" icon={Target} />
            </div>
            <div className="mt-8 flex w-full flex-col gap-3 sm:flex-row">
              <Button size="lg" className="flex-1" onClick={restart}>
                <RotateCcw className="h-5 w-5" /> Luyện bộ câu khác
              </Button>
            </div>
            <Link to={exitTo} className="mt-4 text-sm font-extrabold text-secondary hover:underline">
              {sittingId ? 'Về buổi thi' : 'Về trang thi thử'}
            </Link>
          </div>
        )}

        {current && !isDone && (
          <div key={current.id} className="animate-pop-in">
            <p className="text-sm font-bold text-muted-foreground">
              Câu {index + 1}/{total} ·{' '}
              <span className="font-jp">{QUESTION_TYPE_META[current.questionType]?.jp ?? current.questionType}</span>
            </p>
            <h1 className="mt-1 text-xl font-black sm:text-2xl">{current.questionText}</h1>

            {current.sentence && (
              <Card className="mt-6 p-5 sm:p-6">
                <p className="font-jp text-xl leading-[2.4] sm:text-2xl sm:leading-[2.4]">
                  <SentenceWithTarget sentence={current.sentence} target={current.highlight} />
                </p>
              </Card>
            )}

            <div className="mt-8 grid gap-3 sm:grid-cols-2">
              {LETTERS.map((letter, i) => {
                const isCorrectChoice = i === correctIndex
                const isSelected = i === selected
                return (
                  <button
                    key={letter}
                    type="button"
                    disabled={answered}
                    onClick={() => setAnswers(answerAt(index, i))}
                    className={cn(
                      'flex min-h-16 items-center gap-3 rounded-2xl border-2 border-b-4 px-4 py-3 text-left font-bold transition-all disabled:cursor-default',
                      !answered && 'border-border bg-card hover:bg-muted active:translate-y-[2px] active:border-b-2',
                      answered && isCorrectChoice && 'border-primary bg-primary-soft text-primary-dark',
                      answered && isSelected && !isCorrectChoice && 'border-destructive bg-destructive-soft text-destructive-dark',
                      answered && !isCorrectChoice && !isSelected && 'border-border bg-card opacity-50',
                    )}
                  >
                    <span className="flex h-8 w-8 shrink-0 items-center justify-center rounded-lg border-2 border-current text-sm font-black">
                      {letter}
                    </span>
                    <span className="font-jp text-lg leading-snug">{current[`option${letter}`]}</span>
                  </button>
                )
              })}
            </div>
          </div>
        )}
      </main>

      {answered && current && !isDone && (
        <div
          role="status"
          className={cn(
            'pb-safe fixed inset-x-0 bottom-0 z-30 animate-slide-up border-t-2',
            selected === correctIndex ? 'border-primary/40 bg-primary-soft' : 'border-destructive/40 bg-destructive-soft',
          )}
        >
          <div className="mx-auto flex max-w-2xl flex-col gap-4 px-4 py-5 sm:flex-row sm:items-center">
            <div className="flex flex-1 items-start gap-3">
              <span
                className={cn(
                  'flex h-12 w-12 shrink-0 animate-bounce-in items-center justify-center rounded-full bg-white',
                  selected === correctIndex ? 'text-primary' : 'text-destructive',
                )}
              >
                {selected === correctIndex ? (
                  <Check className="h-7 w-7" strokeWidth={4} />
                ) : (
                  <X className="h-7 w-7" strokeWidth={4} />
                )}
              </span>
              <div className="min-w-0 flex-1">
                <p
                  className={cn(
                    'text-xl font-black',
                    selected === correctIndex ? 'text-primary-dark' : 'text-destructive-dark',
                  )}
                >
                  {selected === correctIndex ? 'Chính xác!' : `Chưa đúng - đáp án ${current.correctOption}`}
                </p>
                {current.explanation && (
                  <p className="mt-1 flex items-start gap-1.5 text-sm font-semibold text-foreground/80">
                    <Lightbulb className="mt-0.5 h-4 w-4 shrink-0" strokeWidth={2.5} />
                    {current.explanation}
                  </p>
                )}
                {current.grammarPoints.length > 0 && (
                  <div className="mt-2 flex flex-wrap gap-1.5">
                    {current.grammarPoints.map((point) => (
                      <Badge key={point.id} variant="purple" className="font-jp" title={point.meaningVi}>
                        {point.pattern}
                      </Badge>
                    ))}
                  </div>
                )}
              </div>
            </div>
            <Button
              variant={selected === correctIndex ? 'default' : 'destructive'}
              size="lg"
              className="w-full sm:w-44"
              onClick={() => setIndex((i) => i + 1)}
            >
              Tiếp tục
            </Button>
          </div>
        </div>
      )}
    </>
  )
}
