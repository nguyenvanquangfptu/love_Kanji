import { useState } from 'react'
import { useParams, useNavigate } from 'react-router-dom'
import { useQuery, useQueryClient } from '@tanstack/react-query'
import {
  ArrowLeft,
  BookOpen,
  Check,
  CheckCircle2,
  Clock,
  Flag,
  Lightbulb,
  RotateCcw,
  Target,
  Trophy,
  XCircle,
} from 'lucide-react'
import { examApi } from '@/api/exam'
import { extractErrorMessage } from '@/api/client'
import type { ExamReviewResponse, QuizDirection } from '@/api/types'
import { QUESTION_TYPE_META, SECTION_META } from '@/lib/jlpt'
import { cn } from '@/lib/utils'
import { Card } from '@/components/ui/card'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { PageSpinner } from '@/components/ui/spinner'
import { Alert } from '@/components/ui/alert'
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs'
import { Leaderboard } from '@/components/Leaderboard'
import { StatTile } from '@/components/StatTile'
import { Meter } from '@/components/Meter'
import { SentenceWithTarget } from '@/components/SentenceWithTarget'
import { PassageText } from '@/components/PassageText'
import { ReportQuestionDialog } from '@/components/ReportQuestionDialog'

const SKILL_LABELS: Record<QuizDirection, string> = {
  KANJI_TO_READING: 'Đọc chữ Hán',
  READING_TO_KANJI: 'Chọn cách viết',
  MEANING: 'Hiểu nghĩa',
  // Không phải kỹ năng câu thi - chỉ để đủ kiểu.
  TYPE_READING: 'Gõ cách đọc',
}

const STATUS_LABEL: Record<string, string> = {
  COMPLETED: 'Đã nộp bài',
  TIMEOUT: 'Hết giờ - tự động nộp',
  IN_PROGRESS: 'Đang làm bài',
}

