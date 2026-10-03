import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { useMutation, useQuery } from '@tanstack/react-query'
import { ArrowRightLeft, BarChart3, FileText, ListChecks, Play, Shuffle, Timer, Trophy } from 'lucide-react'
import { examApi } from '@/api/exam'
import { extractErrorMessage } from '@/api/client'
import { cacheStartedExam } from '@/lib/examCache'
import { JLPT_LEVELS, type ExamSectionName, type JlptLevel, type JlptLevelResponse } from '@/api/types'
import { LEVEL_META } from '@/lib/levels'
import { QUESTION_TYPE_META, SECTION_META } from '@/lib/jlpt'
import { cn } from '@/lib/utils'
import { Button } from '@/components/ui/button'
import { Alert } from '@/components/ui/alert'
import { Card } from '@/components/ui/card'
import { PageSpinner } from '@/components/ui/spinner'
import { PageHeader } from '@/components/PageHeader'
import { Leaderboard } from '@/components/Leaderboard'
import { JlptLeaderboard } from '@/components/JlptLeaderboard'

type Mode = 'jlpt' | 'quick'

const MODES: { value: Mode; title: string; detail: string; icon: typeof FileText }[] = [
  {
    value: 'jlpt',
    title: 'Đề JLPT',
    detail: 'Từ vựng + Ngữ pháp theo cấu trúc đề thật, mỗi phần có giờ riêng.',
    icon: FileText,
  },
  {
    value: 'quick',
    title: 'Thi nhanh',
    detail: 'Trộn ngẫu nhiên đọc, viết, nghĩa; có bảng xếp hạng.',
    icon: Shuffle,
  },
]

const QUESTION_COUNTS = [10, 20, 30, 50]

export function ExamSetupPage() {
  const [mode, setMode] = useState<Mode>('jlpt')

  return (
    <div>
      <PageHeader title="Thi thử JLPT" subtitle="Làm đề theo cấu trúc đề thật, hoặc thi nhanh để leo bảng xếp hạng." />

      <div className="mb-6 grid gap-3 sm:grid-cols-2" role="radiogroup" aria-label="Kiểu thi">
        {MODES.map(({ value, title, detail, icon: Icon }) => (
          <button
            key={value}
            type="button"
            role="radio"
            aria-checked={mode === value}
            onClick={() => setMode(value)}
            className={cn(
              'flex items-start gap-3 rounded-2xl border-2 border-b-4 p-4 text-left transition-all active:translate-y-[2px] active:border-b-2',
              mode === value ? 'border-secondary bg-secondary-soft' : 'border-border bg-card hover:bg-muted',
            )}
          >
            <Icon
              className={cn('mt-0.5 h-6 w-6 shrink-0', mode === value ? 'text-secondary-dark' : 'text-muted-foreground')}
              strokeWidth={2.5}
            />
            <span>
              <span className={cn('block font-black', mode === value && 'text-secondary-dark')}>{title}</span>
              <span className="text-sm font-semibold text-muted-foreground">{detail}</span>
            </span>
          </button>
        ))}
      </div>

      {mode === 'jlpt' ? <JlptSetup /> : <QuickSetup />}
    </div>
  )
}

