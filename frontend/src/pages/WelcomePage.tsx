import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { ChevronLeft, Sparkles } from 'lucide-react'
import { profileApi } from '@/api/profile'
import { srsApi } from '@/api/srs'
import { kanjiApi } from '@/api/kanji'
import { extractErrorMessage } from '@/api/client'
import { JLPT_LEVELS, type JlptLevel, type LearningProfileResponse } from '@/api/types'
import { formatDay, toIsoDay, upcomingJlptDays } from '@/lib/dates'
import { lessonFullTitle } from '@/lib/levels'
import { dismissWelcome } from '@/lib/welcome'
import { Choice } from '@/components/Choice'
import { Button } from '@/components/ui/button'
import { Card } from '@/components/ui/card'
import { Input } from '@/components/ui/input'
import { Alert } from '@/components/ui/alert'
import { PageSpinner } from '@/components/ui/spinner'

const MINUTE_OPTIONS = [10, 15, 20, 30, 45, 60]

/**
 * Hướng dẫn lần đầu (ngay sau khi đăng ký, hoặc từ dải nhắc ở trang Học bài): cấp mục tiêu và ngày thi, số phút mỗi
 * ngày, rồi gợi ý bài đầu tiên. Lưu bằng chính hồ sơ học của trang Mục tiêu - các thiết lập khác giữ nguyên.
 */
export function WelcomePage() {
  const { data: profile, isError, error } = useQuery({
    queryKey: ['profile', 'learning'],
    queryFn: profileApi.getLearning,
  })
  if (isError) return <Alert className="m-6">{extractErrorMessage(error)}</Alert>
  if (!profile) return <PageSpinner />
  return <WelcomeSteps profile={profile} />
}

