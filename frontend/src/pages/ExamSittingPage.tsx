import { useNavigate, useParams } from 'react-router-dom'
import { useMutation, useQuery } from '@tanstack/react-query'
import { Award, BookOpen, Check, Clock, Coffee, Eye, Play, RotateCcw, Target } from 'lucide-react'
import { examApi } from '@/api/exam'
import { extractErrorMessage } from '@/api/client'
import type { ExamSittingResponse } from '@/api/types'
import { cacheStartedExam } from '@/lib/examCache'
import { formatMinutes, SECTION_META } from '@/lib/jlpt'
import { Card } from '@/components/ui/card'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Alert } from '@/components/ui/alert'
import { PageSpinner } from '@/components/ui/spinner'
import { PageHeader } from '@/components/PageHeader'
import { StatTile } from '@/components/StatTile'

const SITTING_STATUS: Record<ExamSittingResponse['status'], { label: string; variant: 'secondary' | 'success' | 'orange' }> = {
  IN_PROGRESS: { label: 'Đang làm', variant: 'secondary' },
  COMPLETED: { label: 'Đã làm xong', variant: 'success' },
  ABANDONED: { label: 'Bỏ dở', variant: 'orange' },
}

const SECTION_STATUS: Record<string, string> = {
  IN_PROGRESS: 'Đang làm',
  COMPLETED: 'Đã nộp bài',
  TIMEOUT: 'Hết giờ - tự động nộp',
}