/** Đề JLPT: chọn cấp độ và các phần; bảng cấu trúc đề cho biết số câu hiện có của từng 問題. */
function JlptSetup() {
  const navigate = useNavigate()
  const [level, setLevel] = useState<string | null>(null)
  const [unchecked, setUnchecked] = useState<ExamSectionName[]>([])

  const levelsQuery = useQuery({ queryKey: ['jlpt-levels'], queryFn: examApi.jlptLevels })
  const startMutation = useMutation({
    mutationFn: examApi.startSitting,
    onSuccess: (data) => {
      cacheStartedExam(data)
      navigate(`/exam/${data.attemptId}`)
    },
  })

  if (levelsQuery.isLoading) return <PageSpinner label="Đang tải cấu trúc đề..." />
  if (levelsQuery.isError || !levelsQuery.data) return <Alert>{extractErrorMessage(levelsQuery.error)}</Alert>

  const levels = levelsQuery.data
  // Mặc định cấp độ đầu tiên đã có câu hỏi.
  const current =
    levels.find((l) => l.jlptLevel === level) ??
    levels.find((l) => l.sections.some((s) => s.questionCount > 0)) ??
    levels[0]
  const chosen = current.sections.filter((s) => s.questionCount > 0 && !unchecked.includes(s.name))

  function toggle(name: ExamSectionName) {
    setUnchecked((prev) => (prev.includes(name) ? prev.filter((n) => n !== name) : [...prev, name]))
  }

  return (
    <div className="grid gap-6 lg:grid-cols-[1fr_380px]">
      <Card className="flex flex-col gap-6 p-5 sm:p-6">
        {startMutation.isError && <Alert>{extractErrorMessage(startMutation.error)}</Alert>}

        <section>
          <h2 className="mb-3 font-black">1. Chọn cấp độ</h2>
          <div className="grid grid-cols-5 gap-2">
            {JLPT_LEVELS.map((lv) => {
              const available = levels.some((l) => l.jlptLevel === lv)
              const selected = current.jlptLevel === lv
              return (
                <button
                  key={lv}
                  type="button"
                  aria-pressed={selected}
                  disabled={!available}
                  title={available ? undefined : 'Chưa có cấu trúc đề cho cấp độ này'}
                  onClick={() => setLevel(lv)}
                  className={cn(
                    'rounded-2xl border-2 border-b-4 py-3 text-lg font-black transition-all active:translate-y-[2px] active:border-b-2 disabled:cursor-not-allowed disabled:opacity-40',
                    selected ? LEVEL_META[lv as JlptLevel].style.solid : 'border-border bg-card hover:bg-muted',
                  )}
                >
                  {lv}
                </button>
              )
            })}
          </div>
        </section>

        <section>
          <h2 className="mb-3 font-black">2. Chọn phần thi</h2>
          <div className="flex flex-col gap-2">
            {current.sections.map((s) => {
              const ready = s.questionCount > 0
              const checked = ready && !unchecked.includes(s.name)
              return (
                <label
                  key={s.name}
                  className={cn(
                    'flex items-center gap-3 rounded-2xl border-2 px-4 py-3',
                    checked ? 'border-secondary bg-secondary-soft' : 'border-border bg-card',
                    ready ? 'cursor-pointer' : 'cursor-not-allowed opacity-60',
                  )}
                >
                  <input
                    type="checkbox"
                    className="h-5 w-5 accent-secondary"
                    checked={checked}
                    disabled={!ready}
                    onChange={() => toggle(s.name)}
                  />
                  <span className="min-w-0 flex-1">
                    <span className="block font-jp font-black">{SECTION_META[s.name].jp}</span>
                    <span className="block text-sm font-bold">
                      {SECTION_META[s.name].vi} ·{' '}
                      {ready ? (
                        `${s.questionCount} câu · ${s.minutes} phút`
                      ) : (
                        <span className="text-muted-foreground">Chưa có câu hỏi</span>
                      )}
                    </span>
                    {ready && s.questionCount < s.plannedQuestions && (
                      <span className="block text-xs font-semibold text-muted-foreground">
                        Đề thật {s.plannedQuestions} câu · {s.plannedMinutes} phút
                      </span>
                    )}
                  </span>
                </label>
              )
            })}
          </div>
        </section>

        <ul className="flex flex-col gap-2 text-sm font-bold text-muted-foreground">
          <li className="flex items-center gap-2">
            <Timer className="h-4 w-4 shrink-0 text-orange" strokeWidth={3} /> Mỗi phần một đồng hồ riêng, hết giờ tự nộp
          </li>
          <li className="flex items-center gap-2">
            <ArrowRightLeft className="h-4 w-4 shrink-0 text-secondary" strokeWidth={3} /> Nộp phần trước mới sang phần
            sau, không quay lại được
          </li>
          <li className="flex items-center gap-2">
            <BarChart3 className="h-4 w-4 shrink-0 text-accent-dark" strokeWidth={3} /> Kết quả theo từng 問題
          </li>
        </ul>

        <Button
          size="lg"
          className="w-full"
          disabled={startMutation.isPending || chosen.length === 0}
          onClick={() => startMutation.mutate({ jlptLevel: current.jlptLevel, sections: chosen.map((s) => s.name) })}
        >
          <Play className="h-5 w-5 fill-current" />
          {startMutation.isPending ? 'Đang ghép đề...' : `Bắt đầu đề ${current.jlptLevel}`}
        </Button>
      </Card>

      <div className="flex flex-col gap-6">
        <StructureCard level={current} />
        <Card className="p-5">
          <h2 className="mb-4 flex items-center gap-2 font-black">
            <Trophy className="h-5 w-5 text-accent-dark" strokeWidth={3} /> Bảng xếp hạng đề {current.jlptLevel}
          </h2>
          <JlptLeaderboard level={current.jlptLevel} />
        </Card>
      </div>
    </div>
  )
}

