import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import {
  BookOpen,
  Brain,
  BrainCircuit,
  CalendarCheck,
  ChevronRight,
  Clock,
  Dumbbell,
  PartyPopper,
  RotateCcw,
  Zap,
  Sprout,
  Target,
} from 'lucide-react'
import { srsApi } from '@/api/srs'
import type { DailyCardResponse, DailyPlanResponse, Page, ReviewRating, ReviewRequest } from '@/api/types'
import { extractErrorMessage, isConflict } from '@/api/client'
import { cn, wordSizeClass } from '@/lib/utils'
import { addDays, formatDay, formatInterval } from '@/lib/dates'
import { lessonQuery } from '@/lib/lesson'
import { lessonTitle } from '@/lib/levels'
import { FlipCard } from '@/components/FlipCard'
import { PageHeader } from '@/components/PageHeader'
import { StatTile } from '@/components/StatTile'
import { EmptyState } from '@/components/EmptyState'
import { SpeakButton } from '@/components/SpeakButton'
import { MemoryAidPanel } from '@/components/MemoryAidPanel'
import { CharacterChips } from '@/components/CharacterChips'
import { UndoReviewBar } from '@/components/UndoReviewBar'
import { useCharacterParts } from '@/lib/characters'
import { useCharacterSheetStore } from '@/store/characterSheetStore'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Alert } from '@/components/ui/alert'
import { Progress } from '@/components/ui/progress'
import { PageSpinner } from '@/components/ui/spinner'

const DAILY_CARDS_KEY = ['srs', 'daily-cards']
const STATS_KEY = ['srs', 'stats']
const DAILY_PLAN_KEY = ['srs', 'daily-plan']

/** Ôn nhanh: lượng ôn vừa chừng này phút. */
const QUICK_MINUTES = 5

/** Hoàn tác được tối đa chừng này lần chấm gần nhất của phiên (server còn giới hạn 30 phút). */
const MAX_UNDO = 20