function WelcomeSteps({ profile }: { profile: LearningProfileResponse }) {
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const [step, setStep] = useState(0)
  const [targetLevel, setTargetLevel] = useState<JlptLevel | null>(profile.targetLevel ?? 'N5')
  const [examDate, setExamDate] = useState(profile.examDate ?? '')
  const [dailyMinutes, setDailyMinutes] = useState(profile.dailyMinutes)
  const [today] = useState(() => toIsoDay(new Date()))
  const [examSuggestions] = useState(() => upcomingJlptDays(2))

  const save = useMutation({
    mutationFn: async () => {
      await profileApi.updateLearning({
        targetLevel,
        examDate: examDate || null,
        dailyMinutes,
        newWordsPerDay: profile.newWordsPerDay,
        scheduler: profile.scheduler,
        desiredRetention: profile.desiredRetention,
      })
      // Bài nên học đầu tiên theo mục tiêu vừa chọn (null nếu đã có đủ từ mới chờ học).
      return srsApi.getDailyPlan()
    },
    onSuccess: () => {
      dismissWelcome()
      queryClient.invalidateQueries({ queryKey: ['profile'] })
      queryClient.invalidateQueries({ queryKey: ['srs'] })
      setStep(2)
    },
  })

  // Thêm cả bài vào Ôn tập rồi vào phiên ôn đầu tiên.
  const start = useMutation({
    mutationFn: async (tagId: number) => {
      const words = await kanjiApi.search({ tagId, size: 500 })
      return srsApi.addCards(words.content.map((word) => word.id))
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['srs'] })
      navigate('/flashcards', { replace: true })
    },
  })

  const nextLesson = save.data?.nextLesson ?? null

  return (
    <div className="mx-auto flex min-h-screen max-w-xl flex-col justify-center gap-6 px-4 py-10">
      <div className="flex items-center gap-2">
        {[0, 1, 2].map((i) => (
          <span key={i} className={`h-2 flex-1 rounded-full ${i <= step ? 'bg-secondary' : 'bg-border'}`} />
        ))}
      </div>

      {step === 0 && (
        <Card className="flex flex-col gap-5 p-6">
          <div>
            <h1 className="text-2xl font-black">Chào mừng bạn!</h1>
            <p className="mt-1 font-semibold text-muted-foreground">
              Bạn muốn đạt cấp JLPT nào? App sẽ chia đều từ cần học tới trước ngày thi.
            </p>
          </div>
          <div className="flex flex-wrap gap-2">
            {JLPT_LEVELS.map((level) => (
              <Choice key={level} active={targetLevel === level} onClick={() => setTargetLevel(level)}>
                {level}
              </Choice>
            ))}
          </div>
          <div className="flex flex-col gap-2">
            <p className="text-sm font-extrabold">Ngày thi (có thể để trống)</p>
            <div className="flex flex-wrap items-center gap-2">
              <Input
                type="date"
                value={examDate}
                min={today}
                onChange={(e) => setExamDate(e.target.value)}
                className="w-auto"
                aria-label="Ngày thi"
              />
              {examSuggestions.map((day) => (
                <Choice key={day} active={examDate === day} onClick={() => setExamDate(day)}>
                  JLPT {formatDay(day)}
                </Choice>
              ))}
            </div>
          </div>
          <Button size="lg" onClick={() => setStep(1)}>
            Tiếp tục
          </Button>
        </Card>
      )}

      {step === 1 && (
        <Card className="flex flex-col gap-5 p-6">
          <div>
            <h1 className="text-2xl font-black">Mỗi ngày bạn học được bao lâu?</h1>
            <p className="mt-1 font-semibold text-muted-foreground">
              App xếp số thẻ ôn và từ mới mỗi ngày cho vừa thời gian này. Đổi lại lúc nào cũng được ở trang Mục tiêu.
            </p>
          </div>
          <div className="flex flex-wrap gap-2">
            {MINUTE_OPTIONS.map((minutes) => (
              <Choice key={minutes} active={dailyMinutes === minutes} onClick={() => setDailyMinutes(minutes)}>
                {minutes} phút
              </Choice>
            ))}
          </div>
          {save.isError && <Alert>{extractErrorMessage(save.error)}</Alert>}
          <div className="flex gap-2">
            <Button variant="ghost" size="lg" onClick={() => setStep(0)}>
              <ChevronLeft className="h-5 w-5" /> Quay lại
            </Button>
            <Button size="lg" className="flex-1" disabled={save.isPending} onClick={() => save.mutate()}>
              {save.isPending ? 'Đang lưu...' : 'Lưu mục tiêu'}
            </Button>
          </div>
        </Card>
      )}

      {step === 2 && (
        <Card className="flex flex-col gap-5 p-6">
          <div>
            <Sparkles className="h-8 w-8 text-accent-dark" strokeWidth={2.5} />
            <h1 className="mt-2 text-2xl font-black">Bắt đầu thôi!</h1>
            <p className="mt-1 font-semibold text-muted-foreground">
              {nextLesson
                ? `Gợi ý bài đầu tiên: ${lessonFullTitle(nextLesson.name)} (${nextLesson.words} từ). Thêm vào Ôn tập là học được ngay.`
                : 'Bạn đã có từ để học. Vào Ôn tập để bắt đầu phiên đầu tiên.'}
            </p>
          </div>
          {start.isError && <Alert>{extractErrorMessage(start.error)}</Alert>}
          {nextLesson ? (
            <Button size="lg" disabled={start.isPending} onClick={() => start.mutate(nextLesson.tagId)}>
              {start.isPending ? 'Đang thêm...' : `Thêm ${lessonFullTitle(nextLesson.name)} và bắt đầu`}
            </Button>
          ) : (
            <Button size="lg" onClick={() => navigate('/flashcards', { replace: true })}>
              Vào Ôn tập
            </Button>
          )}
          <Button variant="ghost" size="lg" onClick={() => navigate('/study', { replace: true })}>
            Tự chọn bài
          </Button>
        </Card>
      )}
    </div>
  )
}
