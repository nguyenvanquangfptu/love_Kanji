import { useNavigate, useParams } from 'react-router-dom'
import { useMutation, useQuery } from '@tanstack/react-query'
import { BookOpen, Check, Clock, Coffee, Eye, Play, RotateCcw, Target } from 'lucide-react'
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
  // Số câu, số phút của phần tiếp theo (đề ghép được lúc này).
  const levelsQuery = useQuery({
    queryKey: ['jlpt-levels'],
    queryFn: examApi.jlptLevels,
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
        <div className="grid grid-cols-3 gap-3">
          <StatTile label="Câu đúng" value={`${score}/${total}`} tone="primary" icon={Check} />
          <StatTile label="Tỉ lệ đúng" value={`${Math.round((score / total) * 100)}%`} tone="secondary" icon={Target} />
          <StatTile label="Thời gian" value={formatMinutes(timeSpent)} tone="orange" icon={Clock} />
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

/** Thời gian đã dùng của một phần; bài tự nộp khi hết giờ có thể được chốt trễ vài giây, không tính phần dôi ra. */
function usedSeconds(section: ExamSittingResponse['sections'][number]) {
  const spent = section.timeSpentSeconds ?? 0
  return section.durationSeconds ? Math.min(spent, section.durationSeconds) : spent
}