/** Một thẻ đã chấm trong phiên, giữ nguyên như lúc hiện ra để đưa lại vào hàng đợi khi hoàn tác. */
interface RatedCard {
  card: DailyCardResponse
  rating: ReviewRating
}

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
  // Các thẻ đã chấm trong phiên (mới nhất ở cuối): bấm nhầm thì hoàn tác lần lượt từ thẻ cuối.
  const [ratedCards, setRatedCards] = useState<RatedCard[]>([])
  const lastRated = ratedCards.at(-1)
  // Ôn nhanh: chỉ ôn N thẻ đầu hàng đợi (đã xếp dễ quên nhất trước), N vừa khoảng 5 phút theo nhịp của chính người học.
  const [quick, setQuick] = useState<{ target: number; startLength: number } | null>(null)

  const { data, isLoading, isError, error } = useQuery({
    queryKey: cardsKey,
    queryFn: () => srsApi.getDailyCards({ size: 100, extra }),
  })

  const { data: stats } = useQuery({ queryKey: STATS_KEY, queryFn: srsApi.getStats })
  const { data: plan } = useQuery({ queryKey: DAILY_PLAN_KEY, queryFn: srsApi.getDailyPlan })

  const queue = data?.content ?? []
  const current = queue[0]
  const { data: characterParts } = useCharacterParts(current ? [current.kanji.id] : [])
  const quickSize = quickReviewSize(plan)
  const quickDone = quick ? quick.startLength - queue.length : 0
  const quickFinished = quick !== null && quickDone >= quick.target

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
      return { previous, card: previous?.content.find((c) => c.kanji.id === vars.kanjiId) }
    },
    onSuccess: (_data, vars, context) => {
      const card = context?.card
      if (card) setRatedCards((stack) => [...stack, { card, rating: vars.rating }].slice(-MAX_UNDO))
    },
    onError: (err, _vars, context) => {
      // Thẻ vừa được chấm ở tab khác: tải lại phiên ôn thay vì đưa thẻ cũ trở lại.
      if (isConflict(err)) queryClient.invalidateQueries({ queryKey: cardsKey })
      else if (context?.previous) queryClient.setQueryData(cardsKey, context.previous)
    },
    onSettled: () => {
      queryClient.invalidateQueries({ queryKey: STATS_KEY })
      queryClient.invalidateQueries({ queryKey: DAILY_PLAN_KEY })
    },
  })

  const undoMutation = useMutation({
    mutationFn: (rated: RatedCard) => srsApi.undoReview(rated.card.kanji.id),
    onSuccess: (_data, rated) => {
      // Thẻ về lại đầu hàng đợi như lúc chưa chấm; không tải lại cả phiên để khỏi xáo các thẻ còn lại.
      queryClient.setQueryData<Page<DailyCardResponse> | undefined>(cardsKey, (old) =>
        old ? { ...old, content: [rated.card, ...old.content.filter((c) => c.kanji.id !== rated.card.kanji.id)] } : old,
      )
      setFlipped(false)
    },
    // Hoàn tác xong, hay bị từ chối (quá 30 phút, từ đã được trả lời lại) thì lần chấm đó cũng hết hoàn tác được.
    onSettled: (_data, _error, rated) => {
      setRatedCards((stack) => stack.filter((entry) => entry !== rated))
      queryClient.invalidateQueries({ queryKey: STATS_KEY })
      queryClient.invalidateQueries({ queryKey: DAILY_PLAN_KEY })
    },
  })

  const undoLast = useCallback(() => {
    if (lastRated && !undoMutation.isPending && !reviewMutation.isPending) undoMutation.mutate(lastRated)
  }, [lastRated, undoMutation, reviewMutation.isPending])

  function studyExtra() {
    setRatedCards([])
    setQuick(null)
    setSessionTotal(null)
    if (extra) queryClient.invalidateQueries({ queryKey: cardsKey })
    else setExtra(true)
  }

  const rate = useMemo(
    () => (rating: ReviewRating) => {
      if (!current || reviewMutation.isPending || undoMutation.isPending) return
      reviewMutation.mutate({ kanjiId: current.kanji.id, rating, responseMs: recallMs.current ?? undefined })
    },
    [current, reviewMutation, undoMutation.isPending],
  )

  useEffect(() => {
    function onKeyDown(e: KeyboardEvent) {
      // Đang gõ ghi chú cách nhớ: phím số, Space và Ctrl+Z là của ô nhập, không phải phím tắt.
      if (e.target instanceof HTMLTextAreaElement || e.target instanceof HTMLInputElement) return
      // Đang xem bảng từ cùng chữ: không lật hay chấm thẻ nằm bên dưới.
      if (useCharacterSheetStore.getState().character !== null) return
      // Hoàn tác được cả khi vừa chấm xong thẻ cuối (không còn thẻ nào đang hiện).
      if ((e.ctrlKey || e.metaKey) && !e.shiftKey && e.key.toLowerCase() === 'z') {
        e.preventDefault()
        undoLast()
        return
      }
      // Xong phần ôn nhanh: thẻ kế tiếp đang ẩn, không lật hay chấm nó.
      if (!current || quickFinished) return
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
  }, [current, quickFinished, flipped, rate, toggleFlip, undoLast])

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

      {lastRated && (
        <UndoReviewBar
          word={lastRated.card.kanji.character}
          ratingLabel={RATING_OPTIONS[lastRated.rating - 1].label}
          pending={undoMutation.isPending}
          onUndo={undoLast}
          className="mx-auto mb-4 max-w-xl"
        />
      )}
      {undoMutation.isError && (
        <Alert className="mx-auto mb-4 max-w-xl">{extractErrorMessage(undoMutation.error)}</Alert>
      )}

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

      {current && quick && quickFinished && (
        <EmptyState
          icon={Zap}
          iconClassName="bg-accent-soft text-accent-dark"
          title={`Xong ${QUICK_MINUTES} phút ôn nhanh!`}
          description={`Bạn vừa ôn ${quick.target} thẻ dễ quên nhất. Hôm nay còn ${queue.length} thẻ - ôn tiếp nếu còn thời gian.`}
          action={
            <div className="flex flex-col gap-3 sm:flex-row">
              <Button size="lg" onClick={() => setQuick(null)}>
                <RotateCcw className="h-5 w-5" /> Ôn tiếp
              </Button>
              <Button size="lg" variant="outline" onClick={() => navigate('/study')}>
                <BookOpen className="h-5 w-5" /> Để sau
              </Button>
            </div>
          }
        />
      )}

      {current && !quickFinished && (
        <div className="mx-auto flex max-w-xl flex-col gap-5">
          {quick ? (
            <div className="flex items-center gap-3">
              <Badge variant="accent" className="shrink-0">
                <Zap className="h-3.5 w-3.5" /> Ôn nhanh
              </Badge>
              <Progress value={(quickDone / quick.target) * 100} className="flex-1" />
              <span className="text-sm font-extrabold text-muted-foreground">
                {quickDone}/{quick.target}
              </span>
              <Button variant="ghost" size="sm" onClick={() => setQuick(null)}>
                Ôn hết
              </Button>
            </div>
          ) : (
            sessionTotal !== null &&
            sessionTotal > 0 && (
              <div className="flex items-center gap-3">
                <Progress value={(doneCount / sessionTotal) * 100} className="flex-1" />
                <span className="text-sm font-extrabold text-muted-foreground">
                  {doneCount}/{sessionTotal}
                </span>
              </div>
            )
          )}
          {!quick && queue.length > quickSize && (
            <Button
              variant="outline"
              size="sm"
              className="self-center"
              onClick={() => setQuick({ target: quickSize, startLength: queue.length })}
            >
              <Zap className="h-4 w-4" /> Ít thời gian? Ôn nhanh {QUICK_MINUTES} phút ({quickSize} thẻ dễ quên nhất)
            </Button>
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
                <CharacterChips
                  hanViet={current.kanji.hanViet}
                  parts={characterParts?.[current.kanji.id]}
                  size="sm"
                  className="justify-center text-muted-foreground"
                />
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
                    disabled={reviewMutation.isPending || undoMutation.isPending}
                    onClick={() => rate(rating)}
                    title={hint}
                    className={cn(
                      'flex flex-col items-center rounded-2xl border-b-4 px-2 py-2.5 transition-all hover:brightness-105 active:translate-y-[2px] active:border-b-2 disabled:opacity-60',
                      className,
                    )}
                  >
                    <span className="text-base font-black leading-none">{label}</span>
                    <span className="mt-1 text-[11px] font-bold leading-tight opacity-90">
                      {current.intervals ? formatInterval(current.intervals[rating - 1]) : hint}
                    </span>
                  </button>
                ))}
              </div>
              <p className="mt-3 hidden text-center text-xs font-semibold text-muted-foreground sm:block">
                Phím tắt: số 1-4 để chấm · Space để lật thẻ · Ctrl+Z để hoàn tác
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

/**
 * Số thẻ vừa khoảng {@link QUICK_MINUTES} phút theo nhịp ôn của chính người học: kế hoạch hôm nay ước tính bao nhiêu
 * phút cho bao nhiêu thẻ, chia lại theo tỉ lệ. Chưa có kế hoạch (hoặc chưa đo được nhịp) thì lấy 10 thẻ; ít nhất 5 thẻ.
 */
function quickReviewSize(plan: DailyPlanResponse | undefined) {
  const cards = plan ? plan.reviewsToday + plan.newToday : 0
  if (!plan || cards <= 0 || plan.estimatedMinutes <= 0) return 10
  return Math.max(5, Math.round((cards * QUICK_MINUTES) / plan.estimatedMinutes))
}

/** Phần còn lại của kế hoạch hôm nay, lý do nếu có thẻ hay từ mới được để dành, và tiến độ so với mục tiêu. */
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
        {plan.nextLesson ? (
          <li>
            Sắp hết từ mới để học -{' '}
            <Link
              to={`/study/vocab?${lessonQuery({ id: plan.nextLesson.tagId, name: plan.nextLesson.name })}`}
              className="font-bold text-secondary hover:underline"
            >
              thêm {lessonTitle(plan.nextLesson.name)} ({plan.nextLesson.words} từ) vào Ôn tập
            </Link>
            .
          </li>
        ) : (
          noNewWords && (
            <li>
              Không còn từ mới nào chờ học -{' '}
              <Link to="/study" className="font-bold text-secondary hover:underline">
                thêm một bài học vào Ôn tập
              </Link>
              .
            </li>
          )
        )}
      </ul>
      <GoalProgress plan={plan} />
    </div>
  )
}