export function ExamResultPage() {
  const { attemptId: attemptIdParam } = useParams()
  const attemptId = Number(attemptIdParam)
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  // Câu đang báo lỗi (hộp thoại); key đổi mỗi lần mở để hộp thoại bắt đầu trống.
  const [reporting, setReporting] = useState<number | null>(null)
  const [reportKey, setReportKey] = useState(0)

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
  // Một phần của đề JLPT: không lên bảng xếp hạng thi nhanh, điểm theo 問題 thay cho theo kỹ năng.
  const sittingId = data.sittingId

  return (
    <div className="flex flex-col gap-6">
      <div className="flex flex-col items-center text-center">
        <span className="flex h-24 w-24 animate-bounce-in items-center justify-center rounded-full bg-accent-soft text-accent-dark">
          <Trophy className="h-12 w-12" strokeWidth={2.5} />
        </span>
        <h1 className="mt-4 text-3xl font-black">{title}</h1>
        <div className="mt-2 flex items-center gap-2">
          <Badge variant="purple">
            {sittingId && data.section
              ? `Đề JLPT ${data.jlptLevel} · ${SECTION_META[data.section].vi}`
              : `Thi thử ${data.jlptLevel}`}
          </Badge>
          <Badge variant={data.status === 'COMPLETED' ? 'success' : 'accent'}>{STATUS_LABEL[data.status] ?? data.status}</Badge>
        </div>
      </div>

      <div className="grid grid-cols-3 gap-3">
        <StatTile label="Câu đúng" value={`${data.totalScore}/${data.totalQuestions}`} tone="primary" icon={Check} />
        <StatTile label="Tỉ lệ đúng" value={`${percent}%`} tone="secondary" icon={Target} />
        <StatTile label="Thời gian" value={`${minutes}:${seconds.toString().padStart(2, '0')}`} tone="orange" icon={Clock} />
      </div>

      {sittingId
        ? data.mondai.length > 0 && <MondaiCard mondai={data.mondai} />
        : data.skills.length > 0 && <SkillCard skills={data.skills} />}
      {data.wrongWords.length > 0 && <WrongWordsCard review={data} />}

      <div className="flex flex-col gap-3 sm:flex-row">
        {sittingId ? (
          <Button size="lg" className="flex-1" onClick={() => navigate(`/exam/jlpt/${sittingId}`)}>
            <ArrowLeft className="h-5 w-5" /> Về buổi thi
          </Button>
        ) : (
          <Button size="lg" className="flex-1" onClick={() => navigate('/exam')}>
            <RotateCcw className="h-5 w-5" /> Thi lại
          </Button>
        )}
        <Button variant="outline" size="lg" className="flex-1" onClick={() => navigate('/study')}>
          <BookOpen className="h-5 w-5" /> Về học bài
        </Button>
      </div>

      <Tabs defaultValue="review">
        <TabsList>
          <TabsTrigger value="review">Xem lại bài làm</TabsTrigger>
          {!sittingId && <TabsTrigger value="leaderboard">Bảng xếp hạng</TabsTrigger>}
        </TabsList>

        <TabsContent value="review">
          <div className="flex flex-col gap-4">
            {data.questions.map((q, idx) => {
              // Đề JLPT: câu xếp theo 問題, đầu mỗi 問題 ghi tên dạng câu.
              const mondai =
                sittingId && q.questionType && q.questionType !== data.questions[idx - 1]?.questionType
                  ? data.mondai.find((m) => m.type === q.questionType)
                  : undefined
              // Đoạn văn 文章の文法 hiện một lần, trước câu đầu tiên của nó.
              const passage =
                q.passageId && q.passageId !== data.questions[idx - 1]?.passageId
                  ? data.passages.find((p) => p.id === q.passageId)
                  : undefined
              const numberOf = (blankNo: number) =>
                data.questions.findIndex((other) => other.passageId === q.passageId && other.blankNo === blankNo) + 1
              return (
                <div key={q.questionId} className="flex flex-col gap-2">
                  {mondai && (
                    <h3 className="mt-2 font-jp font-black">
                      問題{mondai.number} {QUESTION_TYPE_META[mondai.type].jp}
                      <span className="font-sans text-sm font-bold text-muted-foreground">
                        {' '}
                        · {QUESTION_TYPE_META[mondai.type].vi}
                      </span>
                    </h3>
                  )}
                  {passage && (
                    <Card className="px-4 py-3 sm:px-5">
                      {passage.title && <h4 className="mb-1 text-center font-jp font-black">{passage.title}</h4>}
                      <PassageText content={passage.content} labelOf={numberOf} />
                    </Card>
                  )}
                  <Card className={cn('p-4 sm:p-5', q.correct ? 'border-primary/40' : 'border-destructive/40')}>
                    <div className="flex items-start gap-2">
                      {q.correct ? (
                        <CheckCircle2 className="mt-0.5 h-6 w-6 shrink-0 text-primary" strokeWidth={2.5} />
                      ) : (
                        <XCircle className="mt-0.5 h-6 w-6 shrink-0 text-destructive" strokeWidth={2.5} />
                      )}
                      <div>
                        <p className="font-jp text-lg font-bold">
                          <span className="font-sans text-muted-foreground">Câu {idx + 1}.</span>{' '}
                          {q.passageId ? '' : q.questionText}
                        </p>
                        {q.sentence && (
                          <p className="mt-2 font-jp text-lg leading-loose">
                            <SentenceWithTarget sentence={q.sentence} target={q.highlight} />
                          </p>
                        )}
                      </div>
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
                    {q.grammarPoints.length > 0 && (
                      <div className="mt-3 flex flex-wrap items-center gap-1.5">
                        <span className="text-xs font-extrabold uppercase text-muted-foreground">Ngữ pháp</span>
                        {q.grammarPoints.map((point) => (
                          <Badge key={point.id} variant="purple" className="font-jp" title={point.meaningVi}>
                            {point.pattern}
                          </Badge>
                        ))}
                      </div>
                    )}
                    {q.explanation && (
                      <p className="mt-3 flex items-start gap-2 rounded-xl bg-secondary-soft px-3 py-2 text-sm font-semibold text-secondary-dark">
                        <Lightbulb className="mt-0.5 h-4 w-4 shrink-0" strokeWidth={2.5} />
                        {q.explanation}
                      </p>
                    )}
                    <div className="mt-2 flex justify-end">
                      {q.reported ? (
                        <span className="flex items-center gap-1 text-xs font-bold text-muted-foreground">
                          <Flag className="h-3.5 w-3.5" /> Đã báo lỗi - cảm ơn bạn
                        </span>
                      ) : (
                        <Button
                          size="sm"
                          variant="ghost"
                          className="text-muted-foreground"
                          onClick={() => {
                            setReportKey((key) => key + 1)
                            setReporting(q.questionId)
                          }}
                        >
                          <Flag className="h-4 w-4" /> Báo lỗi câu này
                        </Button>
                      )}
                    </div>
                  </Card>
                </div>
              )
            })}
          </div>
        </TabsContent>

        <TabsContent value="leaderboard">
          <Leaderboard level={data.jlptLevel} />
        </TabsContent>
      </Tabs>

      <ReportQuestionDialog
        key={reportKey}
        questionId={reporting}
        onClose={() => setReporting(null)}
        onReported={() => {
          setReporting(null)
          queryClient.invalidateQueries({ queryKey: ['exam', attemptId, 'review'] })
        }}
      />
    </div>
  )
}

