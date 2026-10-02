import { type ReactNode, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Target } from 'lucide-react'
import { profileApi } from '@/api/profile'
import { extractErrorMessage } from '@/api/client'
import {
  JLPT_LEVELS,
  type FsrsParametersResponse,
  type JlptLevel,
  type LearningProfileResponse,
  type Scheduler,
} from '@/api/types'
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
const RETENTION_OPTIONS = [0.8, 0.85, 0.9, 0.95]
const RATING_LABELS = ['Quên', 'Khó', 'Nhớ', 'Dễ']

// Đường quên của FSRS-6 với thông số chung: còn nhớ R = (1 + FACTOR·t/S)^DECAY sau t ngày (S: số ngày còn nhớ 90%).
const FSRS_DECAY = -0.1542
const FSRS_FACTOR = 0.9 ** (1 / FSRS_DECAY) - 1

/** Số ngày tới lần ôn sau để lúc đó còn nhớ `retention`, với một từ có độ ổn định `stability` ngày. */
function fsrsInterval(stability: number, retention: number) {
  return Math.max(1, Math.round((stability / FSRS_FACTOR) * (retention ** (1 / FSRS_DECAY) - 1)))
}

function formatDays(days: number) {
  return days.toLocaleString('vi-VN', { maximumFractionDigits: 1 })
}

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
  const [scheduler, setScheduler] = useState<Scheduler>(profile.scheduler)
  const [desiredRetention, setDesiredRetention] = useState(profile.desiredRetention)
  // Chốt một lần khi mở trang: hôm nay (chặn chọn ngày đã qua) và hai kỳ JLPT sắp tới để chọn nhanh.
  const [today] = useState(() => toIsoDay(new Date()))
  const [examSuggestions] = useState(() => upcomingJlptDays(2))

  const save = useMutation({
    mutationFn: () =>
      profileApi.updateLearning({
        targetLevel,
        examDate: examDate || null,
        dailyMinutes,
        newWordsPerDay,
        scheduler,
        desiredRetention,
      }),
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

      <section className="flex flex-col gap-2">
        <Label>Cách xếp lịch ôn</Label>
        <div className="flex flex-wrap gap-2">
          <Choice active={scheduler === 'FSRS'} onClick={() => setScheduler('FSRS')}>
            FSRS (khuyên dùng)
          </Choice>
          <Choice active={scheduler === 'SM2'} onClick={() => setScheduler('SM2')}>
            SM-2
          </Choice>
        </div>
        <p className="text-sm font-semibold text-muted-foreground">
          {scheduler === 'FSRS'
            ? 'Ước lượng khả năng bạn còn nhớ từng từ và hẹn ôn lại đúng lúc sắp quên - thường ít lượt ôn hơn SM-2 mà vẫn nhớ chắc như vậy.'
            : 'Thuật toán cổ điển: mỗi lần nhớ được thì khoảng ôn nhân lên theo độ dễ của từ.'}{' '}
          Đổi cách xếp lịch thì áp dụng từ lần ôn tới của mỗi từ.
        </p>
        {scheduler === 'FSRS' && (
          <>
            <Label className="mt-2">Tỉ lệ nhớ mong muốn</Label>
            <div className="flex flex-wrap gap-2">
              {RETENTION_OPTIONS.map((retention) => (
                <Choice
                  key={retention}
                  active={Math.abs(desiredRetention - retention) < 0.001}
                  onClick={() => setDesiredRetention(retention)}
                >
                  {Math.round(retention * 100)}%
                </Choice>
              ))}
            </div>
            <p className="text-sm font-semibold text-muted-foreground">
              Đến lượt ôn, bạn còn nhớ chừng ấy phần trăm số từ. Càng cao càng chắc nhưng phải ôn nhiều hơn hẳn; 90% là
              mức cân bằng. Ví dụ một từ sau 10 ngày bạn vẫn còn 90% khả năng nhớ: ôn lại sau{' '}
              {fsrsInterval(10, desiredRetention)} ngày.
            </p>
            <MemoryModel fsrs={profile.fsrs} />
          </>
        )}
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

/** Thông số FSRS đang dùng: chung hay đã tối ưu theo trí nhớ của người học, kèm nút tối ưu ngay. */
function MemoryModel({ fsrs }: { fsrs: FsrsParametersResponse }) {
  const queryClient = useQueryClient()
  const optimize = useMutation({
    mutationFn: profileApi.optimizeFsrs,
    onSuccess: (next) => {
      queryClient.setQueryData<LearningProfileResponse>(['profile', 'learning'], (old) => old && { ...old, fsrs: next })
      queryClient.invalidateQueries({ queryKey: ['srs'] })
    },
  })
  const learned = fsrs.firstReviews.reduce((sum, count) => sum + count, 0)

  return (
    <div className="mt-2 flex flex-col gap-3 rounded-xl border-2 border-border p-4">
      <p className="text-sm font-extrabold">Trí nhớ của riêng bạn</p>
      <p className="text-sm font-semibold text-muted-foreground">
        {fsrs.personalized ? (
          <>
            FSRS đã được chỉnh theo {learned} từ bạn đã học và ôn lại
            {fsrs.optimizedAt && ` (cập nhật ${formatDay(fsrs.optimizedAt.slice(0, 10))})`}. Bảng dưới là số ngày bạn
            còn nhớ 90% một từ mới, tuỳ lần đầu bạn chấm nó thế nào.
          </>
        ) : (
          <>
            Đang dùng thông số chung của FSRS. Khi một mức chấm có đủ {fsrs.minFirstReviews} từ bạn học lần đầu với
            mức đó và đã ôn lại vào hôm khác, app chỉnh FSRS theo trí nhớ của bạn (tự chạy mỗi sáng thứ Hai).
          </>
        )}
      </p>
      <div className="overflow-x-auto">
        <table className="w-full text-sm">
          <thead>
            <tr className="text-muted-foreground">
              <th className="py-1 pr-3 text-left font-bold">Lần đầu chấm</th>
              {RATING_LABELS.map((label) => (
                <th key={label} className="px-2 py-1 text-right font-bold">
                  {label}
                </th>
              ))}
            </tr>
          </thead>
          <tbody className="font-semibold">
            {fsrs.personalized && (
              <tr className="border-t border-border">
                <th className="py-1 pr-3 text-left font-bold">Bạn</th>
                {fsrs.initialStabilities.map((days, i) => (
                  <td key={i} className="px-2 py-1 text-right tabular-nums">
                    {formatDays(days)} ngày
                  </td>
                ))}
              </tr>
            )}
            <tr className="border-t border-border">
              <th className="py-1 pr-3 text-left font-bold">Người học nói chung</th>
              {fsrs.defaultInitialStabilities.map((days, i) => (
                <td key={i} className="px-2 py-1 text-right tabular-nums">
                  {formatDays(days)} ngày
                </td>
              ))}
            </tr>
            <tr className="border-t border-border text-muted-foreground">
              <th className="py-1 pr-3 text-left font-bold">Từ đã học và ôn lại</th>
              {fsrs.firstReviews.map((count, i) => (
                <td key={i} className="px-2 py-1 text-right tabular-nums">
                  {count}/{fsrs.minFirstReviews}
                </td>
              ))}
            </tr>
          </tbody>
        </table>
      </div>
      <div className="flex flex-wrap items-center gap-3">
        <Button variant="secondary" size="sm" disabled={optimize.isPending} onClick={() => optimize.mutate()}>
          {optimize.isPending ? 'Đang tối ưu...' : 'Tối ưu ngay'}
        </Button>
        {optimize.isSuccess && (
          <span className="text-sm font-semibold text-muted-foreground">
            {optimize.data.personalized ? 'Đã cập nhật theo dữ liệu mới nhất.' : 'Chưa mức chấm nào đủ dữ liệu.'}
          </span>
        )}
        {optimize.isError && (
          <span className="text-sm font-semibold text-destructive">{extractErrorMessage(optimize.error)}</span>
        )}
      </div>
    </div>
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