/** Tiến độ so với mục tiêu (cấp độ + ngày thi), hoặc lời mời đặt mục tiêu. */
function GoalProgress({ plan }: { plan: DailyPlanResponse }) {
  if (!plan.goalSet || plan.targetLevel === null || plan.wordsToLearn === null) {
    return (
      <Link to="/goals" className="mt-2 flex items-center gap-1 pl-8 text-sm font-bold text-secondary hover:underline">
        <Target className="h-4 w-4" />
        {plan.goalSet ? 'Chọn cấp độ nhắm tới' : 'Đặt mục tiêu học'} để app tính số từ mới mỗi ngày theo ngày thi
      </Link>
    )
  }
  const revisionStart = plan.examDate ? addDays(plan.examDate, -14) : null
  return (
    <div className="mt-2 border-t-2 border-border pt-2 pl-8 text-sm font-semibold">
      <p>
        <span className="font-extrabold">Mục tiêu {plan.targetLevel}</span>
        {plan.examDate && <> · thi {formatDay(plan.examDate)}</>}: còn {plan.wordsToLearn} từ chưa học.
        {plan.newPerDaySource === 'EXAM' && (
          <> Cần {plan.newPerDayWanted} từ mới mỗi ngày để học xong trước kỳ thi 2 tuần.</>
        )}{' '}
        <Link to="/goals" className="font-bold text-secondary hover:underline">
          Sửa mục tiêu
        </Link>
      </p>
      {plan.newPerDaySource === 'EXAM' && plan.newPerDayLimitedByTime && (
        <p className="text-orange-dark">
          {plan.dailyMinutes} phút mỗi ngày chỉ đủ cho {plan.newPerDay} từ mới vì còn thẻ cần ôn - tăng thời gian học để kịp.
        </p>
      )}
      {plan.projectedFinish && plan.onTrack !== null && revisionStart && (
        <p className={plan.onTrack ? 'text-primary-dark' : 'text-destructive-dark'}>
          Giữ nhịp 2 tuần qua (khoảng {Math.round(plan.recentNewPerDay)} từ/ngày), bạn học xong vào{' '}
          {formatDay(plan.projectedFinish)} - {plan.onTrack ? 'kịp' : 'trễ'} so với mốc {formatDay(revisionStart)}.
        </p>
      )}
    </div>
  )
}
