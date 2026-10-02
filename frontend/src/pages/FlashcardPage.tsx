import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { BookOpen, Brain, BrainCircuit, Clock, PartyPopper, Sprout } from 'lucide-react'
import { srsApi } from '@/api/srs'
import type { DailyCardResponse, Page, ReviewRating, ReviewRequest } from '@/api/types'
import { extractErrorMessage } from '@/api/client'
import { cn, wordSizeClass } from '@/lib/utils'
import { FlipCard } from '@/components/FlipCard'
import { PageHeader } from '@/components/PageHeader'
import { StatTile } from '@/components/StatTile'
import { EmptyState } from '@/components/EmptyState'
import { SpeakButton } from '@/components/SpeakButton'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Alert } from '@/components/ui/alert'
import { Progress } from '@/components/ui/progress'
import { PageSpinner } from '@/components/ui/spinner'

const DAILY_CARDS_KEY = ['srs', 'daily-cards']
const STATS_KEY = ['srs', 'stats']

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

  const { data, isLoading, isError, error } = useQuery({
    queryKey: DAILY_CARDS_KEY,
    queryFn: () => srsApi.getDailyCards({ size: 50 }),
  })

  const { data: stats } = useQuery({ queryKey: STATS_KEY, queryFn: srsApi.getStats })

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
      await queryClient.cancelQueries({ queryKey: DAILY_CARDS_KEY })
      const previous = queryClient.getQueryData<Page<DailyCardResponse>>(DAILY_CARDS_KEY)
      queryClient.setQueryData<Page<DailyCardResponse> | undefined>(DAILY_CARDS_KEY, (old) =>
        old ? { ...old, content: old.content.filter((c) => c.kanji.id !== vars.kanjiId) } : old,
      )
      setFlipped(false)
      return { previous }
    },
    onError: (_err, _vars, context) => {
      if (context?.previous) queryClient.setQueryData(DAILY_CARDS_KEY, context.previous)
    },
    onSettled: () => queryClient.invalidateQueries({ queryKey: STATS_KEY }),
  })

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

  return (
    <div>
      <PageHeader title="Ôn tập hôm nay" subtitle="Ôn lại đúng lúc sắp quên để nhớ lâu hơn." />

      {stats && (
        <div className="mb-6 grid grid-cols-3 gap-3">
          <StatTile label="Cần ôn" value={stats.dueForReview} tone="orange" icon={Clock} />
          <StatTile label="Đang học" value={stats.stillLearning} tone="secondary" icon={Sprout} />
          <StatTile label="Đã thuộc" value={stats.deeplyMemorized} tone="primary" icon={Brain} />
        </div>
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
          description="Quay lại vào ngày mai để giữ chuỗi ôn tập, hoặc học thêm bài mới ngay bây giờ."
          action={
            <Button size="lg" onClick={() => navigate('/study')}>
              <BookOpen className="h-5 w-5" /> Học bài mới
            </Button>
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
                <Badge variant="secondary" className="mt-6">
                  {current.kanji.jlptLevel}
                </Badge>
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
