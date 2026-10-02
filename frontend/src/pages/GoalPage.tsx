import { type ReactNode, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Target } from 'lucide-react'
import { profileApi } from '@/api/profile'
import { extractErrorMessage } from '@/api/client'
import { JLPT_LEVELS, type JlptLevel, type LearningProfileResponse } from '@/api/types'
import { formatDay, toIsoDay, upcomingJlptDays } from '@/lib/dates'
import { cn } from '@/lib/utils'
import { PageHeader } from '@/components/PageHeader'
import { Button } from '@/components/ui/button'
import { Card } from '@/components/ui/card'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Alert } from '@/components/ui/alert'
import { PageSpinner } from '@/components/ui/spinner'

const MINUTE_OPTIONS = [10, 15, 20, 30, 45, 60]

/** Mục tiêu học: cấp độ JLPT nhắm tới, ngày thi, thời gian ôn mỗi ngày - để app tính số từ mới mỗi ngày. */
export function GoalPage() {
  const { data: profile, isLoading, isError, error } = useQuery({
    queryKey: ['profile', 'learning'],
    queryFn: profileApi.getLearning,
  })

  return (
    <div>
      <PageHeader
        title="Mục tiêu học"
        subtitle="App dùng mục tiêu này để tính mỗi ngày nên học bao nhiêu từ mới cho kịp kỳ thi."
      />
      {isLoading && <PageSpinner />}
      {isError && <Alert>{extractErrorMessage(error)}</Alert>}
      {profile && <GoalForm profile={profile} />}
    </div>
  )
}

function GoalForm({ profile }: { profile: LearningProfileResponse }) {
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const [targetLevel, setTargetLevel] = useState<JlptLevel | null>(profile.targetLevel)
  const [examDate, setExamDate] = useState(profile.examDate ?? '')
  const [dailyMinutes, setDailyMinutes] = useState(profile.dailyMinutes)
  // null = để app tự tính số từ mới mỗi ngày.
  const [newWordsPerDay, setNewWordsPerDay] = useState<number | null>(profile.newWordsPerDay)
  // Chốt một lần khi mở trang: hôm nay (chặn chọn ngày đã qua) và hai kỳ JLPT sắp tới để chọn nhanh.
  const [today] = useState(() => toIsoDay(new Date()))
  const [examSuggestions] = useState(() => upcomingJlptDays(2))

  const save = useMutation({
    mutationFn: () =>
      profileApi.updateLearning({ targetLevel, examDate: examDate || null, dailyMinutes, newWordsPerDay }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['profile'] })
      queryClient.invalidateQueries({ queryKey: ['srs'] })
      navigate('/flashcards')
    },
  })

  return (
    <Card className="flex max-w-2xl flex-col gap-6 p-5">
      <section className="flex flex-col gap-2">
        <Label>Cấp độ nhắm tới</Label>
        <div className="flex flex-wrap gap-2">
          {[null, ...JLPT_LEVELS].map((level) => (
            <Choice key={level ?? 'none'} active={targetLevel === level} onClick={() => setTargetLevel(level)}>
              {level ?? 'Chưa chọn'}
            </Choice>
          ))}
        </div>
        <p className="text-sm font-semibold text-muted-foreground">
          Gồm cả từ của các cấp dễ hơn: nhắm N4 là học hết các bài N5 và N4.
        </p>
      </section>

      <section className="flex flex-col gap-2">
        <Label htmlFor="examDate">Ngày thi</Label>
        <div className="flex flex-wrap items-center gap-2">
          <Input
            id="examDate"
            type="date"
            value={examDate}
            min={today}
            onChange={(e) => setExamDate(e.target.value)}
            className="w-auto"
          />
          {examSuggestions.map((day) => (
            <Choice key={day} active={examDate === day} onClick={() => setExamDate(day)}>
              JLPT {formatDay(day)}
            </Choice>
          ))}
          {examDate && (
            <Button variant="ghost" size="sm" onClick={() => setExamDate('')}>
              Bỏ ngày thi
            </Button>
          )}
        </div>
        <p className="text-sm font-semibold text-muted-foreground">
          Kỳ JLPT thường vào Chủ nhật đầu tháng 7 và tháng 12. App để dành 2 tuần cuối cho ôn tổng.
        </p>
      </section>

      <section className="flex flex-col gap-2">
        <Label>Mỗi ngày dành cho ôn tập</Label>
        <div className="flex flex-wrap gap-2">
          {MINUTE_OPTIONS.map((minutes) => (
            <Choice key={minutes} active={dailyMinutes === minutes} onClick={() => setDailyMinutes(minutes)}>
              {minutes} phút
            </Choice>
          ))}
        </div>
      </section>

      <section className="flex flex-col gap-2">
        <Label>Số từ mới mỗi ngày</Label>
        <div className="flex flex-wrap items-center gap-2">
          <Choice active={newWordsPerDay === null} onClick={() => setNewWordsPerDay(null)}>
            Để app tính
          </Choice>
          <Choice active={newWordsPerDay !== null} onClick={() => setNewWordsPerDay(newWordsPerDay ?? 10)}>
            Tự đặt
          </Choice>
          {newWordsPerDay !== null && (
            <Input
              type="number"
              min={0}
              max={100}
              value={newWordsPerDay}
              onChange={(e) => setNewWordsPerDay(Math.max(0, Math.min(100, Number(e.target.value) || 0)))}
              className="w-24"
              aria-label="Số từ mới mỗi ngày"
            />
          )}
        </div>
        <p className="text-sm font-semibold text-muted-foreground">
          {newWordsPerDay === null
            ? 'Có ngày thi thì chia đều số từ còn lại cho tới 2 tuần trước kỳ thi, và bớt đi khi thời gian ôn không đủ.'
            : 'Giữ đúng số này mỗi ngày, kể cả khi đang có nhiều thẻ cần ôn.'}
        </p>
      </section>

      {save.isError && <Alert>{extractErrorMessage(save.error)}</Alert>}
      <div className="flex gap-3">
        <Button size="lg" disabled={save.isPending} onClick={() => save.mutate()}>
          <Target className="h-5 w-5" />
          {save.isPending ? 'Đang lưu...' : 'Lưu mục tiêu'}
        </Button>
        <Button size="lg" variant="ghost" onClick={() => navigate(-1)}>
          Huỷ
        </Button>
      </div>
    </Card>
  )
}

function Choice({ active, onClick, children }: { active: boolean; onClick: () => void; children: ReactNode }) {
  return (
    <button
      type="button"
      onClick={onClick}
      className={cn(
        'h-10 rounded-xl border-2 px-4 text-sm font-extrabold transition-colors',
        active ? 'border-secondary bg-secondary-soft text-secondary-dark' : 'border-border bg-card hover:bg-muted',
      )}
    >
      {children}
    </button>
  )
}
