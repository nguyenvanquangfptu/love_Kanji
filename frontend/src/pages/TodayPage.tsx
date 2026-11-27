import type { ReactNode } from 'react'
import { Link } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import { BookOpen, Check, ListChecks, Lock, PartyPopper, RotateCcw, ScrollText, type LucideIcon } from 'lucide-react'
import { srsApi } from '@/api/srs'
import { GRAMMAR_SESSION_KEY, grammarSrsApi } from '@/api/grammarSrs'
import { extractErrorMessage } from '@/api/client'
import { useQuizStart } from '@/lib/quizStart'
import { cn } from '@/lib/utils'
import { PageHeader } from '@/components/PageHeader'
import { StreakBadge } from '@/components/StreakBadge'
import { Button, buttonVariants } from '@/components/ui/button'
import { Card } from '@/components/ui/card'
import { Alert } from '@/components/ui/alert'
import { PageSpinner } from '@/components/ui/spinner'

/** Trắc nghiệm cuối buổi: hỏi lại chừng này từ vừa ôn. */
const QUIZ_WORDS = 10
/** Trả lời đủ chừng này câu trắc nghiệm trong ngày thì coi như xong bước trắc nghiệm. */
const QUIZ_DONE_AT = 5

interface Step {
  key: string
  icon: LucideIcon
  title: string
  detail: string
  done: boolean
  /** Chưa làm được (vd. chưa ôn thẻ nào thì chưa có từ để trắc nghiệm) - kèm lý do. */
  locked: string | null
  action: ReactNode
}

/**
 * Một buổi học trọn vẹn mỗi ngày, theo thứ tự SRS khuyên: ôn thẻ đến hạn (kèm từ mới theo kế hoạch), ôn ngữ pháp đến
 * hạn, rồi trắc nghiệm nhanh các từ vừa ôn để nhớ chắc hơn. Mỗi bước mở trang sẵn có với ?from=today - xong thì có nút
 * quay về đây làm bước sau.
 */
export function TodayPage() {
  const quiz = useQuizStart()
  const plan = useQuery({ queryKey: ['srs', 'daily-plan'], queryFn: srsApi.getDailyPlan })
  const grammar = useQuery({ queryKey: GRAMMAR_SESSION_KEY, queryFn: grammarSrsApi.session })
  const today = useQuery({ queryKey: ['srs', 'today'], queryFn: srsApi.getToday })

  const error = plan.error ?? grammar.error ?? today.error
  if (error) return <Alert>{extractErrorMessage(error)}</Alert>
  if (!plan.data || !grammar.data || !today.data) return <PageSpinner />

  const cardsLeft = plan.data.reviewsToday + plan.data.newToday
  const grammarLeft = grammar.data.cards.length
  const words = today.data.recentWords.slice(0, QUIZ_WORDS)
  const steps: Step[] = [
    {
      key: 'cards',
      icon: RotateCcw,
      title: 'Ôn thẻ từ vựng',
      detail:
        cardsLeft > 0
          ? `${plan.data.reviewsToday} thẻ ôn · ${plan.data.newToday} từ mới · khoảng ${plan.data.estimatedMinutes} phút`
          : today.data.cardsAnswered > 0
            ? `Đã chấm ${today.data.cardsAnswered} thẻ hôm nay`
            : 'Hôm nay không có thẻ nào đến hạn',
      done: cardsLeft === 0,
      locked: null,
      action: (
        <Link to="/flashcards?from=today" className={buttonVariants({ size: 'lg' })}>
          Ôn thẻ
        </Link>
      ),
    },
    {
      key: 'grammar',
      icon: ScrollText,
      title: 'Ôn ngữ pháp',
      detail: grammarLeft > 0 ? `${grammarLeft} điểm ngữ pháp đến hạn` : 'Không còn điểm nào cần ôn hôm nay',
      done: grammarLeft === 0,
      locked: null,
      action: (
        <Link to="/grammar/review?from=today" className={buttonVariants({ size: 'lg' })}>
          Ôn ngữ pháp
        </Link>
      ),
    },
    {
      key: 'quiz',
      icon: ListChecks,
      title: 'Trắc nghiệm nhanh từ vừa ôn',
      detail:
        today.data.quizAnswers >= QUIZ_DONE_AT
          ? `Đã làm ${today.data.quizAnswers} câu trắc nghiệm hôm nay`
          : `${words.length} từ vừa ôn - nhớ lại thêm một lần nữa giúp nhớ lâu hơn`,
      done: today.data.quizAnswers >= QUIZ_DONE_AT,
      locked: words.length === 0 ? 'Ôn vài thẻ trước để có từ luyện' : null,
      action: (
        <Button size="lg" onClick={() => quiz.start(`/study/quiz?kanjiIds=${words.join(',')}&from=today`)}>
          Làm trắc nghiệm
        </Button>
      ),
    },
  ]
  const nextStep = steps.find((step) => !step.done && step.locked === null)
  const allDone = steps.every((step) => step.done || step.locked !== null) && nextStep === undefined
  const doneCount = steps.filter((step) => step.done).length

  return (
    <div className="mx-auto flex max-w-2xl flex-col gap-5">
      {quiz.dialog}
      <PageHeader
        title="Học hôm nay"
        subtitle={`Ôn thẻ, ôn ngữ pháp, rồi luyện lại từ vừa ôn · xong ${doneCount}/${steps.length} bước`}
        action={<StreakBadge />}
      />

      {allDone && (
        <Card className="flex flex-col items-center gap-3 p-6 text-center">
          <span className="flex h-16 w-16 animate-bounce-in items-center justify-center rounded-full bg-accent-soft text-accent-dark">
            <PartyPopper className="h-8 w-8" strokeWidth={2.5} />
          </span>
          <h2 className="text-xl font-black">Xong buổi học hôm nay!</h2>
          <p className="text-sm font-semibold text-muted-foreground">
            Mai quay lại để giữ chuỗi ngày học. Còn thời gian thì học thêm bài mới.
          </p>
          <Link to="/study" className={buttonVariants({ variant: 'outline' })}>
            <BookOpen className="h-5 w-5" /> Học bài mới
          </Link>
        </Card>
      )}

      <ol className="flex flex-col gap-3">
        {steps.map((step, i) => {
          const current = step === nextStep
          const Icon = step.done ? Check : step.locked ? Lock : step.icon
          return (
            <li key={step.key}>
              <Card
                className={cn(
                  'flex flex-col gap-3 p-4 sm:flex-row sm:items-center',
                  current && 'border-primary ring-4 ring-primary/15',
                  step.done && 'opacity-80',
                )}
              >
                <div className="flex flex-1 items-center gap-3">
                  <span
                    className={cn(
                      'flex h-11 w-11 shrink-0 items-center justify-center rounded-full',
                      step.done ? 'bg-primary text-primary-foreground' : 'bg-muted text-muted-foreground',
                      current && 'bg-primary-soft text-primary-dark',
                    )}
                  >
                    <Icon className="h-5 w-5" strokeWidth={2.75} />
                  </span>
                  <div className="min-w-0">
                    <p className="text-xs font-extrabold uppercase tracking-wider text-muted-foreground">Bước {i + 1}</p>
                    <h2 className="font-black">{step.title}</h2>
                    <p className="text-sm font-semibold text-muted-foreground">{step.locked ?? step.detail}</p>
                  </div>
                </div>
                {!step.done && step.locked === null && <div className="shrink-0">{step.action}</div>}
              </Card>
            </li>
          )
        })}
      </ol>
    </div>
  )
}
