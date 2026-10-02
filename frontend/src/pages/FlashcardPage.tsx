import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { BookOpen, Brain, BrainCircuit, CalendarCheck, ChevronRight, Clock, Dumbbell, PartyPopper, RotateCcw, Sprout } from 'lucide-react'
import { srsApi } from '@/api/srs'
import type { DailyCardResponse, DailyPlanResponse, Page, ReviewRating, ReviewRequest } from '@/api/types'
import { extractErrorMessage } from '@/api/client'
import { cn, wordSizeClass } from '@/lib/utils'
import { FlipCard } from '@/components/FlipCard'
import { PageHeader } from '@/components/PageHeader'
import { StatTile } from '@/components/StatTile'
import { EmptyState } from '@/components/EmptyState'
import { SpeakButton } from '@/components/SpeakButton'
import { MemoryAidPanel } from '@/components/MemoryAidPanel'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Alert } from '@/components/ui/alert'
import { Progress } from '@/components/ui/progress'
import { PageSpinner } from '@/components/ui/spinner'

const DAILY_CARDS_KEY = ['srs', 'daily-cards']
const STATS_KEY = ['srs', 'stats']
const DAILY_PLAN_KEY = ['srs', 'daily-plan']

const RATING_OPTIONS: { rating: ReviewRating; label: string; hint: string; className: string }[] = [
  { rating: 1, label: 'Quên', hint: 'Học lại', className: 'border-destructive-dark bg-destructive text-white' },
  { rating: 2, label: 'Khó', hint: 'Nghĩ lâu mới ra', className: 'border-orange-dark bg-orange text-white' },
  { rating: 3, label: 'Nhớ', hint: 'Nhớ ra được', className: 'border-secondary-dark bg-secondary text-white' },
  { rating: 4, label: 'Dễ', hint: 'Nhớ ngay', className: 'border-primary-dark bg-primary text-white' },
]

