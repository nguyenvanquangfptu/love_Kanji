import { useParams, useNavigate } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import { BookOpen, Check, CheckCircle2, Clock, Lightbulb, RotateCcw, Target, Trophy, XCircle } from 'lucide-react'
import { examApi } from '@/api/exam'
import { extractErrorMessage } from '@/api/client'
import { cn } from '@/lib/utils'
import { Card } from '@/components/ui/card'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { PageSpinner } from '@/components/ui/spinner'
import { Alert } from '@/components/ui/alert'
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs'
import { Leaderboard } from '@/components/Leaderboard'
import { StatTile } from '@/components/StatTile'

const STATUS_LABEL: Record<string, string> = {
  COMPLETED: 'Đã nộp bài',
  TIMEOUT: 'Hết giờ - tự động nộp',
  IN_PROGRESS: 'Đang làm bài',
}

export function ExamResultPage() {
  const { attemptId: attemptIdParam } = useParams()
  const attemptId = Number(attemptIdParam)
  const navigate = useNavigate()

  const { data, isLoading, isError, error } = useQuery({
    queryKey: ['exam', attemptId, 'review'],
    queryFn: () => examApi.getReview(attemptId),
  })

  if (isLoading) return <PageSpinner label="Đang chấm điểm..." />
  if (isError || !data) return <Alert>{extractErrorMessage(error)}</Alert>

  const percent = data.totalQuestions > 0 ? Math.round((data.totalScore / data.totalQuestions) * 100) : 0
  const minutes = Math.floor(data.timeSpentSeconds / 60)
  const seconds = data.timeSpentSeconds % 60
  const title = percent >= 90 ? 'Xuất sắc!' : percent >= 70 ? 'Làm tốt lắm!' : percent >= 50 ? 'Khá ổn!' : 'Cố gắng thêm nhé!'

  return (
    <div className="flex flex-col gap-6">
      <div className="flex flex-col items-center text-center">
        <span className="flex h-24 w-24 animate-bounce-in items-center justify-center rounded-full bg-accent-soft text-accent-dark">
          <Trophy className="h-12 w-12" strokeWidth={2.5} />
        </span>
        <h1 className="mt-4 text-3xl font-black">{title}</h1>
        <div className="mt-2 flex items-center gap-2">
          <Badge variant="purple">Thi thử {data.jlptLevel}</Badge>
          <Badge variant={data.status === 'COMPLETED' ? 'success' : 'accent'}>{STATUS_LABEL[data.status] ?? data.status}</Badge>
        </div>
      </div>

      <div className="grid grid-cols-3 gap-3">
        <StatTile label="Câu đúng" value={`${data.totalScore}/${data.totalQuestions}`} tone="primary" icon={Check} />
        <StatTile label="Tỉ lệ đúng" value={`${percent}%`} tone="secondary" icon={Target} />
        <StatTile label="Thời gian" value={`${minutes}:${seconds.toString().padStart(2, '0')}`} tone="orange" icon={Clock} />
      </div>

      <div className="flex flex-col gap-3 sm:flex-row">
        <Button size="lg" className="flex-1" onClick={() => navigate('/exam')}>
          <RotateCcw className="h-5 w-5" /> Thi lại
        </Button>
        <Button variant="outline" size="lg" className="flex-1" onClick={() => navigate('/study')}>
          <BookOpen className="h-5 w-5" /> Về học bài
        </Button>
      </div>

      <Tabs defaultValue="review">
        <TabsList>
          <TabsTrigger value="review">Xem lại bài làm</TabsTrigger>
          <TabsTrigger value="leaderboard">Bảng xếp hạng</TabsTrigger>
        </TabsList>

        <TabsContent value="review">
          <div className="flex flex-col gap-4">
            {data.questions.map((q, idx) => (
              <Card key={q.questionId} className={cn('p-4 sm:p-5', q.correct ? 'border-primary/40' : 'border-destructive/40')}>
                <div className="flex items-start gap-2">
                  {q.correct ? (
                    <CheckCircle2 className="mt-0.5 h-6 w-6 shrink-0 text-primary" strokeWidth={2.5} />
                  ) : (
                    <XCircle className="mt-0.5 h-6 w-6 shrink-0 text-destructive" strokeWidth={2.5} />
                  )}
                  <p className="font-jp text-lg font-bold">
                    <span className="font-sans text-muted-foreground">Câu {idx + 1}.</span> {q.questionText}
                  </p>
                </div>
                <div className="mt-3 grid gap-2 sm:grid-cols-2">
                  {(['A', 'B', 'C', 'D'] as const).map((opt) => {
                    const text = q[`option${opt}` as 'optionA' | 'optionB' | 'optionC' | 'optionD']
                    const isCorrect = q.correctOption === opt
                    const isSelected = q.selectedOption === opt
                    return (
                      <div
                        key={opt}
                        className={cn(
                          'flex items-center gap-2 rounded-xl border-2 px-3 py-2 font-jp font-bold',
                          isCorrect && 'border-primary bg-primary-soft text-primary-dark',
                          isSelected && !isCorrect && 'border-destructive bg-destructive-soft text-destructive-dark',
                          !isCorrect && !isSelected && 'border-border text-muted-foreground',
                        )}
                      >
                        <span className="font-sans font-black">{opt}.</span> {text}
                        {isSelected && <span className="ml-auto font-sans text-xs font-extrabold">Bạn chọn</span>}
                      </div>
                    )
                  })}
                </div>
                {!q.selectedOption && <p className="mt-2 text-sm font-bold text-orange-dark">Bạn đã bỏ trống câu này.</p>}
                {q.explanation && (
                  <p className="mt-3 flex items-start gap-2 rounded-xl bg-secondary-soft px-3 py-2 text-sm font-semibold text-secondary-dark">
                    <Lightbulb className="mt-0.5 h-4 w-4 shrink-0" strokeWidth={2.5} />
                    {q.explanation}
                  </p>
                )}
              </Card>
            ))}
          </div>
        </TabsContent>

        <TabsContent value="leaderboard">
          <Leaderboard level={data.jlptLevel} />
        </TabsContent>
      </Tabs>
    </div>
  )
}
