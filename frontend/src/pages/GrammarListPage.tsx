import { useState } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { BookMarked, Check, ChevronDown, Plus, RotateCcw, ScrollText } from 'lucide-react'
import { GRAMMAR_POINTS_KEY, GRAMMAR_SESSION_KEY, grammarSrsApi } from '@/api/grammarSrs'
import { extractErrorMessage } from '@/api/client'
import { JLPT_LEVELS, type GrammarPointStatus, type JlptLevel } from '@/api/types'
import { LEVEL_META, lessonTitle } from '@/lib/levels'
import { cn } from '@/lib/utils'
import { PageHeader } from '@/components/PageHeader'
import { LevelTabs, type LevelKey } from '@/components/LevelTabs'
import { GrammarPointDetails } from '@/components/GrammarPointNote'
import { EmptyState } from '@/components/EmptyState'
import { Button, buttonVariants } from '@/components/ui/button'
import { Card } from '@/components/ui/card'
import { Badge } from '@/components/ui/badge'
import { Alert } from '@/components/ui/alert'
import { PageSpinner } from '@/components/ui/spinner'

/** Gom các điểm theo bài, giữ thứ tự server trả về (theo bài rồi theo thứ tự nhập); N3 không theo bài: một nhóm. */
function groupByLesson(points: GrammarPointStatus[]) {
  const groups = new Map<string, GrammarPointStatus[]>()
  for (const point of points) {
    const key = point.lesson ?? ''
    groups.set(key, [...(groups.get(key) ?? []), point])
  }
  return [...groups.entries()]
}

/**
 * Danh sách điểm ngữ pháp theo cấp độ và bài: xem nghĩa, cách nối, ví dụ; thêm từng điểm hay cả bài vào Ôn ngữ pháp
 * để ôn theo lịch như thẻ từ vựng. ?level=N4 chọn sẵn cấp độ.
 */