export function FlashcardPage() {
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const [flipped, setFlipped] = useState(false)
  // Tổng số thẻ của phiên ôn hôm nay - chốt lại ở lần fetch thành công đầu tiên,
  // không đổi theo các đợt review tiếp theo (queue sẽ co dần qua optimistic update).
  const [sessionTotal, setSessionTotal] = useState<number | null>(null)
  // Ôn thêm ngoài kế hoạch hôm nay: lấy mọi thẻ đến hạn, kể cả phần để dành cho những ngày sau.
  const [extra, setExtra] = useState(false)
  const cardsKey = useMemo(() => [...DAILY_CARDS_KEY, extra], [extra])

  const { data, isLoading, isError, error } = useQuery({
    queryKey: cardsKey,
    queryFn: () => srsApi.getDailyCards({ size: 100, extra }),
  })

  const { data: stats } = useQuery({ queryKey: STATS_KEY, queryFn: srsApi.getStats })
  const { data: plan } = useQuery({ queryKey: DAILY_PLAN_KEY, queryFn: srsApi.getDailyPlan })

  const queue = data?.content ?? []
  const current = queue[0]

  useEffect(() => {
    if (data && sessionTotal === null) setSessionTotal(data.content.length)
  }, [data, sessionTotal])

  // Thời gian nhớ lại = từ lúc hiện thẻ tới lần lật đầu tiên (lật đi lật lại sau đó không tính).
  const shownAt = useRef(0)
  const recallMs = useRef<number | null>(null)
  const currentId = current?.kanji.id
  useEffect(() => {
    shownAt.current = performance.now()
    recallMs.current = null
  }, [currentId])

  const toggleFlip = useCallback(() => {
    if (recallMs.current === null) recallMs.current = Math.round(performance.now() - shownAt.current)
    setFlipped((f) => !f)
  }, [])

  const reviewMutation = useMutation({
    mutationFn: (vars: ReviewRequest) => srsApi.submitReview(vars),
    onMutate: async (vars) => {
      await queryClient.cancelQueries({ queryKey: cardsKey })
      const previous = queryClient.getQueryData<Page<DailyCardResponse>>(cardsKey)
      queryClient.setQueryData<Page<DailyCardResponse> | undefined>(cardsKey, (old) =>
        old ? { ...old, content: old.content.filter((c) => c.kanji.id !== vars.kanjiId) } : old,
      )
      setFlipped(false)
      return { previous }
    },
    onError: (_err, _vars, context) => {
      if (context?.previous) queryClient.setQueryData(cardsKey, context.previous)
    },
    onSettled: () => {
      queryClient.invalidateQueries({ queryKey: STATS_KEY })
      queryClient.invalidateQueries({ queryKey: DAILY_PLAN_KEY })
    },
  })

  function studyExtra() {
    setSessionTotal(null)
    if (extra) queryClient.invalidateQueries({ queryKey: cardsKey })
    else setExtra(true)
  }

  const rate = useMemo(
    () => (rating: ReviewRating) => {
      if (!current || reviewMutation.isPending) return
      reviewMutation.mutate({ kanjiId: current.kanji.id, rating, responseMs: recallMs.current ?? undefined })
    },
    [current, reviewMutation],
  )

  useEffect(() => {
    function onKeyDown(e: KeyboardEvent) {
      if (!current) return
      // Đang gõ ghi chú cách nhớ: phím số và Space là chữ, không phải phím tắt.
      if (e.target instanceof HTMLTextAreaElement || e.target instanceof HTMLInputElement) return
      if (e.code === 'Space') {
        e.preventDefault()
        toggleFlip()
        return
      }
      if (flipped && /^[1-4]$/.test(e.key)) {
        rate(Number(e.key) as ReviewRating)
      }
    }
    window.addEventListener('keydown', onKeyDown)
    return () => window.removeEventListener('keydown', onKeyDown)
  }, [current, flipped, rate, toggleFlip])

  const doneCount = sessionTotal !== null ? sessionTotal - queue.length : 0
  // Thẻ đến hạn và từ mới đang chờ mà phiên hôm nay chưa lấy (để dành cho những ngày sau).
  const leftForLater = plan ? plan.dueReviews + plan.newWaiting : 0

  return (
    <div>
      <PageHeader title="Ôn tập hôm nay" subtitle="Ôn lại đúng lúc sắp quên để nhớ lâu hơn." />

      {plan && !extra && stats && stats.totalCardsStarted > 0 && <TodayPlan plan={plan} />}

      {stats && (
        <div className="mb-6 grid grid-cols-3 gap-3">
          <StatTile label="Cần ôn" value={stats.dueForReview} tone="orange" icon={Clock} />
          <StatTile label="Đang học" value={stats.stillLearning} tone="secondary" icon={Sprout} />
          <StatTile label="Đã thuộc" value={stats.deeplyMemorized} tone="primary" icon={Brain} />
        </div>
      )}

      {stats && stats.hardWords > 0 && (
        <Link
          to="/flashcards/hard-words"
          className="mb-6 flex items-center gap-3 rounded-2xl border-2 border-destructive/30 bg-destructive-soft px-4 py-3 font-bold text-destructive-dark transition-all hover:brightness-[0.98]"
        >
          <Dumbbell className="h-5 w-5 shrink-0" strokeWidth={2.5} />
          <span className="flex-1">Bạn có {stats.hardWords} từ khó - xem và luyện riêng</span>
          <ChevronRight className="h-5 w-5 shrink-0" />
        </Link>
      )}

      {isLoading && <PageSpinner />}
      {isError && <Alert>{extractErrorMessage(error)}</Alert>}

      {data && !current && stats?.totalCardsStarted === 0 && (
        <EmptyState
          icon={BrainCircuit}
          title="Chưa có từ nào trong Ôn tập"
          description="Mở một bài học rồi bấm “Thêm bài vào Ôn tập”, hoặc làm trắc nghiệm - từ làm sai sẽ tự vào đây. App sẽ nhắc bạn ôn lại đúng lúc sắp quên."
          action={
            <Button size="lg" onClick={() => navigate('/study')}>
              <BookOpen className="h-5 w-5" /> Chọn bài học
            </Button>
          }
        />
      )}

      {data && !current && stats?.totalCardsStarted !== 0 && (
        <EmptyState
          icon={PartyPopper}
          iconClassName="bg-accent-soft text-accent-dark"
          title="Đã ôn hết thẻ hôm nay!"
          description={
            leftForLater > 0
              ? `Bạn đã xong phần của hôm nay. Còn ${leftForLater} thẻ để dành cho những ngày sau - ôn thêm nếu bạn còn thời gian.`
              : 'Quay lại vào ngày mai để giữ chuỗi ôn tập, hoặc học thêm bài mới ngay bây giờ.'
          }
          action={
            <div className="flex flex-col gap-3 sm:flex-row">
              {leftForLater > 0 && (
                <Button size="lg" onClick={studyExtra}>
                  <RotateCcw className="h-5 w-5" /> Ôn thêm
                </Button>
              )}
              <Button size="lg" variant={leftForLater > 0 ? 'outline' : 'default'} onClick={() => navigate('/study')}>
                <BookOpen className="h-5 w-5" /> Học bài mới
              </Button>
            </div>
          }
        />
      )}

      {current && (
        <div className="mx-auto flex max-w-xl flex-col gap-5">
          {sessionTotal !== null && sessionTotal > 0 && (
            <div className="flex items-center gap-3">
              <Progress value={(doneCount / sessionTotal) * 100} className="flex-1" />
              <span className="text-sm font-extrabold text-muted-foreground">
                {doneCount}/{sessionTotal}
              </span>
            </div>
          )}

          <FlipCard
            flipped={flipped}
            onClick={toggleFlip}
            front={
              <>
                <span className={cn('font-jp font-bold leading-none', wordSizeClass(current.kanji.character))}>
                  {current.kanji.character}
                </span>
                <div className="mt-6 flex flex-wrap justify-center gap-2">
                  <Badge variant="secondary">{current.kanji.jlptLevel}</Badge>
                  {current.newCard && <Badge variant="accent">Từ mới</Badge>}
                  {current.hardWord && <Badge variant="destructive">Từ khó · quên {current.lapseCount} lần</Badge>}
                </div>
                <p className="absolute bottom-5 text-xs font-bold text-muted-foreground">Nhớ nghĩa rồi thì chạm để lật</p>
              </>
            }
            back={
              <div className="flex w-full flex-col items-center gap-2 text-center">
                {current.kanji.reading && (
                  <p className="font-jp text-xl font-bold text-secondary-dark">{current.kanji.reading}</p>
                )}
                <p className="font-jp text-5xl font-bold">{current.kanji.character}</p>
                {current.kanji.hanViet && (
                  <p className="text-sm font-extrabold uppercase tracking-wide text-muted-foreground">
                    {current.kanji.hanViet}
                  </p>
                )}
                <p className="mt-2 text-lg font-bold leading-snug">{current.kanji.meaning}</p>
                <SpeakButton text={current.kanji.reading ?? current.kanji.character} className="mt-1" />
              </div>
            }
          />

          {flipped ? (
            <div className="animate-pop-in">
              {(current.hardWord || current.kanji.mnemonic || current.personalNote) && (
                <MemoryAidPanel
                  key={current.kanji.id}
                  kanjiId={current.kanji.id}
                  mnemonic={current.kanji.mnemonic}
                  personalNote={current.personalNote}
                  className="mb-4"
                />
              )}
              <p className="mb-3 text-center text-sm font-extrabold text-muted-foreground">Bạn nhớ từ này thế nào?</p>
              <div className="grid grid-cols-4 gap-2">
                {RATING_OPTIONS.map(({ rating, label, hint, className }) => (
                  <button
                    key={rating}
                    type="button"
                    disabled={reviewMutation.isPending}
                    onClick={() => rate(rating)}
                    className={cn(
                      'flex flex-col items-center rounded-2xl border-b-4 px-2 py-2.5 transition-all hover:brightness-105 active:translate-y-[2px] active:border-b-2 disabled:opacity-60',
                      className,
                    )}
                  >
                    <span className="text-base font-black leading-none">{label}</span>
                    <span className="mt-1 text-[11px] font-bold leading-tight opacity-90">{hint}</span>
                  </button>
                ))}
              </div>
              <p className="mt-3 hidden text-center text-xs font-semibold text-muted-foreground sm:block">
                Phím tắt: số 1-4 để chấm · Space để lật thẻ
              </p>
            </div>
          ) : (
            <Button variant="secondary" size="lg" onClick={toggleFlip}>
              Hiện đáp án
            </Button>
          )}
        </div>
      )}
    </div>
  )
}