/** Từ của các câu làm sai: đã vào Ôn tập, và luyện lại ngay bằng trắc nghiệm chỉ gồm các từ đó. */
function WrongWordsCard({ review }: { review: ExamReviewResponse }) {
  const navigate = useNavigate()
  const words = review.wrongWords
  const kanjiIds = words.map((w) => w.kanjiId).join(',')

  return (
    <Card className="p-4 sm:p-5">
      <h2 className="text-lg font-black">Từ cần ôn lại</h2>
      <p className="text-sm font-semibold text-muted-foreground">
        {review.addedToReview
          ? `${words.length} từ của các câu làm sai đã được đưa vào Ôn tập.`
          : 'Các từ của câu làm sai trong bài thi này.'}
      </p>
      <ul className="mt-3 flex flex-wrap gap-2">
        {words.map((w) => (
          <li key={w.kanjiId} className="rounded-xl border-2 border-border px-3 py-1.5" title={w.meaning}>
            <span className="font-jp text-lg font-bold">{w.character}</span>
            {w.reading && w.reading !== w.character && (
              <span className="ml-1.5 font-jp text-sm font-semibold text-muted-foreground">{w.reading}</span>
            )}
          </li>
        ))}
      </ul>
      <Button
        className="mt-4"
        variant="secondary"
        onClick={() => navigate(`/study/quiz?kanjiIds=${kanjiIds}&exam=${review.attemptId}`)}
      >
        <Target className="h-5 w-5" /> Luyện lại các từ này
      </Button>
    </Card>
  )
}

/** Tỉ lệ đúng theo từng 問題 của phần đề JLPT, chỉ ra dạng câu yếu nhất. */
function MondaiCard({ mondai }: { mondai: ExamReviewResponse['mondai'] }) {
  const rate = (m: ExamReviewResponse['mondai'][number]) => Math.round((m.correct / m.total) * 100)
  const weakest = mondai.reduce((low, m) => (rate(m) < rate(low) ? m : low), mondai[0])
  const uneven = mondai.some((m) => rate(m) > rate(weakest))

  return (
    <Card className="p-4 sm:p-5">
      <h2 className="text-lg font-black">Theo dạng câu</h2>
      <p className="text-sm font-semibold text-muted-foreground">
        {mondai.length < 2
          ? 'Bài này chỉ có một dạng câu.'
          : uneven
            ? `Cần luyện thêm nhất: 問題${weakest.number} ${QUESTION_TYPE_META[weakest.type].jp} (${QUESTION_TYPE_META[weakest.type].vi}).`
            : 'Các dạng câu đều nhau trong bài này.'}
      </p>
      <div className="mt-4 flex flex-col gap-4">
        {mondai.map((m) => (
          <Meter
            key={m.number}
            label={`問題${m.number} ${QUESTION_TYPE_META[m.type].jp} · ${QUESTION_TYPE_META[m.type].vi}`}
            value={rate(m)}
            detail={`${m.correct}/${m.total} câu`}
          />
        ))}
      </div>
    </Card>
  )
}

/** Tỉ lệ đúng theo từng kỹ năng, chỉ ra kỹ năng yếu nhất khi có ít nhất hai kỹ năng. */
function SkillCard({ skills }: { skills: ExamReviewResponse['skills'] }) {
  const rate = (s: ExamReviewResponse['skills'][number]) => Math.round((s.correct / s.total) * 100)
  const weakest = skills.reduce((low, s) => (rate(s) < rate(low) ? s : low), skills[0])
  const uneven = skills.some((s) => rate(s) > rate(weakest))

  return (
    <Card className="p-4 sm:p-5">
      <h2 className="text-lg font-black">Theo kỹ năng</h2>
      <p className="text-sm font-semibold text-muted-foreground">
        {skills.length < 2
          ? 'Bài này chỉ có câu hỏi một kỹ năng.'
          : uneven
            ? `Cần luyện thêm nhất: ${SKILL_LABELS[weakest.skill].toLowerCase()}.`
            : 'Các kỹ năng đều nhau trong bài này.'}
      </p>
      <div className="mt-4 flex flex-col gap-4">
        {skills.map((s) => (
          <Meter key={s.skill} label={SKILL_LABELS[s.skill]} value={rate(s)} detail={`${s.correct}/${s.total} câu`} />
        ))}
      </div>
    </Card>
  )
}