/** Một buổi làm đề JLPT: nghỉ giữa hai phần rồi làm tiếp, kết quả từng phần và cả buổi. */
export function ExamSittingPage() {
  const { sittingId: sittingIdParam } = useParams()
  const sittingId = Number(sittingIdParam)
  const navigate = useNavigate()

  const sittingQuery = useQuery({
    queryKey: ['exam-sitting', sittingId],
    queryFn: () => examApi.getSitting(sittingId),
  })
  const sitting = sittingQuery.data
  // Số câu, số phút của phần tiếp theo (đề ghép được lúc này), từ đúng nguồn câu của buổi thi.
  const source = sitting?.questionSource ?? undefined
  const levelsQuery = useQuery({
    queryKey: ['jlpt-levels', source ?? 'ALL'],
    queryFn: () => examApi.jlptLevels(source),
    enabled: !!sitting?.nextSection,
  })
  const nextMutation = useMutation({
    mutationFn: () => examApi.startNextSection(sittingId),
    onSuccess: (data) => {
      cacheStartedExam(data)
      navigate(`/exam/${data.attemptId}`)
    },
  })

  if (sittingQuery.isLoading) return <PageSpinner label="Đang tải buổi thi..." />
  if (sittingQuery.isError || !sitting) return <Alert>{extractErrorMessage(sittingQuery.error)}</Alert>

  const done = sitting.sections.filter((s) => s.totalQuestions !== null)
  const score = done.reduce((sum, s) => sum + (s.totalScore ?? 0), 0)
  const total = done.reduce((sum, s) => sum + (s.totalQuestions ?? 0), 0)
  const timeSpent = done.reduce((sum, s) => sum + usedSeconds(s), 0)
  const running = sitting.sections.find((s) => s.status === 'IN_PROGRESS')
  const next = sitting.nextSection
  const nextInfo = next
    ? levelsQuery.data?.find((l) => l.jlptLevel === sitting.jlptLevel)?.sections.find((s) => s.name === next)
    : undefined
  const status = SITTING_STATUS[sitting.status]

  return (
    <div className="flex flex-col gap-6">
      <PageHeader
        title={`Đề JLPT ${sitting.jlptLevel}`}
        subtitle={
          <span className="flex flex-wrap items-center gap-2">
            <Badge variant={status.variant}>{status.label}</Badge>
            {sitting.questionSource === 'IMPORTED' && <Badge variant="outline">Đề tự soạn</Badge>}
            {sitting.customTime && (
              <Badge variant="orange" title="Có phần tự đặt giờ nên không tính vào bảng xếp hạng">
                Giờ tự đặt · không xếp hạng
              </Badge>
            )}
            {sitting.sections.map((s) => SECTION_META[s.name].vi).join(' + ')}
          </span>
        }
      />

      {next && (
        <Card className="flex flex-col gap-4 border-accent p-5 sm:p-6">
          <div className="flex items-start gap-3">
            <span className="flex h-12 w-12 shrink-0 items-center justify-center rounded-2xl bg-accent-soft text-accent-dark">
              <Coffee className="h-6 w-6" strokeWidth={2.5} />
            </span>
            <div>
              <h2 className="text-lg font-black">{done.length > 0 ? 'Nghỉ giữa giờ' : 'Sẵn sàng làm bài'}</h2>
              <p className="font-semibold">
                Phần tiếp theo: <span className="font-jp font-black">{SECTION_META[next].jp}</span>
                {nextInfo && (
                  <span className="text-muted-foreground">
                    {' '}
                    · {nextInfo.questionCount} câu · {nextInfo.minutes} phút
                  </span>
                )}
              </p>
              <p className="mt-1 text-sm font-semibold text-muted-foreground">
                Đồng hồ chỉ chạy khi bạn bấm bắt đầu. Bỏ dở quá 3 tiếng kể từ lúc vào đề thì buổi thi tự kết thúc, kết
                quả các phần đã làm vẫn được giữ.
              </p>
            </div>
          </div>
          {nextMutation.isError && <Alert>{extractErrorMessage(nextMutation.error)}</Alert>}
          <Button size="lg" disabled={nextMutation.isPending} onClick={() => nextMutation.mutate()}>
            <Play className="h-5 w-5 fill-current" />
            {nextMutation.isPending ? 'Đang ghép đề...' : `Bắt đầu phần ${SECTION_META[next].vi}`}
          </Button>
        </Card>
      )}

      {running?.attemptId && (
        <Card className="flex flex-wrap items-center justify-between gap-3 p-5">
          <p className="font-bold">
            Phần <span className="font-jp font-black">{SECTION_META[running.name].jp}</span> đang làm dở.
          </p>
          <Button onClick={() => navigate(`/exam/${running.attemptId}`)}>
            <Play className="h-5 w-5 fill-current" /> Làm tiếp
          </Button>
        </Card>
      )}

      {sitting.status !== 'IN_PROGRESS' && total > 0 && (
        <div className="flex flex-col gap-2">
          <div className="grid grid-cols-2 gap-3 sm:grid-cols-4">
            <StatTile label="Câu đúng" value={`${score}/${total}`} tone="primary" icon={Check} />
            <StatTile label="Tỉ lệ đúng" value={`${Math.round((score / total) * 100)}%`} tone="secondary" icon={Target} />
            {sitting.estimatedScore !== null && (
              <StatTile label="Điểm ước tính" value={`${sitting.estimatedScore}/60`} tone="purple" icon={Award} />
            )}
            <StatTile label="Thời gian" value={formatMinutes(timeSpent)} tone="orange" icon={Clock} />
          </div>
          <p className="text-xs font-semibold text-muted-foreground">
            Điểm ước tính = tỉ lệ đúng × 60, chỉ để tham khảo: JLPT thật quy đổi điểm theo thống kê và có điểm sàn cho
            từng phần{sitting.sections.some((s) => s.totalQuestions === null) ? '; ở đây chỉ tính các phần đã làm' : ''}.
          </p>
        </div>
      )}

      <div className="flex flex-col gap-3">
        {sitting.sections.map((s) => (
          <Card key={s.name} className="flex flex-wrap items-center gap-4 p-4 sm:p-5">
            <div className="min-w-0 flex-1">
              <p className="font-jp font-black">{SECTION_META[s.name].jp}</p>
              <p className="text-sm font-semibold text-muted-foreground">
                {SECTION_META[s.name].vi} ·{' '}
                {s.status ? SECTION_STATUS[s.status] : sitting.status === 'IN_PROGRESS' ? 'Chưa làm' : 'Không làm'}
              </p>
            </div>
            {s.totalQuestions !== null && (
              <div className="text-right">
                <p className="text-2xl font-black tabular-nums">
                  {s.totalScore}/{s.totalQuestions}
                </p>
                <p className="text-xs font-bold text-muted-foreground">
                  {s.totalQuestions > 0 ? Math.round(((s.totalScore ?? 0) / s.totalQuestions) * 100) : 0}% ·{' '}
                  {formatMinutes(usedSeconds(s))}
                  {s.durationSeconds && ` / ${formatMinutes(s.durationSeconds)}`}
                </p>
              </div>
            )}
            {s.attemptId && s.totalQuestions !== null && (
              <Button variant="outline" size="sm" onClick={() => navigate(`/exam/${s.attemptId}/result`)}>
                <Eye className="h-4 w-4" /> Xem lại
              </Button>
            )}
          </Card>
        ))}
      </div>

      {sitting.status !== 'IN_PROGRESS' && <WeakGrammarCard level={sitting.jlptLevel} sittingId={sitting.sittingId} />}

      <div className="flex flex-col gap-3 sm:flex-row">
        <Button size="lg" className="flex-1" variant={next ? 'outline' : 'default'} onClick={() => navigate('/exam')}>
          <RotateCcw className="h-5 w-5" /> Làm đề khác
        </Button>
        <Button variant="outline" size="lg" className="flex-1" onClick={() => navigate('/study')}>
          <BookOpen className="h-5 w-5" /> Về học bài
        </Button>
      </div>
    </div>
  )
}