/** Cấu trúc đề của cấp độ: các 問題 với số câu đề thật và số câu đề này lấy được. */
function StructureCard({ level }: { level: JlptLevelResponse }) {
  const shortened = level.sections.some((s) => s.questionCount > 0 && s.questionCount < s.plannedQuestions)
  return (
    <Card className="p-5">
      <h2 className="font-black">Cấu trúc đề {level.jlptLevel}</h2>
      <p className="text-sm font-semibold text-muted-foreground">Số câu lấy được / số câu của đề thật.</p>
      <div className="mt-4 flex flex-col gap-5">
        {level.sections.map((s) => (
          <section key={s.name}>
            <h3 className="flex items-baseline justify-between gap-2 border-b-2 border-border pb-1.5">
              <span className="font-jp text-sm font-black">{SECTION_META[s.name].jp}</span>
              <span className="shrink-0 text-xs font-bold text-muted-foreground">{s.plannedMinutes} phút</span>
            </h3>
            <ul className="mt-2 flex flex-col gap-1.5 text-sm">
              {s.mondai.map((m) => {
                const taken = Math.min(m.available, m.plannedCount)
                return (
                  <li key={m.number} className="flex items-baseline gap-2">
                    <span className="w-12 shrink-0 font-jp font-bold">問題{m.number}</span>
                    <span className="min-w-0 flex-1">
                      <span className="font-jp font-bold">{QUESTION_TYPE_META[m.type].jp}</span>
                      <span className="text-muted-foreground"> · {QUESTION_TYPE_META[m.type].vi}</span>
                    </span>
                    <span
                      className={cn(
                        'shrink-0 font-black tabular-nums',
                        taken === 0 ? 'text-muted-foreground' : taken < m.plannedCount && 'text-orange-dark',
                      )}
                    >
                      {taken}/{m.plannedCount}
                    </span>
                  </li>
                )
              })}
            </ul>
          </section>
        ))}
      </div>
      {shortened && (
        <p className="mt-4 rounded-xl bg-orange-soft px-3 py-2 text-xs font-semibold text-orange-dark">
          Ngân hàng câu hỏi chưa có đủ mọi dạng nên đề ngắn hơn đề thật; thời gian giảm theo số câu.
        </p>
      )}
    </Card>
  )
}

/** Thi nhanh: số câu tuỳ chọn, trộn các kỹ năng, điểm lên bảng xếp hạng. */
function QuickSetup() {
  const [level, setLevel] = useState<JlptLevel>('N5')
  const [questionCount, setQuestionCount] = useState(20)
  const navigate = useNavigate()

  const startMutation = useMutation({
    mutationFn: examApi.start,
    onSuccess: (data) => {
      cacheStartedExam(data)
      navigate(`/exam/${data.attemptId}`)
    },
  })

  return (
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
  )
}