/** Phần còn lại của kế hoạch hôm nay, cùng lý do nếu có thẻ hay từ mới được để dành cho những ngày sau. */
function TodayPlan({ plan }: { plan: DailyPlanResponse }) {
  const reviewsLater = plan.dueReviews - plan.reviewsToday
  const newLater = plan.newWaiting - plan.newToday
  const noNewWords = plan.newWaiting === 0 && plan.newLearnedToday < plan.newPerDay
  return (
    <div className="mb-6 rounded-2xl border-2 border-border bg-card px-4 py-3">
      <div className="flex flex-wrap items-center gap-x-3 gap-y-1">
        <CalendarCheck className="h-5 w-5 shrink-0 text-secondary" strokeWidth={2.5} />
        <p className="flex-1 font-extrabold">
          Hôm nay còn: {plan.reviewsToday} thẻ ôn · {plan.newToday} từ mới
        </p>
        {plan.reviewsToday + plan.newToday > 0 && (
          <span className="text-sm font-bold text-muted-foreground">khoảng {plan.estimatedMinutes} phút</span>
        )}
      </div>
      <ul className="mt-1 flex flex-col gap-0.5 pl-8 text-sm font-semibold text-muted-foreground">
        {reviewsLater > 0 && (
          <li>
            {reviewsLater} thẻ đến hạn khác sẽ ôn dần, vì mỗi ngày bạn dành {plan.dailyMinutes} phút - thẻ dễ quên nhất được ôn
            trước.
          </li>
        )}
        {newLater > 0 && (
          <li>
            {newLater} từ mới khác để dành cho những ngày sau (tối đa {plan.newPerDay} từ mỗi ngày
            {plan.newPerDayLimitedByTime ? ', đã giảm vì đang có nhiều thẻ cần ôn' : ''}).
          </li>
        )}
        {noNewWords && (
          <li>
            Không còn từ mới nào chờ học -{' '}
            <Link to="/study" className="font-bold text-secondary hover:underline">
              thêm một bài học vào Ôn tập
            </Link>
            .
          </li>
        )}
      </ul>
    </div>
  )
}
