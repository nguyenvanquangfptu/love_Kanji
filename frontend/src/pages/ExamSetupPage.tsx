import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { keepPreviousData, useMutation, useQuery } from '@tanstack/react-query'
import { ArrowRightLeft, BarChart3, FileText, ListChecks, Play, Shuffle, Timer, Trophy } from 'lucide-react'
import { examApi } from '@/api/exam'
import { extractErrorMessage } from '@/api/client'
import { cacheStartedExam } from '@/lib/examCache'
import {
  JLPT_LEVELS,
  type ExamQuestionSource,
  type ExamSectionName,
  type JlptLevel,
  type JlptLevelResponse,
} from '@/api/types'
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

const QUESTION_SOURCES: { value: ExamQuestionSource | undefined; label: string; description: string }[] = [
  { value: undefined, label: 'Tất cả câu đã duyệt', description: 'Đề tự soạn, câu soạn tay, câu sinh từ kho từ' },
  { value: 'IMPORTED', label: 'Chỉ đề tự soạn', description: 'Ghép từ kho các đề đã nhập, mỗi 問題 đủ số câu' },
]

/** Đề JLPT: chọn cấp độ và các phần; bảng cấu trúc đề cho biết số câu hiện có của từng 問題. */
function JlptSetup() {
  const navigate = useNavigate()
  const [level, setLevel] = useState<string | null>(null)
  const [unchecked, setUnchecked] = useState<ExamSectionName[]>([])
  /** undefined = mọi câu đã duyệt; IMPORTED = chỉ câu của các đề tự soạn. */
  const [source, setSource] = useState<ExamQuestionSource | undefined>(undefined)
  /** Phần tự đặt giờ → số phút đang gõ (chuỗi để gõ dở được); phần không có ở đây theo giờ đề thật. */
  const [customMinutes, setCustomMinutes] = useState<Partial<Record<ExamSectionName, string>>>({})

  const levelsQuery = useQuery({
    queryKey: ['jlpt-levels', source ?? 'ALL'],
    queryFn: () => examApi.jlptLevels(source),
    placeholderData: keepPreviousData,
  })
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
  // Giờ tự đặt của các phần đã chọn; phần gõ chưa hợp lệ thì chưa cho bắt đầu.
  const timed = chosen.filter((s) => customMinutes[s.name] !== undefined)
  const invalidTime = timed.some((s) => !validMinutes(customMinutes[s.name]))
  const minutes = timed.length
    ? Object.fromEntries(timed.map((s) => [s.name, Number(customMinutes[s.name])]))
    : undefined

  function toggle(name: ExamSectionName) {
    setUnchecked((prev) => (prev.includes(name) ? prev.filter((n) => n !== name) : [...prev, name]))
  }

  function setTime(name: ExamSectionName, value: string | undefined) {
    setCustomMinutes((prev) => {
      const next = { ...prev }
      if (value === undefined) delete next[name]
      else next[name] = value
      return next
    })
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
          <h2 className="mb-3 font-black">2. Nguồn câu hỏi</h2>
          <div className="grid gap-2 sm:grid-cols-2" role="radiogroup" aria-label="Nguồn câu hỏi">
            {QUESTION_SOURCES.map((option) => {
              const selected = source === option.value
              return (
                <button
                  key={option.label}
                  type="button"
                  role="radio"
                  aria-checked={selected}
                  onClick={() => setSource(option.value)}
                  className={cn(
                    'rounded-2xl border-2 px-4 py-3 text-left',
                    selected ? 'border-secondary bg-secondary-soft' : 'border-border bg-card hover:bg-muted',
                  )}
                >
                  <span className="block font-black">{option.label}</span>
                  <span className="block text-sm font-semibold text-muted-foreground">{option.description}</span>
                </button>
              )
            })}
          </div>
        </section>

        <section>
          <h2 className="mb-3 font-black">3. Chọn phần thi</h2>
          <div className="flex flex-col gap-2">
            {current.sections.map((s) => {
              const ready = s.questionCount > 0
              const checked = ready && !unchecked.includes(s.name)
              return (
                <div key={s.name} className="flex flex-col gap-2">
                  <label
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
                  {checked && (
                    <SectionTime
                      sectionLabel={SECTION_META[s.name].vi}
                      standardMinutes={s.minutes}
                      value={customMinutes[s.name]}
                      onChange={(value) => setTime(s.name, value)}
                    />
                  )}
                </div>
              )
            })}
          </div>
          {timed.length > 0 && (
            <p className="mt-2 rounded-xl bg-orange-soft px-3 py-2 text-xs font-semibold text-orange-dark">
              Có phần tự đặt giờ: kết quả vẫn được chấm và lưu, nhưng không tính vào bảng xếp hạng.
            </p>
          )}
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
          disabled={startMutation.isPending || chosen.length === 0 || invalidTime}
          onClick={() =>
            startMutation.mutate({
              jlptLevel: current.jlptLevel,
              sections: chosen.map((s) => s.name),
              source,
              minutes,
            })
          }
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

const MIN_MINUTES = 5
const MAX_MINUTES = 120

/** Số phút tự đặt hợp lệ: số nguyên trong khoảng cho phép. */
function validMinutes(value: string | undefined) {
  if (value === undefined || !/^\d+$/.test(value.trim())) return false
  const minutes = Number(value)
  return minutes >= MIN_MINUTES && minutes <= MAX_MINUTES
}

/** Đồng hồ của một phần: giờ chuẩn (theo đề thật) hoặc tự đặt số phút. */
function SectionTime({
  sectionLabel,
  standardMinutes,
  value,
  onChange,
}: {
  sectionLabel: string
  standardMinutes: number
  /** undefined = giờ chuẩn. */
  value: string | undefined
  onChange: (value: string | undefined) => void
}) {
  const custom = value !== undefined
  const chip = (selected: boolean) =>
    cn(
      'rounded-xl border-2 px-3 py-1.5 text-sm font-extrabold',
      selected ? 'border-secondary bg-secondary-soft text-secondary-dark' : 'border-border bg-card',
    )
  return (
    <div className="flex flex-wrap items-center gap-2 pl-4" role="group" aria-label={`Thời gian phần ${sectionLabel}`}>
      <Timer className="h-4 w-4 shrink-0 text-orange" strokeWidth={3} aria-hidden />
      <button type="button" aria-pressed={!custom} className={chip(!custom)} onClick={() => onChange(undefined)}>
        Giờ chuẩn · {standardMinutes} phút
      </button>
      <button
        type="button"
        aria-pressed={custom}
        className={chip(custom)}
        onClick={() => onChange(value ?? String(standardMinutes))}
      >
        Tự đặt
      </button>
      {custom && (
        <label className="flex items-center gap-1.5 text-sm font-bold">
          <input
            type="number"
            inputMode="numeric"
            min={MIN_MINUTES}
            max={MAX_MINUTES}
            value={value}
            onChange={(e) => onChange(e.target.value)}
            aria-label={`Số phút phần ${sectionLabel}`}
            aria-invalid={!validMinutes(value)}
            className={cn(
              'h-9 w-20 rounded-xl border-2 bg-card px-2 text-center font-black',
              validMinutes(value) ? 'border-border' : 'border-destructive',
            )}
          />
          phút
          {!validMinutes(value) && (
            <span className="text-destructive-dark">
              ({MIN_MINUTES}-{MAX_MINUTES} phút)
            </span>
          )}
        </label>
      )}
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
