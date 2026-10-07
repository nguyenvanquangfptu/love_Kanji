import { useEffect, useRef, useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { useMutation } from '@tanstack/react-query'
import { ChevronLeft, ChevronRight, Send } from 'lucide-react'
import { examApi } from '@/api/exam'
import { extractErrorMessage } from '@/api/client'
import { getCachedExamQuestions } from '@/lib/examCache'
import { mondaiAt, mondaiRanges, QUESTION_TYPE_META, SECTION_META, type MondaiRange } from '@/lib/jlpt'
import type { ExamPassage, ExamQuestionPublicResponse, ExamSectionName } from '@/api/types'
import { Countdown } from '@/components/Countdown'
import { QuestionPalette } from '@/components/QuestionPalette'
import { SentenceWithTarget } from '@/components/SentenceWithTarget'
import { PassageText } from '@/components/PassageText'
import { Button } from '@/components/ui/button'
import { Card } from '@/components/ui/card'
import { Alert } from '@/components/ui/alert'
import { Progress } from '@/components/ui/progress'
import { PageSpinner } from '@/components/ui/spinner'
import { ConfirmDialog } from '@/components/ui/confirm-dialog'
import { cn } from '@/lib/utils'

const OPTIONS = ['A', 'B', 'C', 'D'] as const

export function ExamWorkspacePage() {
  const { attemptId: attemptIdParam } = useParams()
  const attemptId = Number(attemptIdParam)
  const navigate = useNavigate()

  const [questions, setQuestions] = useState<ExamQuestionPublicResponse[] | null>(null)
  // Phần đề JLPT: các 問題 (câu xếp liền nhau theo thứ tự) và tên phần; rỗng/null với thi nhanh.
  const [mondai, setMondai] = useState<MondaiRange[]>([])
  // Đoạn văn của các câu 文章の文法.
  const [passages, setPassages] = useState<ExamPassage[]>([])
  const [section, setSection] = useState<ExamSectionName | null>(null)
  const [answers, setAnswers] = useState<Record<number, string>>({})
  const [currentIndex, setCurrentIndex] = useState(0)
  const [remainingSeconds, setRemainingSeconds] = useState<number | null>(null)
  const [sessionLoaded, setSessionLoaded] = useState(false)
  const [loadError, setLoadError] = useState<string | null>(null)
  const [locked, setLocked] = useState(false)
  const [autoSubmitNotice, setAutoSubmitNotice] = useState(false)
  const [confirmingSubmit, setConfirmingSubmit] = useState(false)

  const saveTimers = useRef(new Map<number, ReturnType<typeof setTimeout>>())
  // Mốc hết giờ trên đồng hồ đơn điệu của trình duyệt (performance.now()), tính từ remainingSeconds Backend trả về.
  const deadline = useRef<number | null>(null)
  const autoSubmitted = useRef(false)

  useEffect(() => {
    let cancelled = false

    async function load() {
      // GET /session luôn là nguồn sự thật cho remainingSeconds + answers (kể cả
      // ngay sau khi vừa bắt đầu thi) - tránh dùng lại state điều hướng cũ có thể
      // stale nếu component bị remount (F5, HMR) sau khi đã tự động lưu vài đáp án.
      const cached = getCachedExamQuestions(attemptId)
      try {
        const session = await examApi.getSession(attemptId)
        if (cancelled) return
        if (cached) {
          setQuestions(cached.questions)
          setMondai(mondaiRanges(cached.mondai))
          setPassages(cached.passages ?? [])
        }
        setSection(session.section)
        setAnswers(Object.fromEntries(Object.entries(session.answers).map(([k, v]) => [Number(k), v])))
        deadline.current = performance.now() + session.remainingSeconds * 1000
        setRemainingSeconds(session.remainingSeconds)
        setSessionLoaded(true)
      } catch (err) {
        if (!cancelled) setLoadError(extractErrorMessage(err))
      }
    }

    load()
    return () => {
      cancelled = true
    }
  }, [attemptId])

  // Nộp xong: phần đề JLPT thì về buổi thi (nghỉ giữa giờ, phần tiếp theo), thi nhanh thì sang trang kết quả.
  const afterSubmit = (sittingId: number | null) =>
    sittingId ? `/exam/jlpt/${sittingId}` : `/exam/${attemptId}/result`

  const submitMutation = useMutation({
    mutationFn: () => examApi.submit(attemptId),
    onSuccess: (result) => navigate(afterSubmit(result.sittingId), { replace: true }),
    onError: async () => {
      // Máy chủ có thể đã tự chốt bài khi hết giờ (Redis, job dự phòng) trước khi trình duyệt kịp nộp: bài đã có
      // kết quả thì sang trang kết quả luôn.
      try {
        const review = await examApi.getReview(attemptId)
        navigate(afterSubmit(review.sittingId), { replace: true })
      } catch {
        // Nộp bài lỗi (mất mạng, backend gián đoạn...) không được để người dùng kẹt
        // vĩnh viễn ở trạng thái khoá (locked=true) mà không có cách nộp lại.
        setLocked(false)
      }
    },
  })
  const submitExam = submitMutation.mutate

  // Đếm ngược tới mốc hết giờ tính từ remainingSeconds Backend trả về, theo đồng hồ đơn điệu performance.now() chứ
  // KHÔNG theo giờ máy client (new Date()) để tránh lệch giờ do clock drift. Mỗi nhịp tính lại từ mốc, nên tab bị
  // trình duyệt hãm timer (chạy nền) vẫn hiện đúng thời gian còn lại. Hết giờ thì tự nộp một lần; lỗi thì để người
  // dùng bấm nộp lại.
  useEffect(() => {
    if (!sessionLoaded || locked || autoSubmitted.current || deadline.current === null) return
    const tick = () => {
      const left = Math.max(0, Math.ceil((deadline.current! - performance.now()) / 1000))
      setRemainingSeconds(left)
      if (left === 0 && !autoSubmitted.current) {
        autoSubmitted.current = true
        setLocked(true)
        setAutoSubmitNotice(true)
        submitExam()
      }
    }
    tick()
    const t = setInterval(tick, 1000)
    return () => clearInterval(t)
  }, [sessionLoaded, locked, submitExam])

  // Cảnh báo rời trang - chỉ là lớp UX best-effort (trình duyệt hiện đại không
  // cho custom message, mobile Safari phần lớn bỏ qua). Lớp bảo vệ dữ liệu
  // thật sự nằm ở Backend: TTL buffer + Reconciliation Job.
  useEffect(() => {
    if (locked) return
    function handler(e: BeforeUnloadEvent) {
      e.preventDefault()
      e.returnValue = ''
    }
    window.addEventListener('beforeunload', handler)
    return () => window.removeEventListener('beforeunload', handler)
  }, [locked])

  useEffect(() => {
    const timers = saveTimers.current
    return () => {
      timers.forEach(clearTimeout)
    }
  }, [])

  function selectOption(questionId: number, option: string) {
    if (locked) return
    setAnswers((prev) => ({ ...prev, [questionId]: option }))

    const timers = saveTimers.current
    const existing = timers.get(questionId)
    if (existing) clearTimeout(existing)
    timers.set(
      questionId,
      setTimeout(() => {
        examApi.saveAnswer(attemptId, { questionId, selectedOption: option as 'A' | 'B' | 'C' | 'D' }).catch(() => {
          // Auto-save best-effort: lỗi mạng tạm thời sẽ tự thử lại ở lần chọn kế tiếp hoặc lúc nộp bài
        })
        timers.delete(questionId)
      }, 300),
    )
  }

  function handleManualSubmit() {
    setConfirmingSubmit(true)
  }

  function confirmSubmit() {
    setConfirmingSubmit(false)
    setLocked(true)
    submitMutation.mutate()
  }

  const total = questions?.length ?? 0

  // Phím tắt: A-D hoặc 1-4 chọn đáp án, ← → chuyển câu.
  useEffect(() => {
    if (!questions || locked || confirmingSubmit) return
    function onKeyDown(e: KeyboardEvent) {
      if (!questions) return
      const key = e.key.toUpperCase()
      const optionIndex = /^[1-4]$/.test(key) ? Number(key) - 1 : OPTIONS.indexOf(key as (typeof OPTIONS)[number])
      if (optionIndex >= 0) {
        selectOption(questions[currentIndex].id, OPTIONS[optionIndex])
      } else if (e.key === 'ArrowRight') {
        setCurrentIndex((i) => Math.min(total - 1, i + 1))
      } else if (e.key === 'ArrowLeft') {
        setCurrentIndex((i) => Math.max(0, i - 1))
      }
    }
    window.addEventListener('keydown', onKeyDown)
    return () => window.removeEventListener('keydown', onKeyDown)
    // selectOption chỉ phụ thuộc state đã liệt kê - bỏ khỏi deps để listener không gắn lại mỗi lần render.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [questions, locked, confirmingSubmit, currentIndex, total])

  if (loadError) {
    return (
      <div className="mx-auto max-w-md px-4 py-16">
        <Alert>{loadError}</Alert>
        <Link to="/exam" className="mt-4 inline-block font-extrabold text-secondary hover:underline">
          Quay lại trang thi thử
        </Link>
      </div>
    )
  }

  if (!sessionLoaded) return <PageSpinner label="Đang tải bài thi..." />

  const answeredCount = Object.keys(answers).length
  const unansweredCount = Math.max(0, total - answeredCount)

  const confirmDialog = (
    <ConfirmDialog
      open={confirmingSubmit}
      title={section ? `Nộp phần ${SECTION_META[section].vi}?` : 'Nộp bài ngay bây giờ?'}
      description={
        (unansweredCount > 0
          ? `Bạn còn ${unansweredCount} câu chưa làm. Sau khi nộp sẽ không thể sửa đáp án nữa.`
          : 'Sau khi nộp, bạn sẽ không thể chỉnh sửa đáp án nữa.') +
        (section ? ' Như đề thật, nộp rồi thì không quay lại phần này được.' : '')
      }
      confirmLabel="Nộp bài"
      onConfirm={confirmSubmit}
      onCancel={() => setConfirmingSubmit(false)}
    />
  )

  const submitButton = (
    <Button
      variant="destructive"
      size="sm"
      onClick={handleManualSubmit}
      disabled={locked || submitMutation.isPending}
    >
      <Send className="h-4 w-4" />
      {submitMutation.isPending ? 'Đang nộp...' : submitMutation.isError ? 'Thử nộp lại' : 'Nộp bài'}
    </Button>
  )

  const header = (
    <header className="sticky top-0 z-20 border-b-2 border-border bg-background/95 backdrop-blur">
      <div className="mx-auto flex max-w-5xl items-center gap-3 px-4 py-3">
        {remainingSeconds !== null && <Countdown seconds={remainingSeconds} />}
        <div className="flex flex-1 items-center gap-2">
          <Progress value={total > 0 ? (answeredCount / total) * 100 : 0} className="h-3" barClassName="bg-secondary" />
          {total > 0 && (
            <span className="shrink-0 text-xs font-extrabold text-muted-foreground">
              {answeredCount}/{total}
            </span>
          )}
        </div>
        {submitButton}
      </div>
    </header>
  )

  if (!questions) {
    return (
      <>
        {header}
        <div className="mx-auto max-w-md px-4 py-10">
          <Card className="flex flex-col items-center gap-4 p-6 text-center">
            <p className="font-semibold text-muted-foreground">
              Không thể khôi phục nội dung câu hỏi sau khi tải lại trang (nội dung câu hỏi chỉ được lưu tạm trên
              trình duyệt). Bạn vẫn có thể nộp bài với các đáp án đã lưu.
            </p>
            {submitMutation.isError && <Alert>{extractErrorMessage(submitMutation.error)}</Alert>}
          </Card>
        </div>
        {confirmDialog}
      </>
    )
  }

  const current = questions[currentIndex]
  const currentMondai = mondaiAt(mondai, currentIndex)
  const passage = current.passageId ? passages.find((p) => p.id === current.passageId) : undefined
  // Vị trí trong bài của câu điền vào chỗ trống n của đoạn văn đang làm.
  const indexOfBlank = (blankNo: number) =>
    questions.findIndex((q) => q.passageId === current.passageId && q.blankNo === blankNo)

  return (
    <>
      {header}
      <div className="mx-auto grid max-w-5xl gap-6 px-4 py-6 lg:grid-cols-[1fr_280px]">
        <div className="flex flex-col gap-5">
          {autoSubmitNotice && <Alert>Đã hết giờ làm bài — đang tự động nộp bài...</Alert>}
          {submitMutation.isError && <Alert>{extractErrorMessage(submitMutation.error)}</Alert>}

          {section && (
            <p className="flex flex-wrap items-baseline gap-x-2 text-sm font-bold text-muted-foreground">
              <span className="font-jp font-black text-foreground">{SECTION_META[section].jp}</span>
              <span>Phần {SECTION_META[section].vi}</span>
            </p>
          )}
          {currentMondai && <MondaiHeader mondai={currentMondai} />}
          {passage && (
            <section className="max-h-[45vh] overflow-y-auto rounded-2xl border-2 border-border bg-card px-4 py-3">
              {passage.title && <h2 className="mb-1 text-center font-jp font-black">{passage.title}</h2>}
              <PassageText
                content={passage.content}
                labelOf={(blankNo) => indexOfBlank(blankNo) + 1}
                currentBlank={current.blankNo}
                onSelect={(blankNo) => {
                  const index = indexOfBlank(blankNo)
                  if (index >= 0) setCurrentIndex(index)
                }}
              />
            </section>
          )}

          <div key={current.id} className="animate-pop-in">
            <p className="text-sm font-extrabold uppercase tracking-wider text-secondary">
              Câu {currentIndex + 1} / {total}
            </p>
            {/* Đề JLPT: câu dẫn đã có ở đầu 問題, câu có câu ví dụ thì không nhắc lại. */}
            {!(currentMondai && current.sentence) && !current.passageId && (
              <h1 className="mt-2 font-jp text-xl font-bold leading-relaxed sm:text-2xl">{current.questionText}</h1>
            )}
            {current.sentence && (
              <p className="mt-4 rounded-2xl border-2 border-border bg-card px-4 py-3 font-jp text-xl leading-loose sm:text-2xl">
                <SentenceWithTarget sentence={current.sentence} target={current.highlight} />
              </p>
            )}

            <div className="mt-6 flex flex-col gap-3">
              {OPTIONS.map((opt) => {
                const text = current[`option${opt}` as 'optionA' | 'optionB' | 'optionC' | 'optionD']
                const selected = answers[current.id] === opt
                return (
                  <button
                    key={opt}
                    type="button"
                    disabled={locked}
                    onClick={() => selectOption(current.id, opt)}
                    className={cn(
                      'flex min-h-14 items-center gap-3 rounded-2xl border-2 border-b-4 px-4 py-3 text-left font-bold transition-all active:translate-y-[2px] active:border-b-2 disabled:opacity-60',
                      selected ? 'border-secondary bg-secondary-soft text-secondary-dark' : 'border-border bg-card hover:bg-muted',
                    )}
                  >
                    <span
                      className={cn(
                        'flex h-8 w-8 shrink-0 items-center justify-center rounded-lg border-2 text-sm font-black',
                        selected ? 'border-secondary bg-secondary text-white' : 'border-border text-muted-foreground',
                      )}
                    >
                      {opt}
                    </span>
                    <span className="font-jp text-lg">{text}</span>
                  </button>
                )
              })}
            </div>
          </div>

          <div className="flex justify-between gap-3">
            <Button
              variant="outline"
              disabled={currentIndex === 0}
              onClick={() => setCurrentIndex((i) => Math.max(0, i - 1))}
            >
              <ChevronLeft className="h-5 w-5" /> Câu trước
            </Button>
            {currentIndex === total - 1 ? (
              <Button variant="destructive" onClick={handleManualSubmit} disabled={locked || submitMutation.isPending}>
                <Send className="h-5 w-5" /> Nộp bài
              </Button>
            ) : (
              <Button variant="secondary" onClick={() => setCurrentIndex((i) => Math.min(total - 1, i + 1))}>
                Câu tiếp <ChevronRight className="h-5 w-5" />
              </Button>
            )}
          </div>
        </div>

        <aside>
          <Card className="p-4 lg:sticky lg:top-24">
            <h2 className="mb-3 font-black">Danh sách câu hỏi</h2>
            {mondai.length > 0 ? (
              <div className="flex flex-col gap-3">
                {mondai.map((m) => (
                  <div key={m.number}>
                    <p className="mb-1.5 text-xs font-extrabold text-muted-foreground">
                      <span className="font-jp">問題{m.number}</span> · {QUESTION_TYPE_META[m.type].vi}
                    </p>
                    <QuestionPalette
                      total={total}
                      from={m.start}
                      count={m.questionCount}
                      currentIndex={currentIndex}
                      isAnswered={(i) => answers[questions[i].id] !== undefined}
                      onSelect={setCurrentIndex}
                    />
                  </div>
                ))}
              </div>
            ) : (
              <QuestionPalette
                total={total}
                currentIndex={currentIndex}
                isAnswered={(i) => answers[questions[i].id] !== undefined}
                onSelect={setCurrentIndex}
              />
            )}
            <div className="mt-4 flex gap-4 text-xs font-bold text-muted-foreground">
              <span className="flex items-center gap-1.5">
                <span className="h-3 w-3 rounded bg-secondary" /> Đã làm
              </span>
              <span className="flex items-center gap-1.5">
                <span className="h-3 w-3 rounded border-2 border-border" /> Chưa làm
              </span>
            </div>
            <p className="mt-4 hidden text-xs font-semibold text-muted-foreground lg:block">
              Phím tắt: A-D chọn đáp án · ← → chuyển câu
            </p>
          </Card>
        </aside>
      </div>
      {confirmDialog}
    </>
  )
}

/** Đầu một 問題 như đề thật: số 問題 và câu dẫn tiếng Nhật, kèm bản dịch nhỏ bên dưới. */
function MondaiHeader({ mondai }: { mondai: MondaiRange }) {
  const meta = QUESTION_TYPE_META[mondai.type]
  return (
    <div className="rounded-2xl border-2 border-border bg-card px-4 py-3">
      <p className="font-jp font-bold leading-relaxed">
        <span className="mr-2 rounded-lg bg-secondary px-2 py-0.5 text-sm font-black text-white">問題{mondai.number}</span>
        {meta.instruction}
      </p>
      <p className="mt-1 text-xs font-semibold text-muted-foreground">
        {meta.vi}: {meta.instructionVi}
        {mondai.questionCount < mondai.plannedCount &&
          ` (${mondai.questionCount}/${mondai.plannedCount} câu - ngân hàng câu hỏi chưa đủ)`}
      </p>
    </div>
  )
}
