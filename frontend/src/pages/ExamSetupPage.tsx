import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { useMutation } from '@tanstack/react-query'
import { ListChecks, Play, Timer, Trophy } from 'lucide-react'
import { examApi } from '@/api/exam'
import { extractErrorMessage } from '@/api/client'
import { cacheExamQuestions } from '@/lib/examCache'
import { JLPT_LEVELS, type JlptLevel } from '@/api/types'
import { LEVEL_META } from '@/lib/levels'
import { cn } from '@/lib/utils'
import { Button } from '@/components/ui/button'
import { Alert } from '@/components/ui/alert'
import { Card } from '@/components/ui/card'
import { PageHeader } from '@/components/PageHeader'
import { Leaderboard } from '@/components/Leaderboard'

const QUESTION_COUNTS = [10, 20, 30, 50]

export function ExamSetupPage() {
  const [level, setLevel] = useState<JlptLevel>('N5')
  const [questionCount, setQuestionCount] = useState(20)
  const navigate = useNavigate()

  const startMutation = useMutation({
    mutationFn: examApi.start,
    onSuccess: (data) => {
      cacheExamQuestions(data.attemptId, { jlptLevel: data.jlptLevel, questions: data.questions })
      navigate(`/exam/${data.attemptId}`)
    },
  })

  return (
    <div>
      <PageHeader title="Thi thử JLPT" subtitle="Làm bài có tính giờ để kiểm tra trình độ và leo bảng xếp hạng." />

      <div className="grid gap-6 lg:grid-cols-[1fr_320px]">
        <Card className="flex flex-col gap-6 p-5 sm:p-6">
          {startMutation.isError && <Alert>{extractErrorMessage(startMutation.error)}</Alert>}

          <section>
            <h2 className="mb-3 font-black">1. Chọn cấp độ</h2>
            <div className="grid grid-cols-5 gap-2">
              {JLPT_LEVELS.map((lv) => (
                <button
                  key={lv}
                  type="button"
                  aria-pressed={level === lv}
                  onClick={() => setLevel(lv)}
                  className={cn(
                    'rounded-2xl border-2 border-b-4 py-3 text-lg font-black transition-all active:translate-y-[2px] active:border-b-2',
                    level === lv ? LEVEL_META[lv].style.solid : 'border-border bg-card hover:bg-muted',
                  )}
                >
                  {lv}
                </button>
              ))}
            </div>
          </section>

          <section>
            <h2 className="mb-3 font-black">2. Số câu hỏi</h2>
            <div className="grid grid-cols-4 gap-2">
              {QUESTION_COUNTS.map((count) => (
                <button
                  key={count}
                  type="button"
                  aria-pressed={questionCount === count}
                  onClick={() => setQuestionCount(count)}
                  className={cn(
                    'rounded-2xl border-2 border-b-4 py-3 font-black transition-all active:translate-y-[2px] active:border-b-2',
                    questionCount === count
                      ? 'border-secondary bg-secondary-soft text-secondary-dark'
                      : 'border-border bg-card hover:bg-muted',
                  )}
                >
                  {count} câu
                </button>
              ))}
            </div>
          </section>

          <ul className="flex flex-wrap gap-x-5 gap-y-2 text-sm font-bold text-muted-foreground">
            <li className="flex items-center gap-1.5">
              <Timer className="h-4 w-4 text-orange" strokeWidth={3} /> Có đồng hồ đếm ngược
            </li>
            <li className="flex items-center gap-1.5">
              <ListChecks className="h-4 w-4 text-secondary" strokeWidth={3} /> Tự động lưu đáp án
            </li>
            <li className="flex items-center gap-1.5">
              <Trophy className="h-4 w-4 text-accent-dark" strokeWidth={3} /> Xem lại & giải thích
            </li>
          </ul>

          <Button
            size="lg"
            className="w-full"
            disabled={startMutation.isPending}
            onClick={() => startMutation.mutate({ jlptLevel: level, questionCount })}
          >
            <Play className="h-5 w-5 fill-current" />
            {startMutation.isPending ? 'Đang tạo đề...' : `Bắt đầu thi ${level}`}
          </Button>
        </Card>

        <Card className="p-5">
          <h2 className="mb-4 flex items-center gap-2 font-black">
            <Trophy className="h-5 w-5 text-accent-dark" strokeWidth={3} /> Bảng xếp hạng {level}
          </h2>
          <Leaderboard level={level} />
        </Card>
      </div>
    </div>
  )
}