export function GrammarListPage() {
  const queryClient = useQueryClient()
  const [params, setParams] = useSearchParams()
  const levelParam = params.get('level')
  const level: JlptLevel = JLPT_LEVELS.includes(levelParam as JlptLevel) ? (levelParam as JlptLevel) : 'N5'
  const [open, setOpen] = useState<Set<number>>(new Set())

  const { data: points, isLoading, isError, error } = useQuery({
    queryKey: [...GRAMMAR_POINTS_KEY, level],
    queryFn: () => grammarSrsApi.points(level),
  })
  const { data: session } = useQuery({ queryKey: GRAMMAR_SESSION_KEY, queryFn: grammarSrsApi.session })

  const add = useMutation({
    mutationFn: (ids: number[]) => grammarSrsApi.addCards(ids),
    onSettled: () => {
      queryClient.invalidateQueries({ queryKey: GRAMMAR_POINTS_KEY })
      queryClient.invalidateQueries({ queryKey: GRAMMAR_SESSION_KEY })
    },
  })

  const toggle = (id: number) =>
    setOpen((current) => {
      const next = new Set(current)
      if (next.has(id)) next.delete(id)
      else next.add(id)
      return next
    })

  const style = LEVEL_META[level].style
  const inReview = points?.filter((point) => point.inReview).length ?? 0
  const today = session ? session.cards.length : 0

  return (
    <div>
      <PageHeader title="Ngữ pháp" subtitle="Thêm điểm ngữ pháp vào ôn tập để ôn lại đúng lúc sắp quên, như thẻ từ vựng." />

      <Card className="mb-5 flex flex-col gap-3 p-4 sm:flex-row sm:items-center">
        <div className="flex min-w-0 flex-1 items-center gap-3">
          <RotateCcw className="h-7 w-7 shrink-0 text-purple-dark" strokeWidth={2.5} />
          <div className="min-w-0">
            <p className="font-extrabold">Ôn ngữ pháp hôm nay</p>
            <p className="text-sm font-semibold text-muted-foreground">
              {session
                ? today > 0
                  ? `${session.dueCount} điểm đến hạn · ${Math.min(session.newLeftToday, session.newWaiting)} điểm mới`
                  : session.newWaiting > 0
                    ? 'Đã học đủ điểm mới hôm nay - mai học tiếp nhé.'
                    : 'Hôm nay không còn điểm nào cần ôn. Thêm điểm bên dưới để học thêm.'
                : 'Đang tải...'}
            </p>
          </div>
        </div>
        {today > 0 && (
          <Link to="/grammar/review" className={cn(buttonVariants(), 'sm:w-40')}>
            Ôn {today} điểm
          </Link>
        )}
      </Card>

      <LevelTabs
        levels={[...JLPT_LEVELS]}
        active={level}
        onChange={(key: LevelKey) => key !== 'OTHER' && setParams({ level: key }, { replace: true })}
      />

      {isLoading && <PageSpinner />}
      {isError && <Alert className="mt-5">{extractErrorMessage(error)}</Alert>}
      {add.isError && <Alert className="mt-5">{extractErrorMessage(add.error)}</Alert>}

      {points && points.length === 0 && (
        <EmptyState
          icon={ScrollText}
          title={`Chưa có điểm ngữ pháp ${level}`}
          description="Khi có điểm ngữ pháp cho cấp độ này, chúng sẽ xuất hiện ở đây."
        />
      )}

      {points && points.length > 0 && (
        <>
          <p className="mt-5 text-sm font-semibold text-muted-foreground">
            {points.length} điểm ngữ pháp · {inReview} điểm đang ôn
          </p>
          <div className="mt-3 flex flex-col gap-5">
            {groupByLesson(points).map(([lesson, group]) => {
              const missing = group.filter((point) => !point.inReview).map((point) => point.id)
              return (
                <section key={lesson} aria-label={lesson ? lessonTitle(lesson) : `Ngữ pháp ${level}`}>
                  <div className="mb-2 flex items-center gap-3">
                    <span
                      className={cn(
                        'flex h-9 w-9 shrink-0 items-center justify-center rounded-xl border-b-4 text-sm font-black',
                        style.solid,
                      )}
                    >
                      {lesson ? lessonTitle(lesson).replace('Bài ', '') : <BookMarked className="h-4 w-4" />}
                    </span>
                    <h2 className="flex-1 font-extrabold">{lesson ? lessonTitle(lesson) : `Ngữ pháp ${level}`}</h2>
                    {missing.length > 0 ? (
                      <Button size="sm" variant="outline" disabled={add.isPending} onClick={() => add.mutate(missing)}>
                        <Plus className="h-4 w-4" /> Thêm cả bài ({missing.length})
                      </Button>
                    ) : (
                      <Badge variant="purple">
                        <Check className="h-3.5 w-3.5" /> Đã thêm cả bài
                      </Badge>
                    )}
                  </div>
                  <Card className="divide-y-2 divide-border overflow-hidden">
                    {group.map((point) => (
                      <div key={point.id} className="p-3 sm:p-4">
                        <div className="flex items-center gap-3">
                          <button
                            type="button"
                            aria-expanded={open.has(point.id)}
                            onClick={() => toggle(point.id)}
                            className="flex min-w-0 flex-1 items-center gap-2 text-left"
                          >
                            <span className="min-w-0 flex-1">
                              <span className="block font-jp text-lg font-black text-purple-dark">{point.pattern}</span>
                              <span className="block text-sm font-semibold text-muted-foreground">{point.meaningVi}</span>
                            </span>
                            <ChevronDown
                              className={cn(
                                'h-5 w-5 shrink-0 text-muted-foreground transition-transform',
                                open.has(point.id) && 'rotate-180',
                              )}
                            />
                          </button>
                          {point.inReview ? (
                            <Badge variant="purple" className="shrink-0">
                              Đang ôn
                            </Badge>
                          ) : (
                            <Button
                              size="sm"
                              variant="outline"
                              className="shrink-0"
                              disabled={add.isPending}
                              onClick={() => add.mutate([point.id])}
                              aria-label={`Thêm ${point.pattern} vào ôn tập`}
                            >
                              <Plus className="h-4 w-4" /> Thêm
                            </Button>
                          )}
                        </div>
                        {open.has(point.id) && <GrammarPointDetails point={point} id={`grammar-${point.id}`} />}
                      </div>
                    ))}
                  </Card>
                </section>
              )
            })}
          </div>
        </>
      )}
    </div>
  )
}