/** Các điểm ngữ pháp hay làm sai trong các đề gần đây của cấp độ, và nút luyện lại các điểm đó. */
function WeakGrammarCard({ level, sittingId }: { level: string; sittingId: number }) {
  const navigate = useNavigate()
  const { data: points } = useQuery({
    queryKey: ['weak-grammar', level],
    queryFn: () => examApi.weakGrammar(level),
  })
  if (!points || points.length === 0) return null

  const ids = points.map((point) => point.id).join(',')
  return (
    <Card className="p-4 sm:p-5">
      <h2 className="text-lg font-black">Điểm ngữ pháp cần ôn</h2>
      <p className="text-sm font-semibold text-muted-foreground">
        Làm sai nhiều nhất trong các đề {level} 60 ngày qua (không tính câu bỏ trống).
      </p>
      <ul className="mt-3 flex flex-col gap-2">
        {points.map((point) => (
          <li key={point.id} className="flex items-center gap-3 rounded-xl border-2 border-border px-3 py-2">
            <span className="shrink-0 font-jp font-black">{point.pattern}</span>
            <span className="min-w-0 flex-1 truncate text-sm font-semibold text-muted-foreground">{point.meaningVi}</span>
            <span className="shrink-0 text-sm font-black tabular-nums text-destructive-dark">
              sai {point.wrong}/{point.answered}
            </span>
          </li>
        ))}
      </ul>
      <Button
        className="mt-4"
        variant="secondary"
        onClick={() => navigate(`/exam/grammar-practice?level=${level}&points=${ids}&sitting=${sittingId}`)}
      >
        <Target className="h-5 w-5" /> Luyện lại các điểm này
      </Button>
    </Card>
  )
}

/** Thời gian đã dùng của một phần; bài tự nộp khi hết giờ có thể được chốt trễ vài giây, không tính phần dôi ra. */
function usedSeconds(section: ExamSittingResponse['sections'][number]) {
  const spent = section.timeSpentSeconds ?? 0
  return section.durationSeconds ? Math.min(spent, section.durationSeconds) : spent
}
